package com.siliconverity.core.benchmark

data class RunResult(val manifest: RunManifest, val savedPath: String?)

sealed interface BenchmarkUiState {
    data object Idle : BenchmarkUiState
    data object Running : BenchmarkUiState
    data class Done(val results: List<RunResult>, val error: String?) : BenchmarkUiState
}
