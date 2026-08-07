package com.siliconverity.benchmark

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.CorrectnessResult
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import com.siliconverity.nativegpu.GpuWorkload
import com.siliconverity.nativegpu.VulkanBench

/**
 * Vulkan Compute MiniBench 的 Workload 适配器, 使 GPU 测试可并入主套件
 * 走标准 BenchmarkEngine 协议 (采样/稳定性判定/门禁)。
 * 单次 runOnce = 一次 bench.run(variant, 300ms) (native 内部自校准迭代)。
 */
class GpuVulkanWorkload(
    private val bench: VulkanBench,
    private val variant: GpuWorkload,
    workloadId: String,
    private val stableCvOverride: Double? = null,
) : Workload {

    /** GPU 时钟/总线波动: buffer 带宽测试放宽到 5% (独立/依赖变体保持 3%)。 */
    override val stableCvThresholdOverride: Double? = stableCvOverride

    override val spec = BenchmarkSpec(
        workloadId = workloadId,
        workloadVersion = "0.1.0-alpha",
        category = "gpu",
        measurementTarget = "throughput",
        algorithm = "vulkan-compute-minibench",
        implementationBackend = "vulkan",
        dataSize = 0L,
        threadPolicy = "gpu",
        timingMethod = "gpu-timestamp",
        warmupMinMillis = 4_000L,
        warmupMaxMillis = 8_000L,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 5,
        correctnessCheck = "checksum-valid",
        invalidationRules = listOf("unsupported", "checksum-mismatch", "metric-nonfinite"),
    )

    private var lastError: String? = null

    override fun warmUp() {}

    override fun runOnce(): Sample {
        val r = runCatching { bench.run(variant, 300) }.getOrNull()
        if (r == null) {
            lastError = "vulkan run failed"
            return Sample(0, 0L, 1_000_000_000L, "gpu")
        }
        val metric = r.metricValue
        if (!r.supported || metric == null || !metric.isFinite() || metric <= 0.0) {
            lastError = r.invalidReason ?: "unsupported or metric unavailable"
            return Sample(0, 0L, 1_000_000_000L, "gpu")
        }
        lastError = null
        val perSec = metric * 1_000_000_000.0
        return Sample(0, perSec.toLong(), 1_000_000_000L, "gpu")
    }

    override fun correctnessCheck(): CorrectnessResult = CorrectnessResult(
        passed = lastError == null,
        kind = checksumKind,
        reason = lastError,
    )
}
