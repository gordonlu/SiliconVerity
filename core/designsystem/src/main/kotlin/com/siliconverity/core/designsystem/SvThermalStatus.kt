package com.siliconverity.core.designsystem

/**
 * Thermal Status 的 UI 文案映射。
 * 原始枚举 (NONE/LIGHT/...) 用于存储与调试, 不作为 UI 文案。
 */
object SvThermalStatus {

    fun short(status: String): String = when (status) {
        "NONE" -> "NORMAL"
        "LIGHT" -> "LIGHT"
        "MODERATE" -> "MODERATE"
        "SEVERE" -> "SEVERE"
        "CRITICAL" -> "CRITICAL"
        "EMERGENCY" -> "EMERGENCY"
        "SHUTDOWN" -> "SHUTDOWN"
        else -> "UNKNOWN"
    }

    fun detail(status: String): String = when (status) {
        "NONE" -> "正常，无明显热压力"
        "LIGHT" -> "轻微热压力"
        "MODERATE" -> "中度热压力"
        "SEVERE" -> "严重热压力"
        "CRITICAL" -> "临界热状态"
        "EMERGENCY" -> "紧急热状态"
        "SHUTDOWN" -> "触发关机保护"
        else -> "无法识别"
    }
}
