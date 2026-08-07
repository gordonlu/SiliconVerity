package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkEngine
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.benchmark.RunResult
import com.siliconverity.core.benchmark.Workload
import com.siliconverity.core.benchmark.toBenchmarkRun
import com.siliconverity.core.storage.BenchmarkRunStore
import com.siliconverity.benchmark.storage.StorageReadWorkload
import com.siliconverity.benchmark.storage.StorageWriteWorkload
import com.siliconverity.benchmark.storage.StorageDurableWriteWorkload
import com.siliconverity.benchmark.storage.StorageRandomWriteFsyncWorkload
import com.siliconverity.nativecpu.CpuIntegerWorkload
import com.siliconverity.nativecpu.Fp32FmaWorkload
import com.siliconverity.nativememory.MemoryCopyWorkload
import com.siliconverity.nativememory.MemoryReadWorkload
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
        com.siliconverity.nativecpu.IntBranchWorkload(),
        com.siliconverity.nativecpu.CompressionWorkload(),
        com.siliconverity.nativecpu.MultithreadWorkload(),
        MemoryReadWorkload(),
        MemoryCopyWorkload(),
        StorageWriteWorkload(benchDir),
        StorageDurableWriteWorkload(benchDir),
        StorageRandomWriteFsyncWorkload(benchDir),
        StorageReadWorkload(benchDir),
    )
    private val store = BenchmarkRunStore(application.filesDir)

    private val _state = MutableStateFlow<BenchmarkUiState>(BenchmarkUiState.Idle)
    val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()

    fun run() {
        if (!BenchmarkRunCoordinator.tryAcquire()) {
            _state.value = BenchmarkUiState.Done(emptyList(), "另一个 benchmark 正在运行")
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _state.value = BenchmarkUiState.Running
                val sessionId = java.util.UUID.randomUUID().toString()
                val results = mutableListOf<RunResult>()
                var error: String? = null
                for (workload in workloads) {
                    val outcome = runCatching { engine.execute(workload) }
                    val manifest = outcome.getOrNull()?.copy(sessionId = sessionId)
                    if (manifest == null) {
                        error = outcome.exceptionOrNull()?.message
                        break
                    }
                    val saved = runCatching { store.save(manifest.toBenchmarkRun()).name }.getOrNull()
                    results += RunResult(manifest, saved)
                }
                _state.value = BenchmarkUiState.Done(results, error)
            } finally {
                BenchmarkRunCoordinator.release()
            }
        }
    }
}
