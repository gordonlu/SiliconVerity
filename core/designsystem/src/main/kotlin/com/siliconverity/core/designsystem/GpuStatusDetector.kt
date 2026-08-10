package com.siliconverity.core.designsystem

import android.content.Context
import com.siliconverity.core.benchmark.BenchmarkPayload
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.payloadSummaryMedian
import com.siliconverity.core.storage.BenchmarkRunStore
import java.io.File

/**
 * GPU 满载状态检测 (设备自基线, 与参考包无关):
 * 本次 independent 实测 vs 该设备历史 session-median 的中位数;
 * < 0.75 视为疑似未满载 (节能/半速)。无历史基线时不判定。
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

    /** 设备历史基线: 该 workload 历史 median 的中位数 (排除本次会话)。 */
    fun baseline(context: Context, workloadId: String, excludeSessionId: String): Double? {
        val runs = runCatching { BenchmarkRunStore(context.filesDir).list() }.getOrDefault(emptyList())
        val medians = runs
            .filter { it.identity.workloadId == workloadId && it.identity.sessionId != excludeSessionId }
            .mapNotNull { (it.payload as? BenchmarkPayload.Scalar)?.summary?.median }
            .sorted()
        if (medians.isEmpty()) return null
        return medians[medians.size / 2]
    }

    /**
     * GPU 满载状态: "OK" / "LOW" / null(无 GPU 数据或无历史基线)。
     */
    fun status(context: Context, runs: List<BenchmarkRun>): String? {
        val independent = runs.firstOrNull { it.identity.workloadId == "vulkan.fp32.independent" } ?: return null
        val med = independent.payloadSummaryMedian()
        if (med <= 0.0) return null
        val sessionId = independent.identity.sessionId
        val base = baseline(context, "vulkan.fp32.independent", sessionId) ?: return null
        if (base <= 0.0) return null
        return if (med / base < 0.75) "LOW" else "OK"
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
