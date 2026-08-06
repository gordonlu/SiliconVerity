package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class RunManifest(
    val runId: String,
    val sessionId: String = "",
    val appVersion: String,
    val benchmarkEngineVersion: String,
    val workloadId: String,
    val workloadVersion: String,
    val startedAt: String = "",
    val abi: String,
    val androidVersion: String,
    val securityPatch: String,
    val deviceModel: String,
    val socReported: String,
    val batteryLevel: Int,
    val chargingState: String,
    val powerSaveMode: Boolean = false,
    val thermalStatusStart: String,
    val thermalStatusEnd: String,
    val testOrder: List<String>,
    val warmupSamples: List<Sample>,
    val measurementSamples: List<Sample>,
    val median: Double,
    val mad: Double,
    val cv: Double,
    val correctnessStatus: Boolean,
    val validityLevel: ValidityLevel,
    val warnings: List<String> = emptyList(),
)
