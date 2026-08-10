package com.siliconverity.nativegpu

import android.view.Surface

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
    private external fun nativeRunVulkanGraphics(surface: Surface, warmupMs: Int, durationMs: Int): String
    private external fun nativeCancelVulkanGraphics()

    fun run(workload: GpuWorkload, targetDurationMs: Int = 300): NativeGpuResult {
        val s = nativeRunVulkanBenchmark(workload.id, targetDurationMs)
        return NativeGpuResult.parse(s)
    }

    fun runGraphics(surface: Surface, warmupMs: Int = 2_000, durationMs: Int = 20_000): NativeGpuResult {
        return NativeGpuResult.parse(nativeRunVulkanGraphics(surface, warmupMs, durationMs))
    }

    fun cancelGraphics() = nativeCancelVulkanGraphics()
}
