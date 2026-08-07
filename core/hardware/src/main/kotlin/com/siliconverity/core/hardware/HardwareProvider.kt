package com.siliconverity.core.hardware

import android.content.Context
import android.util.Log
import com.siliconverity.core.model.HardwareFact
import com.siliconverity.core.provenance.ProvenanceResolver
import java.time.Instant

class HardwareProvider(private val context: Context) {

    private val collectors: List<HardwareCollector> = listOf(
        DeviceCollector(),
        SocCollector(),
        CpuCollector(),
        MemoryCollector(),
        BatteryCollector(),
        GpuCollector(),
        StorageCollector(),
        ThermalCollector(),
    )
    private val TAG = "HardwareProvider"

    fun collectAll(nowIso: String = Instant.now().toString()): List<HardwareFact> =
        collectors.flatMap { collector ->
            runCatching { collector.collect(context) }
                .onFailure { Log.e(TAG, "collector ${collector.key} failed", it) }
                .getOrDefault(emptyList())
                .map { collected ->
                    ProvenanceResolver.resolve(
                        key = collected.key,
                        evidence = collected.evidence,
                        collectedAt = nowIso,
                        capabilityStatus = collected.capabilityStatus,
                        warnings = collected.warnings,
                    )
                }
        }

    fun collect(key: String, nowIso: String = Instant.now().toString()): HardwareFact? =
        collectAll(nowIso).firstOrNull { it.key == key }
}
