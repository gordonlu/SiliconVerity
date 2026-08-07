package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class WorkloadScore(
    val workloadId: String,
    val workloadVersion: String,
    val measuredValue: Double,
    val referenceValue: Double,
    val normalizedScore: Double,
    val weight: Double,
    val eligible: Boolean,
    val exclusionReason: String? = null,
)

@Serializable
data class ScoreExclusion(
    val workloadId: String,
    val reason: String,
)

@Serializable
data class ScoreConfidence(
    val level: String,          // HIGH / MEDIUM / LOW (全 STABLE=HIGH, 有 VARIABLE=MEDIUM, 其他=LOW)
    val eligibleWorkloads: Int,
    val totalWorkloads: Int,
)

@Serializable
data class ScoreReport(
    val sessionId: String,
    val scoreVersion: String,
    val referencePackVersion: String,
    val overallScore: Int?,
    val cpuScore: Int?,
    val gpuScore: Int?,
    val memoryScore: Int?,
    val appIoScore: Int?,
    val sustainedScore: Int? = null,
    val retentionPercent: Double? = null,
    val confidence: ScoreConfidence,
    val coveragePercent: Double,
    val exclusions: List<ScoreExclusion> = emptyList(),
    val workloadScores: List<WorkloadScore> = emptyList(),
)
