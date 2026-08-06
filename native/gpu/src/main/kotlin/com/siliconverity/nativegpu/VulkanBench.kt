package com.siliconverity.nativegpu

enum class GpuWorkload(val id: Int) {
    FP32_COMPUTE(0),
    BUFFER_THROUGHPUT(1),
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
