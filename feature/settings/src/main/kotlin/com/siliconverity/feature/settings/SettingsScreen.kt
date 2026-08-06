package com.siliconverity.feature.settings

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.designsystem.SvSpacing

private data class EnvInfo(
    val appVersion: String,
    val powerSave: Boolean,
    val charging: String,
    val thermal: String,
    val battery: Int,
)

@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val env = remember { readEnv(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(
            start = SvSpacing.PageHorizontal,
            end = SvSpacing.PageHorizontal,
            top = SvSpacing.Md,
            bottom = SvSpacing.Md,
        ),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
    ) {
        item { SectionTitle("ABOUT") }
        item { Kv("应用", "芯鉴 SiliconVerity") }
        item { Kv("版本", env.appVersion) }
        item { Kv("许可证", "GPL-3.0-only") }
        item { Kv("applicationId", "com.siliconverity") }
        item { Kv("平台基线", "Android 16 / API 36") }

        item { Spacer(Modifier.height(SvSpacing.Md)); SectionTitle("ENVIRONMENT DIAGNOSTICS") }
        item { Kv("省电模式", if (env.powerSave) "ON" else "OFF") }
        item { Kv("充电状态", env.charging) }
        item { Kv("热状态", env.thermal) }
        item { Kv("电量", "${env.battery}%") }

        item { Spacer(Modifier.height(SvSpacing.Md)); SectionTitle("BENCHMARK") }
        item {
            Text(
                "Alpha 阶段不提供正式综合分。原始指标、统计与有效性见历史与运行详情。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                "每项测试 7 轮测量, 取 median, 辅以 MAD/CV。CV ≤ 3% 为 STABLE, > 7% 标 RETEST_RECOMMENDED。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Kv(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun readEnv(context: Context): EnvInfo {
    val appContext = context.applicationContext
    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val version = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrDefault("?")
    return EnvInfo(
        appVersion = version ?: "?",
        powerSave = pm?.isPowerSaveMode == true,
        charging = if (bm?.isCharging == true) "charging" else "not charging",
        thermal = pm?.currentThermalStatus?.let(::thermalName) ?: "unknown",
        battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
    )
}

private fun thermalName(status: Int): String = when (status) {
    0 -> "NONE"
    1 -> "LIGHT"
    2 -> "MODERATE"
    3 -> "SEVERE"
    4 -> "CRITICAL"
    5 -> "EMERGENCY"
    6 -> "SHUTDOWN"
    else -> "UNKNOWN($status)"
}
