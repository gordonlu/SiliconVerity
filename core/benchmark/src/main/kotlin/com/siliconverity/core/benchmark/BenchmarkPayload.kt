package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

/**
 * 统一结果形态。不同 workload 产出不同 payload，但外层 BenchmarkRun 统一。
 * 不强行把曲线/时间线/诊断塞成普通 Sample。
 */
@Serializable
sealed interface BenchmarkPayload {

    /** 标量: CPU/Memory/Storage/GPU-FP32 等单指标 workload。 */
    @Serializable
    data class Scalar(
        val samples: List<Sample>,
        val summary: Statistics.Summary,
    ) : BenchmarkPayload

    /** 时间线: 持续性能 (per-window 吞吐 + 热曲线 + 保持率)。 */
    @Serializable
    data class Timeline(
        val result: SustainedResult,
    ) : BenchmarkPayload

    /** 工作集曲线: 内存延迟 (size -> latency)。 */
    @Serializable
    data class Curve(
        val points: List<LatencyPoint>,
    ) : BenchmarkPayload

    /** 诊断: GPU submission diagnostics 等多指标诊断。 */
    @Serializable
    data class Diagnostics(
        val metrics: Map<String, Double>,
    ) : BenchmarkPayload
}
