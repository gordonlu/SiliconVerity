package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkEngine
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.storage.RunManifestStore
import com.siliconverity.feature.gpu.GpuUiState
import com.siliconverity.nativegpu.GpuWorkload
import com.siliconverity.nativegpu.NativeGpuResult
import com.siliconverity.nativegpu.VulkanBench
import kotlinx.coroutines.Dispatchers
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

    fun run() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = GpuUiState.Running
            val sessionId = "gpu-" + UUID.randomUUID().toString()
            val fp32Res = runCatching { bench.run(GpuWorkload.FP32_COMPUTE, 300) }
            val triadRes = runCatching { bench.run(GpuWorkload.BUFFER_THROUGHPUT, 300) }
            val fp32 = fp32Res.getOrNull()
            val triad = triadRes.getOrNull()
            val now = env.nowIso()
            if (fp32 != null) {
                runCatching { store.save(toManifest(fp32, "vulkan.fp32.compute", "0.1.0-alpha", sessionId, now)) }
            }
            if (triad != null) {
                runCatching { store.save(toManifest(triad, "vulkan.buffer.throughput", "0.1.0-alpha", sessionId, now)) }
            }
            val err = fp32Res.exceptionOrNull()?.message ?: triadRes.exceptionOrNull()?.message
            _state.value = GpuUiState.Done(fp32, triad, err)
        }
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
            appVersion = env.appVersion,
            benchmarkEngineVersion = "vulkan-0.1.0-alpha",
            workloadId = workloadId,
            workloadVersion = version,
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
            warnings = if (!valid) listOfNotNull(r.invalidReason) else emptyList(),
        )
    }
}
