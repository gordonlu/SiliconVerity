package com.siliconverity.core.benchmark

/** 单个 workload 的执行阶段; 状态只在阶段边界更新, 不订阅测量内部高频数据。 */
enum class BenchmarkPhase {
    CALIBRATING,
    WARMING_UP,
    MEASURING,
    VERIFYING,
    FINALIZING,
}

/** 阶段回调: (阶段, 样本序号?, 样本总数?)。仅阶段/样本边界触发。 */
fun interface PhaseListener {
    fun onPhase(phase: BenchmarkPhase, sampleIndex: Int?, sampleCount: Int?)
}
