package com.siliconverity.nativegpu

enum class GpuWorkload(val id: Int) {
    FP32_INDEPENDENT(0),
    FP32_DEPENDENCY(1),
    BUFFER_THROUGHPUT(2),
}

class VulkanBench {

    init {
        System.loadLibrary("sv_gpu")
    }

    private external fun nativeRunVulkanBenchmark(workload: Int, targetDurationMs: Int): String

    fun run(workload: GpuWorkload, targetDurationMs: Int = 300): NativeGpuResult {
        val s = nativeRunVulkanBenchmark(workload.id, targetDurationMs)
        return NativeGpuResult.parse(s)
    }
}
