package com.siliconverity.core.hardware

import android.content.Context
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType
import java.io.File

/**
 * CPU 占用率: /proc/stat 双采样 (间隔 delayMs), 计算忙/总 delta。
 * 瞬时采样, PROCFS, MEDIUM。
 */
class CpuUsageCollector(
    private val delayMs: Long = 500,
) : HardwareCollector {
    override val key: String = "cpu.usage"

    override fun collect(context: Context): List<CollectedFact> {
        val t1 = readCpuTicks()
        Thread.sleep(delayMs)
        val t2 = readCpuTicks()
        if (t1 == null || t2 == null) {
            return listOf(CollectedFact(key = key, evidence = emptyList(), warnings = listOf("/proc/stat unreadable")))
        }
        val idleDelta = t2.idle - t1.idle
        val totalDelta = t2.total - t1.total
        val usage = if (totalDelta > 0) (totalDelta - idleDelta) * 100.0 / totalDelta else null
        if (usage == null) {
            return listOf(CollectedFact(key = key, evidence = emptyList(), warnings = listOf("cpu stat delta zero")))
        }
        return listOf(
            CollectedFact(
                key = key,
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PROCFS,
                        sourceId = "/proc/stat cpu (two-sample ${delayMs}ms)",
                        rawValue = "%.1f".format(usage),
                        note = "busy/total delta, 瞬时采样",
                    ),
                ),
            ),
        )
    }

    private data class CpuTicks(val idle: Long, val total: Long)

    private fun readCpuTicks(): CpuTicks? {
        val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return null
        val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 8) return null
        val idle = parts[3] + parts[4]
        val total = parts.take(8).sum()
        return CpuTicks(idle, total)
    }
}
