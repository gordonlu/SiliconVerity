package com.siliconverity.benchmark

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.CorrectnessResult
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import com.siliconverity.nativegpu.GpuWorkload
import com.siliconverity.nativegpu.NativeGpuResult
import com.siliconverity.nativegpu.VulkanBench

/**
 * Vulkan Compute MiniBench 的 Workload 适配器。
 * native 协议已内置 prime + 校准 + 7 轮 + transition/双峰重试,
 * 因此 runOnce 只执行一次完整 native 协议并缓存结果,
 * engine 的重复采样 (5-11 次) 返回同一结果, 避免双重采样导致单项耗时 20-55s。
 * controller 自动重测前需调用 reset() 清除缓存。
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
        timingMethod = "host-submit-to-fence",
        warmupMinMillis = 0L,
        warmupMaxMillis = 0L,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 5,
        correctnessCheck = "checksum-valid",
        invalidationRules = listOf("unsupported", "checksum-mismatch", "metric-nonfinite"),
    )

    private var cachedSample: Sample? = null
    private var cachedRetest = false
    private var lastError: String? = null

    override fun warmUp() {}

    override fun runOnce(): Sample {
        cachedSample?.let { return it }
        val sample = realRunOnce()
        cachedSample = sample
        return sample
    }

    /** 自动重测前清除缓存 (下一次 runOnce 重新执行完整协议)。 */
    fun reset() {
        cachedSample = null
        cachedRetest = false
        lastError = null
    }

    private fun realRunOnce(): Sample {
        var r = runCatching { bench.run(variant, 300) }.getOrNull()
        // native 检测到 P-state transition/双峰 -> 内部再跑最多 2 次
        if (r?.retestNeeded == true) {
            repeat(2) {
                val retry = runCatching { bench.run(variant, 300) }.getOrNull() ?: return@repeat
                if (!retry.retestNeeded && retry.metricValue != null) {
                    r = retry
                    return@repeat
                }
                r = retry
            }
        }
        cachedRetest = r?.retestNeeded == true
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
        passed = lastError == null && !cachedRetest,
        kind = checksumKind,
        reason = lastError ?: if (cachedRetest) "GPU P-state transition/bimodal after retry" else null,
    )
}
