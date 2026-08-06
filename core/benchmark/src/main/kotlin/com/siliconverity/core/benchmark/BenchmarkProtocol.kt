package com.siliconverity.core.benchmark

/**
 * 版本化的测量协议。所有可调参数集中于此，不得散落魔法数字。
 * 当前为 PROVISIONAL 初值，待设备矩阵校准后冻结为 v1。
 */
data class BenchmarkProtocol(
    val protocolVersion: String,
    val warmupMinSeconds: Double,
    val warmupMaxSeconds: Double,
    val warmupConvergeThreshold: Double,
    val measurementMinSamples: Int,
    val measurementRecommendedSamples: Int,
    val measurementMaxSamples: Int,
    val stableCvThreshold: Double,
    val variableCvThreshold: Double,
    val targetRoundMillis: Int,
    val provisional: Boolean = true,
)

val DefaultBenchmarkProtocol: BenchmarkProtocol = BenchmarkProtocol(
    protocolVersion = "0.1.0",
    warmupMinSeconds = 2.0,
    warmupMaxSeconds = 8.0,
    warmupConvergeThreshold = 0.03,
    measurementMinSamples = 5,
    measurementRecommendedSamples = 7,
    measurementMaxSamples = 11,
    stableCvThreshold = 0.03,
    variableCvThreshold = 0.07,
    targetRoundMillis = 300,
    provisional = true,
)
