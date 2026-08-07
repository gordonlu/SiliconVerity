package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class LatencyPoint(val sizeBytes: Long, val latencyNs: Double)

@Serializable
data class MemoryLatencyResult(
    val runId: String,
    val startedAt: String,
    val deviceModel: String,
    val socReported: String,
    val androidVersion: String,
    val abi: String,
    val points: List<LatencyPoint>,
)
