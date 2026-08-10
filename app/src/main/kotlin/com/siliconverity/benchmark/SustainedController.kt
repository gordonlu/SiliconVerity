package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.os.PowerManager
import com.siliconverity.core.benchmark.SustainedProgress
import com.siliconverity.core.benchmark.SustainedRunner
import com.siliconverity.core.benchmark.toBenchmarkRun
import com.siliconverity.core.storage.BenchmarkRunStore
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
    private val store = BenchmarkRunStore(application.filesDir)

    private val _state = MutableStateFlow<SustainedUiState>(SustainedUiState.Idle)
    val state: StateFlow<SustainedUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock(durationSec: Int) {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sv:sustained").apply {
            setReferenceCounted(false)
            acquire((durationSec + 60L) * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    fun start(durationSec: Int) {
        if (!BenchmarkRunCoordinator.tryAcquire()) {
            _state.value = SustainedUiState.Error("另一个 benchmark 正在运行")
            return
        }
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                acquireWakeLock(durationSec)
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
                        val saved = runCatching { store.save(it.toBenchmarkRun()).name }.getOrNull()
                        SustainedUiState.Done(it, saved)
                    },
                    onFailure = { SustainedUiState.Error(it.message ?: "unknown error") },
                )
            } finally {
                releaseWakeLock()
                BenchmarkRunCoordinator.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        releaseWakeLock()
        _state.value = SustainedUiState.Idle
    }

    fun reset() {
        _state.value = SustainedUiState.Idle
    }
}
