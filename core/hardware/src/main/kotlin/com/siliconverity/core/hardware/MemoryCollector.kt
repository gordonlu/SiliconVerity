package com.siliconverity.core.hardware

import android.app.ActivityManager
import android.content.Context
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType

class MemoryCollector : HardwareCollector {
    override val key: String = "memory"

    override fun collect(context: Context): List<CollectedFact> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return listOf(
                CollectedFact(
                    key = "memory.totalMem",
                    evidence = emptyList(),
                    warnings = listOf("ActivityManager unavailable"),
                ),
            )

        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        return listOf(
            CollectedFact(
                key = "memory.totalMem",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "ActivityManager.MemoryInfo.totalMem",
                        rawValue = mi.totalMem.toString(),
                        note = "kernel accessible total memory",
                    ),
                ),
            ),
            CollectedFact(
                key = "memory.availMem",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "ActivityManager.MemoryInfo.availMem",
                        rawValue = mi.availMem.toString(),
                        note = "system estimated available memory",
                    ),
                ),
            ),
            CollectedFact(
                key = "memory.threshold",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "ActivityManager.MemoryInfo.threshold",
                        rawValue = mi.threshold.toString(),
                    ),
                ),
            ),
            CollectedFact(
                key = "memory.lowMemory",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "ActivityManager.MemoryInfo.lowMemory",
                        rawValue = mi.lowMemory.toString(),
                    ),
                ),
            ),
        )
    }
}
