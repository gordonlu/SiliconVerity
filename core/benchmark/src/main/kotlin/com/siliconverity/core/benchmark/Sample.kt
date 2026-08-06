package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class Sample(
    val index: Int,
    val workUnits: Long,
    val durationNanos: Long,
    val timestamp: String,
) {
    val throughput: Double get() = if (durationNanos > 0) workUnits.toDouble() / (durationNanos / 1_000_000_000.0) else 0.0
}
