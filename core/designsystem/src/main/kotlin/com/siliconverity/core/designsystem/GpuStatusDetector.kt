package com.siliconverity.core.designsystem

import android.content.Context
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.payloadSummaryMedian
import java.io.File

/**
 * GPU 满载状态检测 (供结果页/会话聚合显示):
 * 1) sysfs 读 GPU 实际频率 (devfreq 多路径, 不可读返回 null)
 * 2) 与参考值比值判定: vulkan.fp32.independent < 0.75 视为疑似半速/节能
 */
object GpuStatusDetector {

    private val devfreqPaths = listOf(
        "/sys/class/devfreq/gpufreq/cur_freq",
        "/sys/class/devfreq/mali/cur_freq",
        "/sys/class/devfreq/gpu/cur_freq",
        "/sys/kernel/gpu/gpu_clock",
    )

    /** GPU 当前频率 (MHz); 不可读返回 null。 */
    fun currentFreqMhz(): Int? {
        for (path in devfreqPaths) {
            val v = runCatching { File(path).readText().trim().toLongOrNull() }.getOrNull()
            if (v != null && v > 0) return (v / 1_000_000).toInt().takeIf { it > 0 } ?: (v / 1000).toInt()
        }
        return null
    }

    /**
     * GPU 满载状态: "OK" / "LOW"(疑似未满载) / null(无 GPU 数据)。
     * ratio = independent 实测/参考; < 0.75 视为半速。
     */
    fun status(context: Context, runs: List<BenchmarkRun>): String? {
        val independent = runs.firstOrNull { it.identity.workloadId == "vulkan.fp32.independent" }
            ?: return null
        val med = independent.payloadSummaryMedian()
        val ref = SessionScorer.refValue(context, "vulkan.fp32.independent") ?: return null
        if (med <= 0.0 || ref <= 0.0) return null
        val ratio = med / ref
        return if (ratio < 0.75) "LOW" else "OK"
    }

    /** 展示文案: 频率 + 状态。 */
    fun display(context: Context, runs: List<BenchmarkRun>): String? {
        val st = status(context, runs) ?: return null
        val freq = currentFreqMhz()?.let { "${it} MHz" }
        return when (st) {
            "OK" -> if (freq != null) "GPU 满载 · $freq" else "GPU 满载"
            else -> if (freq != null) "GPU 疑似未满载（节能/半速）· $freq" else "GPU 疑似未满载（节能/半速）"
        }
    }
}
