package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.MemoryLatencyResult
import com.siliconverity.core.benchmark.toBenchmarkRun
import com.siliconverity.core.storage.BenchmarkRunStore
import com.siliconverity.nativememory.MemoryLatencyBench
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface LatencyUiState {
    data object Idle : LatencyUiState
    data object Running : LatencyUiState
    data class Done(val points: List<com.siliconverity.core.benchmark.LatencyPoint>) : LatencyUiState
    data class Error(val message: String) : LatencyUiState
}

class LatencyController(application: Application) : AndroidViewModel(application) {

    private val env = AndroidBenchmarkEnvironment(application)
    private val store = BenchmarkRunStore(application.filesDir)

    private val _state = MutableStateFlow<LatencyUiState>(LatencyUiState.Idle)
    val state: StateFlow<LatencyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val latest = store.list().firstOrNull { it.payload is com.siliconverity.core.benchmark.BenchmarkPayload.Curve }
            val curve = latest?.payload as? com.siliconverity.core.benchmark.BenchmarkPayload.Curve
            if (curve != null) {
                _state.value = LatencyUiState.Done(curve.points)
            }
        }
    }

    fun run() {
        if (!BenchmarkRunCoordinator.tryAcquire()) {
            _state.value = LatencyUiState.Error("另一个 benchmark 正在运行")
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _state.value = LatencyUiState.Running
                val r = runCatching { MemoryLatencyBench.run() }
                _state.value = r.fold(
                    onSuccess = { points ->
                        val res = MemoryLatencyResult(
                            runId = env.runId(),
                            startedAt = env.nowIso(),
                            deviceModel = env.deviceModel,
                            socReported = env.socReported,
                            androidVersion = env.androidVersion,
                            abi = env.abi,
                            points = points,
                        )
                        runCatching { store.save(res.toBenchmarkRun()) }
                        LatencyUiState.Done(points)
                    },
                    onFailure = { LatencyUiState.Error(it.message ?: "unknown") },
                )
            } finally {
                BenchmarkRunCoordinator.release()
            }
        }
    }

    fun reset() {
        _state.value = LatencyUiState.Idle
    }
}
