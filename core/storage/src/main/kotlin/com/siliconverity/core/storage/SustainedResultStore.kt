package com.siliconverity.core.storage

import com.siliconverity.core.benchmark.SustainedResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SustainedResultStore(runsDir: File) {

    private val dir: File = runsDir.apply { mkdirs() }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(result: SustainedResult): File {
        val file = File(dir, "sustained_${result.runId}.json")
        file.writeText(json.encodeToString(result))
        return file
    }

    fun list(): List<SustainedResult> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("sustained_") && f.extension.equals("json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { loadFile(it) }
            ?: emptyList()

    private fun loadFile(file: File): SustainedResult? = runCatching {
        json.decodeFromString<SustainedResult>(file.readText())
    }.getOrNull()
}
