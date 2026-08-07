package com.siliconverity.core.benchmark

data class RunResult(val manifest: RunManifest, val savedPath: String?)

sealed interface BenchmarkUiState {
    data object Idle : BenchmarkUiState
    data object Scoring : BenchmarkUiState

    data class Running(
        val sessionId: String,
        val index: Int,
        val total: Int,
        val category: BenchmarkCategory,
        val workloadId: String,
        val phase: BenchmarkPhase,
        val sampleIndex: Int? = null,
        val sampleCount: Int? = null,
        val completed: List<WorkloadProgress>,
        val environment: LiveEnvironmentSnapshot,
        val paused: Boolean = false,
    ) : BenchmarkUiState

    data class Done(
        val results: List<RunResult>,
        val error: String?,
        val score: ScoreReport? = null,
        val sessionStartedAt: String? = null,
    ) : BenchmarkUiState
}
