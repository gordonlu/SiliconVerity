package com.siliconverity.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.storage.BenchmarkRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class HistoryUiState(
    val loading: Boolean = true,
    val runs: List<BenchmarkRun> = emptyList(),
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val store = BenchmarkRunStore(
        runsDir = File(application.filesDir, "runs"),
        sustainedDir = File(application.filesDir, "sustained"),
        latencyDir = File(application.filesDir, "latency"),
    )

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            val runs = runCatching { store.list() }.getOrDefault(emptyList())
            _state.value = HistoryUiState(loading = false, runs = runs)
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.clear() }
            _state.value = HistoryUiState(loading = false, runs = emptyList())
        }
    }
}
