package com.siliconverity.core.hardware

import android.content.Context
import android.os.Build
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType
import java.io.File

class SocCollector : HardwareCollector {
    override val key: String = "soc"

    override fun collect(context: Context): List<CollectedFact> {
        val facts = mutableListOf<CollectedFact>()

        facts += CollectedFact(
            key = "soc.manufacturer",
            evidence = listOf(
                Evidence(SourceType.PUBLIC_API, "Build.SOC_MANUFACTURER", Build.SOC_MANUFACTURER),
            ),
        )

        val modelEvidence = mutableListOf<Evidence>()
        modelEvidence += Evidence(SourceType.PUBLIC_API, "Build.SOC_MODEL", Build.SOC_MODEL)
        runCatching {
            val cpuinfo = File("/proc/cpuinfo").readText()
            cpuinfo.lineSequence().firstOrNull { it.startsWith("Hardware") }?.let { line ->
                modelEvidence += Evidence(
                    sourceType = SourceType.PROCFS,
                    sourceId = "/proc/cpuinfo:Hardware",
                    rawValue = line.substringAfter(':').trim(),
                )
            }
        }
        facts += CollectedFact(key = "soc.model", evidence = modelEvidence)

        facts += CollectedFact(
            key = "soc.platform",
            evidence = listOf(
                Evidence(SourceType.SYSTEM_PROPERTY, "ro.board.platform (Build.BOARD)", Build.BOARD),
            ),
        )

        facts += CollectedFact(
            key = "soc.hardware_id",
            evidence = listOf(
                Evidence(SourceType.SYSTEM_PROPERTY, "ro.hardware (Build.HARDWARE)", Build.HARDWARE),
            ),
        )

        return facts
    }
}
