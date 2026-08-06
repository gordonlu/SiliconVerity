package com.siliconverity.nativegpu

class VulkanBench {
    init {
        System.loadLibrary("sv_gpu")
    }

    private external fun nativeProbe(): String

    val probe: String get() = nativeProbe()
}
