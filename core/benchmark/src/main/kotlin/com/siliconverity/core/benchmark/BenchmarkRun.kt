package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

/** 运行标识与版本信息。 */
@Serializable
data class RunIdentity(
    val runId: String,
    val sessionId: String = "",
    val benchmarkProtocolVersion: String,
    val workloadId: String,
    val workloadVersion: String,
    val shaderSourceVersion: String? = null,
    val spirvHash: String? = null,
    val pipelineConfigVersion: String? = null,
    val scoreVersion: String? = null,
    val arithmeticType: ArithmeticType? = null,
    val arithmeticContract: ArithmeticContract? = null,
    val checksumKind: ChecksumKind? = null,
)

/** 环境快照 (测试前后 + 期间独立低频采样)。 */
@Serializable
data class EnvironmentSnapshot(
    val appVersion: String,
    val engineVersion: String,
    val abi: String,
    val androidVersion: String,
    val securityPatch: String,
    val deviceModel: String,
    val socReported: String,
    val batteryLevel: Int,
    val chargingState: String,
    val powerSaveMode: Boolean,
    val thermalStatusStart: String,
    val thermalStatusEnd: String,
    val thermalTimeline: List<ThermalSample> = emptyList(),
)

/** 协议快照 (本次实际使用的 protocol 参数)。 */
@Serializable
data class ProtocolSnapshot(
    val protocolVersion: String,
    val warmupMinSeconds: Double,
    val warmupMaxSeconds: Double,
    val warmupConvergeThreshold: Double,
    val measurementSamplesActual: Int,
    val stableCvThreshold: Double,
    val variableCvThreshold: Double,
    val targetRoundMillis: Int,
    val provisional: Boolean,
)

/** 正确性结果 (golden vector / reference / NaN-Inf)。 */
@Serializable
data class CorrectnessResult(
    val passed: Boolean,
    val kind: ChecksumKind,
    val expected: String? = null,
    val actual: String? = null,
    val maxAbsError: Double? = null,
    val maxRelError: Double? = null,
    val maxUlpError: Long? = null,
    val finite: Boolean = true,
    val reason: String? = null,
)

/** 有效性 (correctness + stability 统一判定)。 */
@Serializable
data class ValidityResult(
    val valid: Boolean,
    val scoreEligible: Boolean,
    val stability: ValidityLevel,
    val robustCv: Double,
    val reason: String? = null,
)

/**
 * 统一运行记录。外层统一: 会话/环境/版本/取消/正确性/有效性/持久化。
 * payload 不强行统一 (Scalar/Timeline/Curve/Diagnostics)。
 */
@Serializable
data class BenchmarkRun(
    val identity: RunIdentity,
    val environment: EnvironmentSnapshot,
    val protocol: ProtocolSnapshot,
    val correctness: CorrectnessResult,
    val validity: ValidityResult,
    val payload: BenchmarkPayload,
    val startedAt: String,
    val endedAt: String,
    val actualDurationNanos: Long,
    val warnings: List<String> = emptyList(),
)
