package com.siliconverity.core.benchmark

object WorkloadFormat {

    fun isFloatingPoint(workloadId: String): Boolean =
        workloadId.contains("fp32") || workloadId.contains("fp16") || workloadId.contains(".fp.")

    fun isStorage(workloadId: String): Boolean = workloadId.startsWith("storage.")

    fun isMemoryBandwidth(workloadId: String): Boolean = workloadId.startsWith("mem.bandwidth")

    fun unit(workloadId: String): String = when {
        isFloatingPoint(workloadId) -> "GFLOPS"
        isStorage(workloadId) -> "MB/s"
        isMemoryBandwidth(workloadId) -> "GB/s"
        else -> "M ops/s"
    }

    fun scale(workloadId: String, value: Double): Double = when {
        isFloatingPoint(workloadId) -> value / 1_000_000_000.0
        isStorage(workloadId) -> value / 1_000_000.0
        isMemoryBandwidth(workloadId) -> value / 1_000_000_000.0
        else -> value / 1_000_000.0
    }
}
