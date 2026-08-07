package com.siliconverity.core.benchmark

object WorkloadFormat {

    fun isFloatingPoint(workloadId: String): Boolean =
        workloadId.contains("fp32") || workloadId.contains("fp16") || workloadId.contains(".fp.")

    fun isStorage(workloadId: String): Boolean = workloadId.startsWith("storage.")

    fun isMixedCompression(workloadId: String): Boolean = workloadId.startsWith("cpu.compress")

    fun isMemoryBandwidth(workloadId: String): Boolean = workloadId.startsWith("mem.bandwidth")

    fun isVulkanFp32(workloadId: String): Boolean = workloadId.startsWith("vulkan.fp32")

    fun isVulkanBuffer(workloadId: String): Boolean = workloadId.startsWith("vulkan.buffer")

    fun unit(workloadId: String): String = when {
        isFloatingPoint(workloadId) -> "GFLOPS"
        isStorage(workloadId) -> "MB/s"
        isMixedCompression(workloadId) -> "MB/s"
        isMemoryBandwidth(workloadId) -> "GB/s"
        isVulkanFp32(workloadId) -> "GFLOPS"
        isVulkanBuffer(workloadId) -> "GB/s"
        else -> "M ops/s"
    }

    fun scale(workloadId: String, value: Double): Double = when {
        isFloatingPoint(workloadId) -> value / 1_000_000_000.0
        isStorage(workloadId) -> value / 1_000_000.0
        isMixedCompression(workloadId) -> value / 1_000_000.0
        isMemoryBandwidth(workloadId) -> value / 1_000_000_000.0
        isVulkanFp32(workloadId) -> value / 1_000_000_000.0
        isVulkanBuffer(workloadId) -> value / 1_000_000_000.0
        else -> value / 1_000_000.0
    }
}
