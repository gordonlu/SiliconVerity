package com.siliconverity.core.benchmark

import kotlin.math.abs

object Statistics {

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    fun mad(values: List<Double>, center: Double = median(values)): Double {
        if (values.isEmpty()) return 0.0
        val deviations = values.map { abs(it - center) }
        return median(deviations)
    }

    fun cv(values: List<Double>): Double {
        val center = median(values)
        if (center == 0.0) return Double.NaN
        val m = mad(values, center)
        return m / center
    }

    fun min(values: List<Double>): Double = values.minOrNull() ?: 0.0
    fun max(values: List<Double>): Double = values.maxOrNull() ?: 0.0

    /** 线性回归斜率 (value vs index)，检测性能漂移。稳定时 ~0。 */
    fun trendSlope(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return 0.0
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dx = i - meanX
            num += dx * (values[i] - meanY)
            den += dx * dx
        }
        return if (den != 0.0) num / den else 0.0
    }

    /** 离群点数：偏离 median 超过 3×MAD。 */
    fun outlierCount(values: List<Double>, center: Double = median(values), m: Double = mad(values, center)): Int {
        if (m == 0.0) return 0
        val thresh = 3.0 * m
        return values.count { abs(it - center) > thresh }
    }

    data class Summary(
        val median: Double,
        val mad: Double,
        val cv: Double,
        val minimum: Double,
        val maximum: Double,
        val trendSlope: Double,
        val outlierCount: Int,
        val count: Int,
    )

    fun summarize(samples: List<Sample>): Summary {
        val values = samples.map { it.throughput }
        val med = median(values)
        val m = mad(values, med)
        return Summary(
            median = med,
            mad = m,
            cv = cv(values),
            minimum = min(values),
            maximum = max(values),
            trendSlope = trendSlope(values),
            outlierCount = outlierCount(values, med, m),
            count = values.size,
        )
    }
}
