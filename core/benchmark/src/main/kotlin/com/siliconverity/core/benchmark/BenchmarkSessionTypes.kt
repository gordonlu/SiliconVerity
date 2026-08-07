package com.siliconverity.core.benchmark

/** 套件分类 (进度/结果页展示用)。 */
enum class BenchmarkCategory {
    CPU,
    MEMORY,
    GPU,
    APP_IO,
}

/** 单项目进度 (会话完成后保留有效性)。 */
data class WorkloadProgress(
    val workloadId: String,
    val category: BenchmarkCategory,
    val valid: Boolean,
)

/** 运行中环境快照 (会话开始时采集一次, 测试页仅展示 3 项)。 */
data class LiveEnvironmentSnapshot(
    val thermal: String,
    val batteryTempC: Double?,
    val power: String,
)
