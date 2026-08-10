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
     * 单 workload 稳定阈值覆盖 (null = 用 protocol 全局 3%)。
     * I/O/GPU 等天然高抖动 workload (内核调度/闪存/GPU 时钟) 可放宽,
     * 避免 VARIABLE 误报; RETEST 线 (7%) 不变。
     */
    val stableCvThresholdOverride: Double?
        get() = null

    /** 非评分诊断信息（例如 native 协议/驱动反馈），用于结果追溯。 */
    val diagnostics: List<String>
        get() = emptyList()

    /**
     * Calibration: 把工作量调整到接近 targetMillis/轮 (每 workload 自校准)。
     * 固定工作量的 workload (内存/存储/GPU) 可不覆写 (默认 no-op)。
     */
    fun calibrate(targetMillis: Long) {}
}
