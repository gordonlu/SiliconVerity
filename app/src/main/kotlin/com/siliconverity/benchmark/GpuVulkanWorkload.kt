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
 * native 协议已内置 saturation prime + 12 轮 + transition/双峰检测,
 * 因此 runOnce 只执行一次完整 native 协议并缓存结果,
 * engine 的重复采样消费 native 内部保存的真实 7 轮时长，避免二次执行完整协议，
 * 同时保留真实 CV，而不是复制同一个中位数得到伪 CV=0。
 * native 检测到性能档位切换时，最多追加两次完整分项测试，并在所有
 * correctness 合格的候选中选择吞吐最高者，避免最后一次半速覆盖峰值。
 */
class GpuVulkanWorkload(
    private val bench: VulkanBench,
    private val variant: GpuWorkload,
    workloadId: String,
    private val stableCvOverride: Double? = null,
) : Workload {

    /** GPU 时钟/总线波动: buffer 带宽测试放宽到 5% (独立/依赖变体保持 3%)。 */
    override val stableCvThresholdOverride: Double? = stableCvOverride
        ?: if (variant == GpuWorkload.BUFFER_THROUGHPUT) 0.05 else null

    private val fp32Compute = variant != GpuWorkload.BUFFER_THROUGHPUT

    override val spec = BenchmarkSpec(
        workloadId = workloadId,
        workloadVersion = if (variant == GpuWorkload.FP32_DEPENDENCY) "0.3.0-alpha" else "0.2.0-alpha",
        category = "gpu",
        measurementTarget = "throughput",
        algorithm = if (fp32Compute) {
            if (variant == GpuWorkload.FP32_DEPENDENCY) {
                "4096 workgroups, fixed-work 200ms prime batches, serialized dependency chain"
            } else {
                "4096 workgroups, fixed-work 200ms prime batches, 64 repeated dispatches with compute barriers"
            }
        } else {
            "16M-element FP32 triad, fixed-work saturation prime and repeated dispatch measurement"
        },
        implementationBackend = "vulkan",
        dataSize = if (fp32Compute) 4L * 1024 * 1024 else 3L * 64 * 1024 * 1024,
        threadPolicy = "gpu",
        timingMethod = "host-submit-to-fence",
        warmupMinMillis = 0L,
        warmupMaxMillis = 0L,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 5,
        correctnessCheck = "checksum-valid",
        invalidationRules = listOf("unsupported", "checksum-mismatch", "metric-nonfinite"),
    )

    private var cachedSamples: List<Sample> = emptyList()
    private var sampleCursor = 0
    private var cachedRetest = false
    private var lastError: String? = null
    private var lastNativeDiagnostics: String? = null

    override val diagnostics: List<String>
        get() = listOfNotNull(lastNativeDiagnostics)

    override fun warmUp() {}

    override fun runOnce(): Sample {
        if (sampleCursor < cachedSamples.size) return cachedSamples[sampleCursor++]
        // 禁止样本耗尽后复制最后一个值制造 CV=0。若调用方开始了新一轮，必须
        // 重新执行完整 native 协议并取得一组新的真实样本。
        realRunOnce()
        return cachedSamples[sampleCursor++]
    }

    /** 新一轮完整套件前清除缓存 (下一次 runOnce 重新执行完整协议)。 */
    fun reset() {
        cachedSamples = emptyList()
        sampleCursor = 0
        cachedRetest = false
        lastError = null
        lastNativeDiagnostics = null
    }

    private fun realRunOnce() {
        cachedSamples = emptyList()
        sampleCursor = 0
        var latest = runCatching { bench.run(variant, 300) }.getOrNull()
        var best = latest?.takeIf { it.isScoreCandidate() }
        var attempts = if (latest == null) 0 else 1
        if (variant != GpuWorkload.BUFFER_THROUGHPUT && latest?.retestNeeded == true) {
            for (attempt in 0 until 2) {
                val retry = runCatching { bench.run(variant, 300) }.getOrNull() ?: continue
                attempts++
                latest = retry
                if (retry.isScoreCandidate() &&
                    (best?.metricValue == null || retry.metricValue!! > best!!.metricValue!!)
                ) {
                    best = retry
                }
                if (!retry.retestNeeded) break
            }
        }
        val r = best ?: latest
        // 用户成绩采用 correctness 合格候选中的峰值；档位切换仅进入内部诊断。
        cachedRetest = false
        lastNativeDiagnostics = r?.diag?.let { "gpu-native: $it peakSelectAttempts=$attempts" }
        if (r == null) {
            lastError = "vulkan run failed"
            cachedSamples = listOf(Sample(0, 0L, 1_000_000_000L, "gpu"))
            return
        }
        val metric = r.metricValue
        if (!r.supported || !r.checksumValid || !r.invalidReason.isNullOrEmpty() ||
            metric == null || !metric.isFinite() || metric <= 0.0
        ) {
            lastError = r.invalidReason ?: if (!r.checksumValid) "checksum mismatch" else "unsupported or metric unavailable"
            cachedSamples = listOf(Sample(0, 0L, 1_000_000_000L, "gpu"))
            return
        }
        lastError = null
        val medianNs = r.medianNs?.takeIf { it > 0L } ?: 1_000_000_000L
        val workUnits = (metric * medianNs.toDouble()).toLong().coerceAtLeast(1L)
        cachedSamples = r.sampleNanos.takeIf { it.isNotEmpty() }
            ?.mapIndexed { index, duration -> Sample(index, workUnits, duration, "gpu") }
            ?: listOf(Sample(0, (metric * 1_000_000_000.0).toLong(), 1_000_000_000L, "gpu"))
    }

    override fun correctnessCheck(): CorrectnessResult = CorrectnessResult(
        passed = lastError == null && !cachedRetest,
        kind = checksumKind,
        reason = lastError ?: if (cachedRetest) "GPU P-state transition/bimodal after retry" else null,
    )

    private fun NativeGpuResult.isScoreCandidate(): Boolean =
        supported && checksumValid && invalidReason.isNullOrEmpty() &&
            metricValue?.let { it.isFinite() && it > 0.0 } == true
}
