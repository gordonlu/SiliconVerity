package com.siliconverity.core.benchmark

interface Workload {
    val spec: BenchmarkSpec

    /** 正确性校验方式；默认 EXACT（含重跑 bit-exact 一致）。浮点 workload 可覆写为 ULP 等。 */
    val checksumKind: ChecksumKind
        get() = ChecksumKind.EXACT

    fun warmUp()

    fun runOnce(): Sample

    fun correctnessCheck(): Boolean
}
