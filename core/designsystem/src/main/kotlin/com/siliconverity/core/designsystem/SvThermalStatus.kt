package com.siliconverity.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Thermal Status 的 UI 文案映射 (本地化)。
 * 原始枚举 (NONE/LIGHT/...) 用于存储与调试, 不作为 UI 文案。
 */
object SvThermalStatus {

    @Composable
    fun short(status: String): String = when (status) {
        "NONE" -> stringResource(R.string.sv_thermal_normal)
        "LIGHT" -> stringResource(R.string.sv_thermal_light)
        "MODERATE" -> stringResource(R.string.sv_thermal_moderate)
        "SEVERE" -> stringResource(R.string.sv_thermal_severe)
        "CRITICAL" -> stringResource(R.string.sv_thermal_critical)
        "EMERGENCY" -> stringResource(R.string.sv_thermal_emergency)
        "SHUTDOWN" -> stringResource(R.string.sv_thermal_shutdown)
        else -> stringResource(R.string.sv_thermal_unknown)
    }

    @Composable
    fun detail(status: String): String = when (status) {
        "NONE" -> stringResource(R.string.sv_thermal_normal_detail)
        "LIGHT" -> stringResource(R.string.sv_thermal_light_detail)
        "MODERATE" -> stringResource(R.string.sv_thermal_moderate_detail)
        "SEVERE" -> stringResource(R.string.sv_thermal_severe_detail)
        "CRITICAL" -> stringResource(R.string.sv_thermal_critical_detail)
        "EMERGENCY" -> stringResource(R.string.sv_thermal_emergency_detail)
        "SHUTDOWN" -> stringResource(R.string.sv_thermal_shutdown_detail)
        else -> stringResource(R.string.sv_thermal_unknown_detail)
    }
}
