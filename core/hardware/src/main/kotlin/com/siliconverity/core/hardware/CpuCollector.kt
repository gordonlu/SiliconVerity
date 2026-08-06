package com.siliconverity.core.hardware

import android.content.Context
import android.os.Build
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType
import com.siliconverity.nativecpu.CpuFeatures
import java.io.File

class CpuCollector : HardwareCollector {
    override val key: String = "cpu"

    override fun collect(context: Context): List<CollectedFact> {
        val facts = mutableListOf<CollectedFact>()

        facts += coresFact("/sys/devices/system/cpu/present", "cpu.cores.configured")
        facts += coresFact("/sys/devices/system/cpu/online", "cpu.cores.online")

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
            val cf = CpuFeatures()
            facts += CollectedFact(
                key = "cpu.features",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.NDK_API,
                        sourceId = "getauxval(AT_HWCAP/AT_HWCAP2)",
                        rawValue = cf.features,
                        note = "decoded ARM hwcaps, authoritative",
                    ),
                ),
            )
            facts += CollectedFact(
                key = "cpu.hwcap",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.NDK_API,
                        sourceId = "getauxval raw",
                        rawValue = cf.hwcapHex(),
                    ),
                ),
            )
        }.onFailure {
            facts += CollectedFact(
                key = "cpu.features",
                evidence = emptyList(),
                warnings = listOf("getauxval unavailable: ${it.message}"),
            )
        }

        runCatching {
            val cpuinfo = File("/proc/cpuinfo").readText()
            val features = cpuinfo.lineSequence()
                .firstOrNull { it.startsWith("Features") }
                ?.substringAfter(':')?.trim()
            if (!features.isNullOrEmpty()) {
                facts += CollectedFact(
                    key = "cpu.features_procfs",
                    evidence = listOf(
                        Evidence(
                            sourceType = SourceType.PROCFS,
                            sourceId = "/proc/cpuinfo:Features",
                            rawValue = features,
                            note = "supplement to NDK getauxval",
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

    private fun coresFact(path: String, key: String): CollectedFact {
        val list = readCpuList(path)
        return CollectedFact(
            key = key,
            evidence = list?.let {
                listOf(Evidence(SourceType.SYSFS, path, it.count.toString(), "range=${it.raw}"))
            } ?: emptyList(),
            warnings = if (list == null) listOf("$path unreadable") else emptyList(),
        )
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
