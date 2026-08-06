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

    data class Summary(
        val median: Double,
        val mad: Double,
        val cv: Double,
        val min: Double,
        val max: Double,
        val count: Int,
    )

    fun summarize(samples: List<Sample>): Summary {
        val values = samples.map { it.throughput }
        val med = median(values)
        return Summary(
            median = med,
            mad = mad(values, med),
            cv = cv(values),
            min = min(values),
            max = max(values),
            count = values.size,
        )
    }
}
