package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkSpec(
    val workloadId: String,
    val workloadVersion: String,
    val category: String,
    val measurementTarget: String,
    val algorithm: String,
    val implementationBackend: String,
    val dataSize: Long,
    val threadPolicy: String,
    val timingMethod: String,
    val warmupMinMillis: Long,
    val warmupMaxMillis: Long,
    val warmupConvergeThreshold: Double,
    val measurementRepetitions: Int,
    val correctnessCheck: String,
    val invalidationRules: List<String> = emptyList(),
    val knownInterferences: List<String> = emptyList(),
)
