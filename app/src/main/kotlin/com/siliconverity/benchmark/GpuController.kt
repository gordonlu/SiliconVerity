package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.ArithmeticContract
import com.siliconverity.core.benchmark.ArithmeticType
import com.siliconverity.core.benchmark.ChecksumKind
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.storage.RunManifestStore
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
    private val store = RunManifestStore(File(application.filesDir, "runs"))

    private val _state = MutableStateFlow<GpuUiState>(GpuUiState.Idle)
    val state: StateFlow<GpuUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun run() {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            _state.value = GpuUiState.Running
            val sessionId = "gpu-" + UUID.randomUUID().toString()
            val now = env.nowIso()
            val indep = runOne(GpuWorkload.FP32_INDEPENDENT, "vulkan.fp32.independent", "0.1.0-alpha", sessionId, now)
            val dep = runOne(GpuWorkload.FP32_DEPENDENCY, "vulkan.fp32.dependency", "0.1.0-alpha", sessionId, now)
            val buf = runOne(GpuWorkload.BUFFER_THROUGHPUT, "vulkan.buffer.throughput", "0.1.0-alpha", sessionId, now)
            _state.value = GpuUiState.Done(indep.first, dep.first, buf.first, indep.second ?: dep.second ?: buf.second)
        }
    }

    private suspend fun runOne(
        workload: GpuWorkload,
        workloadId: String,
        version: String,
        sessionId: String,
        nowIso: String,
    ): Pair<NativeGpuResult?, String?> {
        yield()
        return try {
            val r = bench.run(workload, 300)
            runCatching { store.save(toManifest(r, workloadId, version, sessionId, nowIso)) }
            r to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null to (e.message ?: "unknown")
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = GpuUiState.Idle
    }

    fun reset() {
        _state.value = GpuUiState.Idle
    }

    private fun toManifest(r: NativeGpuResult, workloadId: String, version: String, sessionId: String, nowIso: String): RunManifest {
        val rawThroughput = (r.metricValue ?: 0.0) * 1_000_000_000.0
        val valid = r.supported && r.checksumValid
        return RunManifest(
            runId = "${sessionId}_$workloadId",
            sessionId = sessionId,
            benchmarkProtocolVersion = "0.1.0",
            appVersion = env.appVersion,
            benchmarkEngineVersion = "vulkan-0.1.0-alpha",
            workloadId = workloadId,
            workloadVersion = version,
            shaderSourceVersion = version,
            spirvHash = r.spirvHash,
            pipelineConfigVersion = "0.1.0-alpha",
            scoreVersion = null,
            arithmeticType = r.arithType?.let { parseArithType(it) },
            arithmeticContract = r.arithContract?.let { parseArithContract(it) },
            startedAt = nowIso,
            abi = env.abi,
            androidVersion = env.androidVersion,
            securityPatch = env.securityPatch,
            deviceModel = env.deviceModel,
            socReported = r.deviceName ?: "",
            batteryLevel = env.batteryLevel,
            chargingState = env.chargingState,
            powerSaveMode = env.powerSaveMode,
            thermalStatusStart = env.thermalStatusStart,
            thermalStatusEnd = env.thermalStatusEnd(),
            testOrder = listOf(workloadId),
            warmupSamples = emptyList(),
            measurementSamples = emptyList(),
            median = rawThroughput,
            mad = 0.0,
            cv = r.coefficientOfVariation ?: 0.0,
            correctnessStatus = r.checksumValid,
            validityLevel = if (valid) ValidityLevel.STABLE else ValidityLevel.INVALID,
            checksumKind = if (workloadId.contains("fp32")) ChecksumKind.ULP else ChecksumKind.EXACT,
            warnings = if (!valid) listOfNotNull(r.invalidReason) else emptyList(),
        )
    }

    private fun parseArithType(s: String): ArithmeticType? = when (s) {
        "FP32" -> ArithmeticType.FP32
        "FP16" -> ArithmeticType.FP16
        "INT8" -> ArithmeticType.INT8
        else -> null
    }

    private fun parseArithContract(s: String): ArithmeticContract? = when (s) {
        "DEVICE_DEFAULT" -> ArithmeticContract.DEVICE_DEFAULT
        "NO_CONTRACTION" -> ArithmeticContract.NO_CONTRACTION
        "DEVICE_FAST" -> ArithmeticContract.DEVICE_FAST
        "STRICT_CONFORMANT" -> ArithmeticContract.STRICT_CONFORMANT
        else -> null
    }
}
