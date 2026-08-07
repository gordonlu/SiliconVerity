package com.siliconverity.core.storage

import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.toBenchmarkRun
import java.io.File

/**
 * 统一运行记录聚合: runs/(CPU/Mem/Storage/GPU) + sustained/ + latency/ -> BenchmarkRun。
 * 历史/对比/导出的统一读侧; 各管线仍产出各自类型, 经适配器映射。
 */
class BenchmarkRunStore(
    runsDir: File,
    sustainedDir: File,
    latencyDir: File,
) {
    private val runStore = RunManifestStore(runsDir)
    private val sustainedStore = SustainedResultStore(sustainedDir)
    private val latencyStore = MemoryLatencyResultStore(latencyDir)

    fun list(): List<BenchmarkRun> {
        val runs = runCatching { runStore.list().map { it.toBenchmarkRun() } }.getOrDefault(emptyList())
        val sustained = runCatching { sustainedStore.list().map { it.toBenchmarkRun() } }.getOrDefault(emptyList())
        val latency = runCatching { latencyStore.list().map { it.toBenchmarkRun() } }.getOrDefault(emptyList())
        return (runs + sustained + latency).sortedByDescending { it.startedAt }
    }

    fun load(runId: String): BenchmarkRun? = list().firstOrNull { it.identity.runId == runId }

    fun clear() {
        runCatching { runStore.clear() }
        runCatching { sustainedStore.clear() }
        runCatching { latencyStore.clear() }
    }
}
