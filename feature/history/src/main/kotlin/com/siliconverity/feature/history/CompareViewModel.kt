package com.siliconverity.feature.history

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
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

class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val store = BenchmarkRunStore(application.filesDir)

    private val _runs = MutableStateFlow<List<BenchmarkRun>>(emptyList())
    val runs: StateFlow<List<BenchmarkRun>> = _runs.asStateFlow()

    val selected = mutableStateListOf<String>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _runs.value = runCatching { store.list() }.getOrDefault(emptyList())
        }
    }

    fun toggle(runId: String) {
        when {
            selected.contains(runId) -> selected.remove(runId)
            selected.size < 2 -> selected.add(runId)
        }
    }

    fun clear() {
        selected.clear()
    }
}
