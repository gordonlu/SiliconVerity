package com.siliconverity.feature.sustained

import com.siliconverity.core.benchmark.SustainedProgress
import com.siliconverity.core.benchmark.SustainedResult

sealed interface SustainedUiState {
    data object Idle : SustainedUiState
    data class Running(val progress: SustainedProgress) : SustainedUiState
    data class Done(val result: SustainedResult, val savedPath: String?) : SustainedUiState
    data class Error(val message: String) : SustainedUiState
}
