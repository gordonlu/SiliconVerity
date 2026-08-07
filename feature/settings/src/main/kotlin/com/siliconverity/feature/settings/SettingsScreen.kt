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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvThermalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val env by produceState(initialValue = EnvInfo("", false, "", "", -1)) {
        value = withContext(Dispatchers.Default) { readEnv(context) }
    }

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
        item { SectionTitle(stringResource(R.string.settings_about)) }
        item { Kv(stringResource(R.string.settings_app), "芯鉴 SiliconVerity") }
        item { Kv(stringResource(R.string.settings_version), env.appVersion) }
        item { Kv(stringResource(R.string.settings_license), "GPL-3.0-only") }
        item { Kv("applicationId", "com.siliconverity") }
        item { Kv(stringResource(R.string.settings_platform), "Android 16 / API 36") }

        item { Spacer(Modifier.height(SvSpacing.Md)); SectionTitle(stringResource(R.string.settings_env)) }
        item { Kv(stringResource(R.string.settings_power_save), if (env.powerSave) stringResource(R.string.settings_on) else stringResource(R.string.settings_off)) }
        item { Kv(stringResource(R.string.settings_charging), env.charging) }
        item { Kv(stringResource(R.string.settings_thermal), SvThermalStatus.short(env.thermal)) }
        item { Kv(stringResource(R.string.settings_battery), "${env.battery}%") }

        item { Spacer(Modifier.height(SvSpacing.Md)); SectionTitle(stringResource(R.string.settings_benchmark)) }
        item {
            Text(
                stringResource(R.string.settings_bench_desc_1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.settings_bench_desc_2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.settings_bench_desc_3),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.settings_bench_desc_4),
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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
        charging = if (bm?.isCharging == true) "充电中" else "未充电",
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
