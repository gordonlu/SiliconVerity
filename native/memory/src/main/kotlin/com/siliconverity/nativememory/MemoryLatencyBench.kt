package com.siliconverity.nativememory

import com.siliconverity.core.benchmark.LatencyPoint

object MemoryLatencyBench {
    init {
        System.loadLibrary("sv_mem")
    }

    private external fun nativeRunLatency(sizeBytes: Long, accesses: Long, rounds: Int): Double

    /** 固定 size 列表 (PROVISIONAL, PRD §13.2 子集, 避免过大置换内存)。 */
    val sizes: LongArray = longArrayOf(
        4L * 1024,
        16L * 1024,
        64L * 1024,
        256L * 1024,
        1024L * 1024,
        4L * 1024 * 1024,
        16L * 1024 * 1024,
    )

    fun run(): List<LatencyPoint> {
        val out = ArrayList<LatencyPoint>(sizes.size)
        for (size in sizes) {
            val accesses = when {
                size <= 64L * 1024 -> 5_000_000L
                size <= 1024L * 1024 -> 2_000_000L
                else -> 1_000_000L
            }
            val ns = runCatching { nativeRunLatency(size, accesses, 7) }.getOrDefault(-1.0)
            out += LatencyPoint(size, ns)
        }
        return out
    }
}
