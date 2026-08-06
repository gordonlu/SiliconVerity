package com.siliconverity.benchmark

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.siliconverity.core.benchmark.BenchmarkEngine
import java.time.Instant
import java.util.UUID

class AndroidBenchmarkEnvironment(context: Context) : BenchmarkEngine.Environment {

    private val appContext = context.applicationContext
    private val pm: PowerManager? = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val bm: BatteryManager? = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    override val appVersion: String = "0.1.0-alpha"
    override val engineVersion: String = "0.1.0-alpha"
    override val abi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    override val androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    override val securityPatch: String = Build.VERSION.SECURITY_PATCH
    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    override val socReported: String = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()

    override val batteryLevel: Int
        get() = runCatching {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }.getOrDefault(-1)

    override val chargingState: String
        get() = runCatching { if (bm?.isCharging == true) "charging" else "not charging" }
            .getOrDefault("unknown")

    override val thermalStatusStart: String
        get() = pm?.currentThermalStatus?.let { com.siliconverity.core.hardware.ThermalStatusNames.name(it) } ?: "unknown"

    override fun thermalStatusEnd(): String =
        pm?.currentThermalStatus?.let { com.siliconverity.core.hardware.ThermalStatusNames.name(it) } ?: "unknown"

    override fun nowIso(): String = Instant.now().toString()

    override fun runId(): String = UUID.randomUUID().toString()
}
