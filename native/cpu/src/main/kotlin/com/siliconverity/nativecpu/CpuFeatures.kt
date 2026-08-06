package com.siliconverity.nativecpu

class CpuFeatures {

    init {
        System.loadLibrary("sv_cpu_int")
    }

    private external fun nativeFeatures(): String
    private external fun nativeHwcap(): LongArray

    val features: String get() = nativeFeatures()

    val hwcap: LongArray get() = nativeHwcap()

    fun hwcapHex(): String {
        val h = hwcap
        return "hwcap=0x${h[0].toString(16)} hwcap2=0x${h[1].toString(16)}"
    }
}
