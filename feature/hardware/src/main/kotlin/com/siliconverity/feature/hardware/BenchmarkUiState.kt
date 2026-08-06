package com.siliconverity.feature.hardware

import com.siliconverity.core.benchmark.RunManifest

sealed interface BenchmarkUiState {
    data object Idle : BenchmarkUiState
    data object Running : BenchmarkUiState
    data class Done(
        val manifest: RunManifest?,
        val error: String?,
        val savedPath: String? = null,
    ) : BenchmarkUiState
}
