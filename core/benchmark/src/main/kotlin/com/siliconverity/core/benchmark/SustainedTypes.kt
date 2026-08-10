package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class SustainedSample(
    val windowIndex: Int,
    val elapsedSec: Double,
    val throughput: Double,
    val thermalStatus: String,
)

@Serializable
data class SustainedResult(
    val runId: String,
    val workloadId: String,
    val workloadVersion: String,
    val deviceModel: String,
    val socReported: String,
    val androidVersion: String,
    val appVersion: String,
    val startedAt: String,
    val durationSec: Int,
    val windowSec: Int,
    val samples: List<SustainedSample>,
    val initialMedian: Double,
    val stableMedian: Double,
    val retention: Double,
    val timeTo90Percent: Double = -1.0,
    val timeTo80Percent: Double = -1.0,
    val worstStableWindow: Double = 0.0,
    val absoluteWorkCompleted: Long = 0,
    val thermalStatusStart: String,
    val thermalStatusEnd: String,
    val batteryLevel: Int,
    val chargingState: String,
    val actualDurationNanos: Long = 0L,
    val correctness: CorrectnessResult = CorrectnessResult(passed = true, kind = ChecksumKind.EXACT),
)

data class SustainedProgress(
    val elapsedSec: Double,
    val durationSec: Int,
    val currentThroughput: Double,
    val thermalStatus: String,
    val samples: List<SustainedSample>,
)
