package com.siliconverity.feature.gpu

import com.siliconverity.nativegpu.NativeGpuResult

sealed interface GpuUiState {
    data object Idle : GpuUiState
    data object Running : GpuUiState
    data class Done(
        val independent: NativeGpuResult?,
        val dependency: NativeGpuResult?,
        val buffer: NativeGpuResult?,
        val error: String?,
    ) : GpuUiState
}
