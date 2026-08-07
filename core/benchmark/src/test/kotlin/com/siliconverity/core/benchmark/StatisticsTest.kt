package com.siliconverity.core.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsTest {

    @Test
    fun median_odd() = assertEquals(3.0, Statistics.median(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 1e-9)

    @Test
    fun median_even() = assertEquals(2.5, Statistics.median(listOf(1.0, 2.0, 3.0, 4.0)), 1e-9)

    @Test
    fun median_empty() = assertEquals(0.0, Statistics.median(emptyList()), 1e-9)

    @Test
    fun median_single() = assertEquals(7.0, Statistics.median(listOf(7.0)), 1e-9)

    @Test
    fun mad_known() {
        assertEquals(1.0, Statistics.mad(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 1e-9)
    }

    @Test
    fun cv_zero_when_stable() {
        assertEquals(0.0, Statistics.cv(listOf(100.0, 100.0, 100.0, 100.0, 100.0)), 1e-9)
    }

    @Test
    fun cv_nonzero_known() {
        assertEquals(1.0 / 3.0, Statistics.cv(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 1e-9)
    }

    @Test
    fun min_max() {
        assertEquals(1.0, Statistics.min(listOf(3.0, 1.0, 2.0)), 1e-9)
        assertEquals(3.0, Statistics.max(listOf(3.0, 1.0, 2.0)), 1e-9)
    }

    @Test
    fun summarize_count_and_positive_median() {
        val samples = listOf(
            Sample(0, 50_000_000L, 138_000_000L, "t0"),
            Sample(1, 50_000_000L, 139_000_000L, "t1"),
            Sample(2, 50_000_000L, 138_500_000L, "t2"),
        )
        val s = Statistics.summarize(samples)
        assertEquals(3, s.count)
        assertTrue(s.median > 0)
        assertTrue(s.cv < 0.05)
    }

    @Test
    fun summarize_empty_is_safe() {
        val s = Statistics.summarize(emptyList())
        assertEquals(0, s.count)
        assertEquals(0.0, s.median, 1e-9)
    }

    @Test
    fun trendSlope_constant_is_zero() {
        assertEquals(0.0, Statistics.trendSlope(listOf(100.0, 100.0, 100.0, 100.0)), 1e-9)
    }

    @Test
    fun trendSlope_increasing_is_positive() {
        assertTrue(Statistics.trendSlope(listOf(1.0, 2.0, 3.0, 4.0)) > 0.0)
    }

    @Test
    fun outlierCount_stable_is_zero() {
        assertEquals(0, Statistics.outlierCount(listOf(100.0, 100.0, 100.0, 100.0)))
    }

    @Test
    fun outlierCount_detects_outlier() {
        // median 3, MAD small; 1000 is a clear outlier
        assertEquals(1, Statistics.outlierCount(listOf(1.0, 2.0, 3.0, 4.0, 1000.0)))
    }

    @Test
    fun outlierCount_mad_zero_flags_non_median() {
        // MAD=0 (all 100 except 200): 200 != median(100) -> 1 outlier (原返回 0 漏检)
        assertEquals(1, Statistics.outlierCount(listOf(100.0, 100.0, 100.0, 100.0, 200.0)))
    }

    @Test
    fun relativeTrend_constant_is_zero() {
        assertEquals(0.0, Statistics.relativeTrend(listOf(100.0, 100.0, 100.0, 100.0)), 1e-9)
    }
}
