package com.siliconverity.core.storage

import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.toBenchmarkRun
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 统一运行记录存储: bruns/ 存 BenchmarkRun (统一格式, 各管线写侧产出)。
 * 旧目录 runs/ + sustained/ + latency/ 读兼容 (适配器映射)。
 * 历史/导出/对比/版本追踪统一消费 BenchmarkRun。
 */
class BenchmarkRunStore(filesDir: File) {

    private val brunsDir = File(filesDir, "bruns")
    private val runStore = RunManifestStore(File(filesDir, "runs"))
    private val sustainedStore = SustainedResultStore(File(filesDir, "sustained"))
    private val latencyStore = MemoryLatencyResultStore(File(filesDir, "latency"))

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(run: BenchmarkRun): File {
        brunsDir.mkdirs()
        val file = File(brunsDir, "${run.identity.runId}.json")
        file.writeText(json.encodeToString(run))
        return file
    }

    fun list(): List<BenchmarkRun> {
        val bruns = readBruns()
        val old = runCatching { runStore.list().map { it.toBenchmarkRun() } }.getOrDefault(emptyList()) +
            runCatching { sustainedStore.list().map { it.toBenchmarkRun() } }.getOrDefault(emptyList()) +
            runCatching { latencyStore.list().map { it.toBenchmarkRun() } }.getOrDefault(emptyList())
        return (bruns + old).sortedByDescending { it.startedAt }
    }

    fun load(runId: String): BenchmarkRun? = list().firstOrNull { it.identity.runId == runId }

    fun clear() {
        brunsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }?.forEach { it.delete() }
        runCatching { runStore.clear() }
        runCatching { sustainedStore.clear() }
        runCatching { latencyStore.clear() }
    }

    private fun readBruns(): List<BenchmarkRun> =
        brunsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { runCatching { json.decodeFromString<BenchmarkRun>(it.readText()) }.getOrNull() }
            ?: emptyList()
}
