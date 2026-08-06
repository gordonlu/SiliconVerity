package com.siliconverity.benchmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.feature.gpu.GpuUiState
import com.siliconverity.nativegpu.GpuWorkload
import com.siliconverity.nativegpu.VulkanBench
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GpuController(application: Application) : AndroidViewModel(application) {

    private val bench = VulkanBench()

    private val _state = MutableStateFlow<GpuUiState>(GpuUiState.Idle)
    val state: StateFlow<GpuUiState> = _state.asStateFlow()

    fun run() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = GpuUiState.Running
            val fp32Res = runCatching { bench.run(GpuWorkload.FP32_COMPUTE, 300) }
            val triadRes = runCatching { bench.run(GpuWorkload.BUFFER_THROUGHPUT, 300) }
            val err = fp32Res.exceptionOrNull()?.message ?: triadRes.exceptionOrNull()?.message
            _state.value = GpuUiState.Done(fp32Res.getOrNull(), triadRes.getOrNull(), err)
        }
    }

    fun reset() {
        _state.value = GpuUiState.Idle
    }
}
