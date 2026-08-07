package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.MemoryLatencyResult
import com.siliconverity.core.storage.MemoryLatencyResultStore
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
    private val store = MemoryLatencyResultStore(File(application.filesDir, "latency"))

    private val _state = MutableStateFlow<LatencyUiState>(LatencyUiState.Idle)
    val state: StateFlow<LatencyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            store.latest()?.let { _state.value = LatencyUiState.Done(it.points) }
        }
    }

    fun run() {
        viewModelScope.launch(Dispatchers.Default) {
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
                    runCatching { store.save(res) }
                    LatencyUiState.Done(points)
                },
                onFailure = { LatencyUiState.Error(it.message ?: "unknown") },
            )
        }
    }

    fun reset() {
        _state.value = LatencyUiState.Idle
    }
}
