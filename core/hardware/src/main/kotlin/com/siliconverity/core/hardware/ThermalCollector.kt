package com.siliconverity.core.hardware

import android.content.Context
import android.os.PowerManager
import com.siliconverity.core.model.CapabilityStatus
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType

class ThermalCollector : HardwareCollector {
    override val key: String = "thermal"

    override fun collect(context: Context): List<CollectedFact> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return listOf(
                CollectedFact(
                    key = "thermal.status",
                    evidence = emptyList(),
                    warnings = listOf("PowerManager unavailable"),
                ),
            )

        val statusInt = pm.currentThermalStatus
        val statusFact = CollectedFact(
            key = "thermal.status",
            evidence = listOf(
                Evidence(
                    sourceType = SourceType.PUBLIC_API,
                    sourceId = "PowerManager.currentThermalStatus",
                    rawValue = ThermalStatusNames.name(statusInt),
                ),
            ),
        )

        val cap = CapabilityRegistry.thermalHeadroom(pm)
        val headroomEvidence = if (cap is CapabilityStatus.Supported) {
            val h = CapabilityRegistry.thermalHeadroomValue(pm)
            if (h != null) {
                listOf(Evidence(SourceType.PUBLIC_API, "PowerManager.getThermalHeadroom(1)", h.toString()))
            } else emptyList()
        } else emptyList()

        val headroomFact = CollectedFact(
            key = "thermal.headroom",
            evidence = headroomEvidence,
            capabilityStatus = cap,
            warnings = if (cap !is CapabilityStatus.Supported) listOf("thermal headroom: $cap") else emptyList(),
        )

        return listOf(statusFact, headroomFact)
    }
}
