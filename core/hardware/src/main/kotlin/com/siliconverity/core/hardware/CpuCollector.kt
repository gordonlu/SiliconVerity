package com.siliconverity.core.hardware

import android.content.Context
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType
import java.io.File

class CpuCollector : HardwareCollector {
    override val key: String = "cpu"

    override fun collect(context: Context): List<CollectedFact> {
        val facts = mutableListOf<CollectedFact>()

        val present = readCpuList("/sys/devices/system/cpu/present")
        facts += CollectedFact(
            key = "cpu.cores.configured",
            evidence = present?.let {
                listOf(Evidence(SourceType.SYSFS, "/sys/devices/system/cpu/present", it.raw, "count=${it.count}"))
            } ?: emptyList(),
            warnings = if (present == null) listOf("/sys/devices/system/cpu/present unreadable") else emptyList(),
        )

        val online = readCpuList("/sys/devices/system/cpu/online")
        facts += CollectedFact(
            key = "cpu.cores.online",
            evidence = online?.let {
                listOf(Evidence(SourceType.SYSFS, "/sys/devices/system/cpu/online", it.raw, "count=${it.count}"))
            } ?: emptyList(),
            warnings = if (online == null) listOf("/sys/devices/system/cpu/online unreadable") else emptyList(),
        )

        facts += CollectedFact(
            key = "cpu.cores.jvm_available",
            evidence = listOf(
                Evidence(
                    sourceType = SourceType.PUBLIC_API,
                    sourceId = "Runtime.availableProcessors",
                    rawValue = Runtime.getRuntime().availableProcessors().toString(),
                    note = "JVM available processors, not physical core count",
                ),
            ),
        )

        runCatching {
            val cpuinfo = File("/proc/cpuinfo").readText()
            val features = cpuinfo.lineSequence()
                .firstOrNull { it.startsWith("Features") }
                ?.substringAfter(':')?.trim()
            if (!features.isNullOrEmpty()) {
                facts += CollectedFact(
                    key = "cpu.features",
                    evidence = listOf(
                        Evidence(
                            sourceType = SourceType.PROCFS,
                            sourceId = "/proc/cpuinfo:Features",
                            rawValue = features,
                            note = "ARM hwcaps; supplement to NDK getauxval",
                        ),
                    ),
                )
            }
            val implementer = cpuinfo.lineSequence().firstOrNull { it.startsWith("CPU implementer") }?.substringAfter(':')?.trim()
            val part = cpuinfo.lineSequence().firstOrNull { it.startsWith("CPU part") }?.substringAfter(':')?.trim()
            if (!implementer.isNullOrEmpty() || !part.isNullOrEmpty()) {
                facts += CollectedFact(
                    key = "cpu.arm_core_id",
                    evidence = listOf(
                        Evidence(
                            sourceType = SourceType.PROCFS,
                            sourceId = "/proc/cpuinfo:CPU implementer/part",
                            rawValue = "implementer=$implementer part=$part",
                        ),
                    ),
                )
            }
        }

        return facts
    }

    private data class CpuList(val raw: String, val count: Int)

    private fun readCpuList(path: String): CpuList? = runCatching {
        val raw = File(path).readText().trim()
        var count = 0
        for (token in raw.split(",")) {
            val t = token.trim()
            if (t.isEmpty()) continue
            if (t.contains("-")) {
                val bounds = t.split("-")
                count += bounds[1].trim().toInt() - bounds[0].trim().toInt() + 1
            } else {
                count += 1
            }
        }
        CpuList(raw, count)
    }.getOrNull()
}
