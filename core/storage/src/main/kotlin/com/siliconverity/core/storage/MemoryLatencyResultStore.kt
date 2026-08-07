package com.siliconverity.core.storage

import com.siliconverity.core.benchmark.MemoryLatencyResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MemoryLatencyResultStore(runsDir: File) {

    private val dir: File = runsDir.apply { mkdirs() }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(result: MemoryLatencyResult): File {
        val file = File(dir, "latency_${result.runId}.json")
        file.writeText(json.encodeToString(result))
        return file
    }

    fun latest(): MemoryLatencyResult? =
        dir.listFiles { f -> f.isFile && f.name.startsWith("latency_") && f.extension.equals("json", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?.let { loadFile(it) }

    private fun loadFile(file: File): MemoryLatencyResult? = runCatching {
        json.decodeFromString<MemoryLatencyResult>(file.readText())
    }.getOrNull()
}
