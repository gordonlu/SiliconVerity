package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.SustainedProgress
import com.siliconverity.core.benchmark.SustainedRunner
import com.siliconverity.core.storage.SustainedResultStore
import com.siliconverity.feature.sustained.SustainedUiState
import com.siliconverity.nativecpu.CpuIntegerWorkload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SustainedController(application: Application) : AndroidViewModel(application) {

    private val env = AndroidBenchmarkEnvironment(application)
    private val store = SustainedResultStore(File(application.filesDir, "sustained"))

    private val _state = MutableStateFlow<SustainedUiState>(SustainedUiState.Idle)
    val state: StateFlow<SustainedUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(durationSec: Int) {
        if (!BenchmarkRunCoordinator.tryAcquire()) {
            _state.value = SustainedUiState.Error("另一个 benchmark 正在运行")
            return
        }
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                _state.value = SustainedUiState.Running(
                    SustainedProgress(0.0, durationSec, 0.0, env.thermalStatusStart, emptyList()),
                )
                val workload = CpuIntegerWorkload()
                val runner = SustainedRunner(workload, env) { System.nanoTime() }
                val result = runCatching {
                    runner.run(durationSec, windowSec = 1) { p ->
                        _state.value = SustainedUiState.Running(p)
                    }
                }
                _state.value = result.fold(
                    onSuccess = {
                        val saved = runCatching { store.save(it).name }.getOrNull()
                        SustainedUiState.Done(it, saved)
                    },
                    onFailure = { SustainedUiState.Error(it.message ?: "unknown error") },
                )
            } finally {
                BenchmarkRunCoordinator.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = SustainedUiState.Idle
    }

    fun reset() {
        _state.value = SustainedUiState.Idle
    }
}
