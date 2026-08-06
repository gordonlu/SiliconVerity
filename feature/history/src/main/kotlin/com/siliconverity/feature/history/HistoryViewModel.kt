package com.siliconverity.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.storage.RunManifestStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class HistoryUiState(
    val loading: Boolean = true,
    val runs: List<RunManifest> = emptyList(),
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RunManifestStore(File(application.filesDir, "runs"))

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
}
