package com.siliconverity.core.benchmark

interface Workload {
    val spec: BenchmarkSpec

    /** 正确性校验方式；默认 EXACT（含重跑 bit-exact 一致）。浮点 workload 可覆写为 ULP 等。 */
    val checksumKind: ChecksumKind
        get() = ChecksumKind.EXACT

    fun warmUp()

    fun runOnce(): Sample

    /** 正确性校验: 返回 passed + kind + finite + reason (为 golden vector/reference 铺路)。 */
    fun correctnessCheck(): CorrectnessResult

    /**
     * Calibration: 把工作量调整到接近 targetMillis/轮 (每 workload 自校准)。
     * 固定工作量的 workload (内存/存储/GPU) 可不覆写 (默认 no-op)。
     */
    fun calibrate(targetMillis: Long) {}
}
