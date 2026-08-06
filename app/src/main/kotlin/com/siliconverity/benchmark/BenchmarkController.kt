package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkEngine
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.benchmark.RunResult
import com.siliconverity.core.benchmark.Workload
import com.siliconverity.core.storage.RunManifestStore
import com.siliconverity.benchmark.storage.StorageReadWorkload
import com.siliconverity.benchmark.storage.StorageWriteWorkload
import com.siliconverity.nativecpu.CpuIntegerWorkload
import com.siliconverity.nativecpu.Fp32FmaWorkload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class BenchmarkController(application: Application) : AndroidViewModel(application) {

    private val env = AndroidBenchmarkEnvironment(application)
    private val engine = BenchmarkEngine({ System.nanoTime() }, env)
    private val benchDir = File(application.filesDir, "bench")
    private val workloads: List<Workload> = listOf(
        CpuIntegerWorkload(),
        Fp32FmaWorkload(),
        StorageWriteWorkload(benchDir),
        StorageReadWorkload(benchDir),
    )
    private val store = RunManifestStore(File(application.filesDir, "runs"))

    private val _state = MutableStateFlow<BenchmarkUiState>(BenchmarkUiState.Idle)
    val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()

    fun run() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = BenchmarkUiState.Running
            val results = mutableListOf<RunResult>()
            var error: String? = null
            for (workload in workloads) {
                val outcome = runCatching { engine.execute(workload) }
                val manifest = outcome.getOrNull()
                if (manifest == null) {
                    error = outcome.exceptionOrNull()?.message
                    break
                }
                val saved = runCatching { store.save(manifest).name }.getOrNull()
                results += RunResult(manifest, saved)
            }
            _state.value = BenchmarkUiState.Done(results, error)
        }
    }
}
