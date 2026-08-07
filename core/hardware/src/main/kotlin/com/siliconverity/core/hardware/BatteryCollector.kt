package com.siliconverity.core.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType

/**
 * 电池: 读取 sticky ACTION_BATTERY_CHANGED (EXTRA_TEMPERATURE/LEVEL/STATUS)。
 * 注意: API 34+ 已移除 BatteryManager.BATTERY_PROPERTY_TEMPERATURE, 故用广播。
 */
class BatteryCollector : HardwareCollector {
    override val key: String = "battery"

    override fun collect(context: Context): List<CollectedFact> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            return listOf(
                CollectedFact("battery.temperature", emptyList(), warnings = listOf("ACTION_BATTERY_CHANGED unavailable")),
            )
        }
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return listOf(
            CollectedFact(
                key = "battery.temperature",
                evidence = if (tempTenths != Int.MIN_VALUE) {
                    listOf(
                        Evidence(
                            SourceType.PUBLIC_API,
                            "ACTION_BATTERY_CHANGED EXTRA_TEMPERATURE",
                            "%.1f".format(tempTenths / 10.0),
                            "摄氏度 (十分之一度)",
                        ),
                    )
                } else emptyList(),
            ),
            CollectedFact(
                key = "battery.level",
                evidence = listOf(
                    Evidence(SourceType.PUBLIC_API, "ACTION_BATTERY_CHANGED EXTRA_LEVEL", level.toString(), "百分比"),
                ),
            ),
            CollectedFact(
                key = "battery.charging",
                evidence = listOf(
                    Evidence(SourceType.PUBLIC_API, "ACTION_BATTERY_CHANGED EXTRA_STATUS", charging.toString()),
                ),
            ),
        )
    }
}
