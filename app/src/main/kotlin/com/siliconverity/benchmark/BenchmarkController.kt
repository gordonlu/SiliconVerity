package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkEngine
import com.siliconverity.core.storage.RunManifestStore
import com.siliconverity.feature.hardware.BenchmarkUiState
import com.siliconverity.nativecpu.CpuIntegerWorkload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class BenchmarkController(application: Application) : AndroidViewModel(application) {

    private val env = AndroidBenchmarkEnvironment(application)
    private val engine = BenchmarkEngine({ System.nanoTime() }, env)
    private val workload = CpuIntegerWorkload()
    private val store = RunManifestStore(File(application.filesDir, "runs"))

    private val _state = MutableStateFlow<BenchmarkUiState>(BenchmarkUiState.Idle)
    val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()

    fun run() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = BenchmarkUiState.Running
            val result = runCatching { engine.execute(workload) }
            val manifest = result.getOrNull()
            val savedPath = manifest?.let {
                runCatching { store.save(it).name }.getOrNull()
            }
            _state.value = BenchmarkUiState.Done(
                manifest = manifest,
                error = result.exceptionOrNull()?.message,
                savedPath = savedPath,
            )
        }
    }
}
