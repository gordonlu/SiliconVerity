package com.siliconverity.benchmark

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkCategory
import com.siliconverity.core.benchmark.BenchmarkEngine
import com.siliconverity.core.benchmark.BenchmarkPhase
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.benchmark.LiveEnvironmentSnapshot
import com.siliconverity.core.benchmark.MemoryLatencyResult
import com.siliconverity.core.benchmark.RunResult
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.Workload
import com.siliconverity.core.benchmark.WorkloadProgress
import com.siliconverity.core.benchmark.toBenchmarkRun
import com.siliconverity.core.hardware.BatteryCollector
import com.siliconverity.core.hardware.ThermalCollector
import com.siliconverity.core.storage.BenchmarkRunStore
import com.siliconverity.benchmark.storage.StorageReadWorkload
import com.siliconverity.benchmark.storage.StorageWriteWorkload
import com.siliconverity.benchmark.storage.StorageDurableWriteWorkload
import com.siliconverity.benchmark.storage.StorageRandomWriteFsyncWorkload
import com.siliconverity.nativecpu.CpuIntegerWorkload
import com.siliconverity.nativecpu.Fp32FmaWorkload
import com.siliconverity.nativegpu.GpuWorkload
import com.siliconverity.nativegpu.VulkanBench
import com.siliconverity.nativememory.MemoryCopyWorkload
import com.siliconverity.nativememory.MemoryLatencyBench
import com.siliconverity.nativememory.MemoryReadWorkload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class BenchmarkController(application: Application) : AndroidViewModel(application) {

    private val appContext = application
    private val env = AndroidBenchmarkEnvironment(application)
    private val engine = BenchmarkEngine({ System.nanoTime() }, env)
    private val benchDir = File(application.filesDir, "bench")
    private val gpuBench = VulkanBench()

    /** 套件顺序 = 展示顺序: CPU 5, MEMORY 3 (含延迟), GPU 3, APP I/O 4 = 15 项。 */
    private data class SuiteItem(val workloadId: String, val workload: Workload?, val isLatency: Boolean)

    private val suite: List<SuiteItem> = listOf(
        SuiteItem("cpu.int.ilp", CpuIntegerWorkload(), false),
        SuiteItem("cpu.fp32.fma", Fp32FmaWorkload(), false),
        SuiteItem("cpu.int.branch", com.siliconverity.nativecpu.IntBranchWorkload(), false),
        SuiteItem("cpu.hash.cached", com.siliconverity.nativecpu.CompressionWorkload(), false),
        SuiteItem("cpu.multithread", com.siliconverity.nativecpu.MultithreadWorkload(), false),
        SuiteItem("mem.bandwidth.read", MemoryReadWorkload(), false),
        SuiteItem("mem.bandwidth.copy", MemoryCopyWorkload(), false),
        SuiteItem("mem.latency.curve", null, true),
        SuiteItem("vulkan.fp32.independent", GpuVulkanWorkload(gpuBench, GpuWorkload.FP32_INDEPENDENT, "vulkan.fp32.independent"), false),
        SuiteItem("vulkan.fp32.dependency", GpuVulkanWorkload(gpuBench, GpuWorkload.FP32_DEPENDENCY, "vulkan.fp32.dependency"), false),
        SuiteItem("vulkan.buffer.throughput", GpuVulkanWorkload(gpuBench, GpuWorkload.BUFFER_THROUGHPUT, "vulkan.buffer.throughput"), false),
        SuiteItem("storage.seq_write.buffered", StorageWriteWorkload(benchDir), false),
        SuiteItem("storage.seq_write.durable", StorageDurableWriteWorkload(benchDir), false),
        SuiteItem("storage.random_write.fsync", StorageRandomWriteFsyncWorkload(benchDir), false),
        SuiteItem("storage.seq_read.warm", StorageReadWorkload(benchDir), false),
    )

    private val store = BenchmarkRunStore(application.filesDir)

    private val _state = MutableStateFlow<BenchmarkUiState>(BenchmarkUiState.Idle)
    val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var sessionStartedAt: String = ""

    @Volatile
    private var paused = false

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            val extraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incoming = extraState == TelephonyManager.EXTRA_STATE_RINGING ||
                extraState == TelephonyManager.EXTRA_STATE_OFFHOOK
            setPaused(incoming)
        }
    }

    init {
        runCatching {
            appContext.registerReceiver(
                phoneReceiver,
                IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            )
        }
    }

    override fun onCleared() {
        runCatching { appContext.unregisterReceiver(phoneReceiver) }
        super.onCleared()
    }

    private fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        val current = _state.value
        if (current is BenchmarkUiState.Running) {
            _state.value = current.copy(paused = value)
        }
    }

    /** 来电/通话期间挂起, 挂断自动恢复。 */
    private suspend fun awaitResume() {
        while (paused) delay(300)
    }

    fun run() {
        if (!BenchmarkRunCoordinator.tryAcquire()) {
            _state.value = BenchmarkUiState.Done(emptyList(), "另一个 benchmark 正在运行")
            return
        }
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                // 离开 Done: 结果页守卫据此 pop 回首页, 避免双 pop 弹空栈
                _state.value = BenchmarkUiState.Idle
                val sessionId = java.util.UUID.randomUUID().toString()
                sessionStartedAt = env.nowIso()
                val results = mutableListOf<RunResult>()
                val completed = mutableListOf<WorkloadProgress>()
                var error: String? = null
                val snapshot = captureEnvironment()

                for ((index, item) in suite.withIndex()) {
                    awaitResume()
                    val progressState = { phase: BenchmarkPhase, sampleIndex: Int?, sampleCount: Int? ->
                        _state.value = BenchmarkUiState.Running(
                            sessionId = sessionId,
                            index = index + 1,
                            total = suite.size,
                            category = item.workloadId.let { categoryOf(it) },
                            workloadId = item.workloadId,
                            phase = phase,
                            sampleIndex = sampleIndex,
                            sampleCount = sampleCount,
                            completed = completed.toList(),
                            environment = snapshot,
                            paused = paused,
                        )
                    }
                    if (item.isLatency) {
                        progressState(BenchmarkPhase.CALIBRATING, null, null)
                        progressState(BenchmarkPhase.MEASURING, 0, 1)
                        val latency = runCatching { MemoryLatencyBench.run() }
                        progressState(BenchmarkPhase.VERIFYING, null, null)
                        val valid = latency.isSuccess && latency.getOrNull()?.all { it.latencyNs >= 0 } == true
                        if (latency.isSuccess) {
                            runCatching {
                                store.save(
                                    MemoryLatencyResult(
                                        runId = env.runId(),
                                        startedAt = env.nowIso(),
                                        deviceModel = env.deviceModel,
                                        socReported = env.socReported,
                                        androidVersion = env.androidVersion,
                                        abi = env.abi,
                                        points = latency.getOrThrow(),
                                        sessionId = sessionId,
                                    ).toBenchmarkRun(),
                                )
                            }
                        }
                        progressState(BenchmarkPhase.FINALIZING, null, null)
                        completed += WorkloadProgress(item.workloadId, categoryOf(item.workloadId), valid)
                        if (latency.isFailure) {
                            error = latency.exceptionOrNull()?.message
                        }
                        continue
                    }

                    val workload = item.workload!!
                    val outcome = runCatching {
                        engine.execute(workload, onPhase = { phase, sIdx, sCnt ->
                            progressState(phase, sIdx, sCnt)
                        })
                    }
                    var manifest = outcome.getOrNull()?.copy(sessionId = sessionId)
                    if (manifest == null) {
                        error = outcome.exceptionOrNull()?.message
                        completed += WorkloadProgress(item.workloadId, categoryOf(item.workloadId), false)
                        progressState(BenchmarkPhase.FINALIZING, null, null)
                        break
                    }
                    // 外部干扰 (来电/推送/后台任务) 会导致高 CV -> RETEST。
                    // 自动重测最多 2 次取最优, 干扰过去后恢复 STABLE; 仍高才保留 RETEST。
                    if (manifest != null && manifest.validityLevel == ValidityLevel.RETEST_RECOMMENDED) {
                        var current: com.siliconverity.core.benchmark.RunManifest = manifest
                        for (attempt in 1..2) {
                            progressState(BenchmarkPhase.FINALIZING, null, null)
                            val retry = runCatching {
                                engine.execute(workload, onPhase = { phase, sIdx, sCnt ->
                                    progressState(phase, sIdx, sCnt)
                                })
                            }.getOrNull()?.copy(sessionId = sessionId)
                            if (retry == null) break
                            if (retry.cv < current.cv) {
                                current = retry.copy(
                                    warnings = retry.warnings + "auto-retry #$attempt (高波动, 可能外部干扰)",
                                )
                            }
                            if (current.validityLevel != ValidityLevel.RETEST_RECOMMENDED) break
                        }
                        manifest = current
                    }
                    val saved = runCatching { store.save(manifest.toBenchmarkRun()).name }.getOrNull()
                    results += RunResult(manifest, saved)
                    completed += WorkloadProgress(
                        item.workloadId,
                        categoryOf(item.workloadId),
                        manifest.validityLevel == ValidityLevel.STABLE || manifest.validityLevel == ValidityLevel.VARIABLE,
                    )
                    progressState(BenchmarkPhase.FINALIZING, null, null)
                }

                _state.value = BenchmarkUiState.Scoring
                val score = computeScore(results)
                delay(900)
                _state.value = BenchmarkUiState.Done(results, error, score, sessionStartedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BenchmarkUiState.Done(emptyList(), e.message)
            } finally {
                BenchmarkRunCoordinator.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = BenchmarkUiState.Idle
    }

    private fun categoryOf(workloadId: String): BenchmarkCategory = when {
        workloadId.startsWith("cpu.") -> BenchmarkCategory.CPU
        workloadId.startsWith("mem.") -> BenchmarkCategory.MEMORY
        workloadId.startsWith("vulkan.") -> BenchmarkCategory.GPU
        else -> BenchmarkCategory.APP_IO
    }

    private fun captureEnvironment(): LiveEnvironmentSnapshot {
        val facts = runCatching {
            BatteryCollector().collect(appContext).associateBy { it.key }
        }.getOrDefault(emptyMap())
        val tempC = facts["battery.temperature"]?.evidence?.firstOrNull()?.rawValue?.toDoubleOrNull()
        val charging = facts["battery.charging"]?.evidence?.firstOrNull()?.rawValue == "true"
        val pm = appContext.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
        val thermal = pm?.currentThermalStatus?.let { status ->
            com.siliconverity.core.hardware.ThermalStatusNames.name(status)
        } ?: "UNKNOWN"
        return LiveEnvironmentSnapshot(
            thermal = thermal,
            batteryTempC = tempC,
            power = if (charging) "CHARGING" else "BATTERY",
        )
    }

    private fun computeScore(results: List<RunResult>): com.siliconverity.core.benchmark.ScoreReport? {
        val pack = com.siliconverity.core.benchmark.ScorePackLoader.loadDefault() ?: return null
        val runs = results.map { it.manifest.toBenchmarkRun() }
        return runCatching { com.siliconverity.core.benchmark.ScoringEngine(pack).score(runs) }.getOrNull()
    }
}
