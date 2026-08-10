package com.siliconverity.benchmark

import android.app.Application
import android.app.GameManager
import android.app.GameState
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.ArithmeticContract
import com.siliconverity.core.benchmark.ArithmeticType
import com.siliconverity.core.benchmark.ChecksumKind
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ProtocolSnapshot
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.toBenchmarkRun
import com.siliconverity.core.storage.BenchmarkRunStore
import com.siliconverity.feature.gpu.GpuUiState
import com.siliconverity.nativegpu.GpuWorkload
import com.siliconverity.nativegpu.NativeGpuResult
import com.siliconverity.nativegpu.VulkanBench
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class GpuController(application: Application) : AndroidViewModel(application) {

    private val bench = VulkanBench()
    private val env = AndroidBenchmarkEnvironment(application)
    private val store = BenchmarkRunStore(application.filesDir)
    private val gameManager = application.getSystemService(GameManager::class.java)

    private val _state = MutableStateFlow<GpuUiState>(GpuUiState.Idle(currentGameMode()))
    val state: StateFlow<GpuUiState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var graphicsSurface: Surface? = null

    fun setGraphicsSurface(surface: Surface?) {
        graphicsSurface = surface
        if (surface == null) {
            bench.cancelGraphics()
            if (_state.value is GpuUiState.Running) {
                job?.cancel()
                _state.value = GpuUiState.Idle(currentGameMode())
            }
        }
    }

    fun run() {
        if (!BenchmarkRunCoordinator.tryAcquire()) {
            _state.value = GpuUiState.Done(null, null, null, null, "另一个 benchmark 正在运行", currentGameMode())
            return
        }
        val surface = graphicsSurface?.takeIf { it.isValid }
        if (surface == null) {
            BenchmarkRunCoordinator.release()
            _state.value = GpuUiState.Done(null, null, null, null, "图形 Surface 尚未就绪", currentGameMode())
            return
        }
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            setBenchmarkGameState(active = true)
            val gameMode = currentGameMode()
            try {
                _state.value = GpuUiState.Running(gameMode)
                val sessionId = "gpu-" + UUID.randomUUID().toString()
                val now = env.nowIso()
                val graphics = runGraphics(surface, sessionId, now, gameMode)
                val indep = runOne(GpuWorkload.FP32_INDEPENDENT, "vulkan.fp32.independent", "0.2.0-alpha", sessionId, now, gameMode)
                val dep = runOne(GpuWorkload.FP32_DEPENDENCY, "vulkan.fp32.dependency", "0.3.0-alpha", sessionId, now, gameMode)
                val buf = runOne(GpuWorkload.BUFFER_THROUGHPUT, "vulkan.buffer.throughput", "0.2.0-alpha", sessionId, now, gameMode)
                _state.value = GpuUiState.Done(
                    graphics.first,
                    indep.first,
                    dep.first,
                    buf.first,
                    graphics.second ?: indep.second ?: dep.second ?: buf.second,
                    gameMode,
                )
            } finally {
                setBenchmarkGameState(active = false)
                BenchmarkRunCoordinator.release()
            }
        }
    }

    private suspend fun runGraphics(surface: Surface, sessionId: String, nowIso: String, gameMode: String): Pair<NativeGpuResult?, String?> {
        yield()
        return try {
            val startedNs = System.nanoTime()
            val result = bench.runGraphics(surface, warmupMs = 2_000, durationMs = 10_000)
            val actualDurationNs = System.nanoTime() - startedNs
            runCatching {
                store.save(toManifest(result, "vulkan.graphics.procedural_glow", "0.3.0-alpha", sessionId, nowIso, actualDurationNs, gameMode).toBenchmarkRun())
            }
            result to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null to (e.message ?: "graphics failed")
        }
    }

    private suspend fun runOne(
        workload: GpuWorkload,
        workloadId: String,
        version: String,
        sessionId: String,
        nowIso: String,
        gameMode: String,
    ): Pair<NativeGpuResult?, String?> {
        yield()
        return try {
            val startedNs = System.nanoTime()
            var latest = bench.run(workload, 300)
            var best = latest.takeIf { it.isScoreCandidate() }
            var retryCount = 0
            // Buffer 在部分移动 Vulkan 驱动上不支持同进程立即重建大块
            // host-visible workload；波动时保留 RETEST_RECOMMENDED，交给用户
            // 冷却后重测。Compute 才允许本轮内自动重试。
            while (workload != GpuWorkload.BUFFER_THROUGHPUT && latest.retestNeeded && retryCount < 2) {
                yield()
                latest = bench.run(workload, 300)
                if (latest.isScoreCandidate() &&
                    (best?.metricValue == null || latest.metricValue!! > best!!.metricValue!!)
                ) {
                    best = latest
                }
                retryCount++
            }
            val r = (best ?: latest).copy(
                retestNeeded = false,
                diag = listOfNotNull((best ?: latest).diag, "peakSelectAttempts=${retryCount + 1}").joinToString(" "),
            )
            val actualDurationNs = System.nanoTime() - startedNs
            runCatching { store.save(toManifest(r, workloadId, version, sessionId, nowIso, actualDurationNs, gameMode).toBenchmarkRun()) }
            r to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null to (e.message ?: "unknown")
        }
    }

    fun stop() {
        bench.cancelGraphics()
        job?.cancel()
        setBenchmarkGameState(active = false)
        _state.value = GpuUiState.Idle(currentGameMode())
    }

    fun reset() {
        _state.value = GpuUiState.Idle(currentGameMode())
    }

    override fun onCleared() {
        bench.cancelGraphics()
        job?.cancel()
        setBenchmarkGameState(active = false)
        super.onCleared()
    }

    private fun toManifest(
        r: NativeGpuResult,
        workloadId: String,
        version: String,
        sessionId: String,
        nowIso: String,
        actualDurationNanos: Long,
        gameMode: String,
    ): RunManifest {
        val graphics = workloadId == "vulkan.graphics.procedural_glow"
        val rawThroughput = (r.metricValue ?: 0.0) * 1_000_000_000.0
        val timingOk = (r.medianNs ?: 0L) > 0L
        val metricOk = r.metricValue?.let { it.isFinite() && it > 0.0 } ?: false
        val valid = r.supported && r.checksumValid && r.invalidReason.isNullOrEmpty() && timingOk && metricOk
        val cv = r.coefficientOfVariation ?: 1.0
        val validity = when {
            !valid -> ValidityLevel.INVALID
            r.retestNeeded -> ValidityLevel.RETEST_RECOMMENDED
            cv <= if (graphics) 0.08 else 0.03 -> ValidityLevel.STABLE
            cv <= if (graphics) 0.15 else 0.07 -> ValidityLevel.VARIABLE
            else -> ValidityLevel.RETEST_RECOMMENDED
        }
        return RunManifest(
            runId = "${sessionId}_$workloadId",
            sessionId = sessionId,
            benchmarkProtocolVersion = "0.1.0",
            appVersion = env.appVersion,
            benchmarkEngineVersion = "vulkan-0.3.0-alpha",
            workloadId = workloadId,
            workloadVersion = version,
            shaderSourceVersion = when {
                graphics -> "0.3.0-alpha"
                workloadId == "vulkan.fp32.independent" -> "0.2.0-alpha"
                else -> "0.1.0-alpha"
            },
            spirvHash = r.spirvHash,
            pipelineConfigVersion = when (workloadId) {
                "vulkan.fp32.dependency" -> "0.3.0-alpha"
                "vulkan.graphics.procedural_glow" -> "0.3.0-alpha"
                "vulkan.fp32.independent" -> "0.2.0-alpha"
                else -> "0.1.0-alpha"
            },
            scoreVersion = null,
            arithmeticType = r.arithType?.let { parseArithType(it) },
            arithmeticContract = r.arithContract?.let { parseArithContract(it) },
            startedAt = nowIso,
            endedAt = env.nowIso(),
            actualDurationNanos = actualDurationNanos,
            abi = env.abi,
            androidVersion = env.androidVersion,
            securityPatch = env.securityPatch,
            deviceModel = env.deviceModel,
            socReported = r.deviceName ?: "",
            batteryLevel = env.batteryLevel,
            chargingState = env.chargingState,
            powerSaveMode = env.powerSaveMode,
            gameMode = gameMode,
            thermalStatusStart = env.thermalStatusStart,
            thermalStatusEnd = env.thermalStatusEnd(),
            testOrder = listOf(workloadId),
            warmupSamples = emptyList(),
            measurementSamples = if (metricOk) {
                val medianNs = r.medianNs?.takeIf { it > 0L } ?: 1_000_000_000L
                val workUnits = if (graphics) 1L else ((r.metricValue ?: 0.0) * medianNs).toLong().coerceAtLeast(1L)
                r.sampleNanos.takeIf { it.isNotEmpty() }
                    ?.mapIndexed { index, duration -> Sample(index, workUnits, duration, nowIso) }
                    ?: listOf(Sample(0, rawThroughput.toLong(), 1_000_000_000L, nowIso))
            } else {
                emptyList()
            },
            median = rawThroughput,
            mad = 0.0,
            cv = cv,
            correctnessStatus = r.checksumValid,
            correctness = com.siliconverity.core.benchmark.CorrectnessResult(
                passed = r.checksumValid,
                kind = if (workloadId.contains("fp32")) ChecksumKind.ULP else ChecksumKind.EXACT,
                finite = r.checksumValid,
                reason = r.invalidReason,
            ),
            protocol = if (graphics) ProtocolSnapshot(
                protocolVersion = "0.3.0",
                warmupMinSeconds = 2.0,
                warmupMaxSeconds = 2.0,
                warmupConvergeThreshold = 0.0,
                measurementSamplesActual = r.sampleNanos.size,
                stableCvThreshold = 0.08,
                variableCvThreshold = 0.15,
                targetRoundMillis = 10,
                provisional = true,
            ) else null,
            validityLevel = validity,
            checksumKind = if (workloadId.contains("fp32")) ChecksumKind.ULP else ChecksumKind.EXACT,
            diagnostics = listOfNotNull(
                r.diag?.let { "gpu-native: $it" },
                "game-mode: $gameMode",
            ),
            warnings = buildList {
                if (!valid) addAll(listOfNotNull(r.invalidReason))
                if (gameMode == "BATTERY") add("GPU suite ran in BATTERY game mode")
                if (gameMode == "UNSUPPORTED" || gameMode == "UNAVAILABLE") {
                    add("GPU suite was not recognized by Android GameManager: $gameMode")
                }
            },
        )
    }

    private fun currentGameMode(): String = when (gameManager?.gameMode) {
        GameManager.GAME_MODE_PERFORMANCE -> "PERFORMANCE"
        GameManager.GAME_MODE_BATTERY -> "BATTERY"
        GameManager.GAME_MODE_STANDARD -> "STANDARD"
        GameManager.GAME_MODE_CUSTOM -> "CUSTOM"
        GameManager.GAME_MODE_UNSUPPORTED -> "UNSUPPORTED"
        null -> "UNAVAILABLE"
        else -> "UNKNOWN"
    }

    private fun setBenchmarkGameState(active: Boolean) {
        runCatching {
            gameManager?.setGameState(
                GameState(
                    false,
                    if (active) GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE else GameState.MODE_NONE,
                ),
            )
        }
    }

    private fun parseArithType(s: String): ArithmeticType? = when (s) {
        "FP32" -> ArithmeticType.FP32
        "FP16" -> ArithmeticType.FP16
        "INT8" -> ArithmeticType.INT8
        else -> null
    }

    private fun NativeGpuResult.isScoreCandidate(): Boolean =
        supported && checksumValid && invalidReason.isNullOrEmpty() &&
            metricValue?.let { it.isFinite() && it > 0.0 } == true

    private fun parseArithContract(s: String): ArithmeticContract? = when (s) {
        "DEVICE_DEFAULT" -> ArithmeticContract.DEVICE_DEFAULT
        "NO_CONTRACTION" -> ArithmeticContract.NO_CONTRACTION
        "DEVICE_FAST" -> ArithmeticContract.DEVICE_FAST
        "STRICT_CONFORMANT" -> ArithmeticContract.STRICT_CONFORMANT
        else -> null
    }
}
