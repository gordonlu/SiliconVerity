package com.siliconverity.core.hardware

import android.content.Context
import com.siliconverity.core.model.HardwareFact
import com.siliconverity.core.provenance.ProvenanceResolver
import java.time.Instant

class HardwareProvider(private val context: Context) {

    private val collectors: List<HardwareCollector> = listOf(
        SocCollector(),
        CpuCollector(),
        MemoryCollector(),
        GpuCollector(),
        StorageCollector(),
        ThermalCollector(),
    )

    fun collectAll(nowIso: String = Instant.now().toString()): List<HardwareFact> =
        collectors.flatMap { collector ->
            collector.collect(context).map { collected ->
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
