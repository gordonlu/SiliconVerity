package com.siliconverity.core.benchmark

object WorkloadFormat {

    fun isFloatingPoint(workloadId: String): Boolean =
        workloadId.contains("fp32") || workloadId.contains("fp16") || workloadId.contains(".fp.")

    fun unit(workloadId: String): String =
        if (isFloatingPoint(workloadId)) "GFLOPS" else "M ops/s"

    fun scale(workloadId: String, value: Double): Double =
        if (isFloatingPoint(workloadId)) value / 1_000_000_000.0 else value / 1_000_000.0
}
