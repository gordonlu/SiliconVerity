package com.siliconverity.feature.gpu

import com.siliconverity.nativegpu.NativeGpuResult

sealed interface GpuUiState {
    data class Idle(val gameMode: String = "") : GpuUiState
    data class Running(val gameMode: String) : GpuUiState
    data class Done(
        val graphics: NativeGpuResult?,
        val independent: NativeGpuResult?,
        val dependency: NativeGpuResult?,
        val buffer: NativeGpuResult?,
        val error: String?,
        val gameMode: String = "",
    ) : GpuUiState
}
