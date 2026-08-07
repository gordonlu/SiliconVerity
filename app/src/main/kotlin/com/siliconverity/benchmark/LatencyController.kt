package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.nativememory.MemoryLatencyBench
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LatencyUiState {
    data object Idle : LatencyUiState
    data object Running : LatencyUiState
    data class Done(val points: List<MemoryLatencyBench.LatencyPoint>) : LatencyUiState
    data class Error(val message: String) : LatencyUiState
}

class LatencyController(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LatencyUiState>(LatencyUiState.Idle)
    val state: StateFlow<LatencyUiState> = _state.asStateFlow()

    fun run() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = LatencyUiState.Running
            val r = runCatching { MemoryLatencyBench.run() }
            _state.value = r.fold(
                onSuccess = { LatencyUiState.Done(it) },
                onFailure = { LatencyUiState.Error(it.message ?: "unknown") },
            )
        }
    }

    fun reset() {
        _state.value = LatencyUiState.Idle
    }
}
