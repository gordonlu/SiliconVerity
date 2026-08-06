package com.siliconverity.core.storage

import com.siliconverity.core.benchmark.RunManifest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class RunManifestStore(runsDir: File) {

    private val dir: File = runsDir.apply { mkdirs() }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(manifest: RunManifest): File {
        val file = File(dir, "${manifest.runId}.json")
        file.writeText(json.encodeToString(manifest))
        return file
    }

    fun list(): List<RunManifest> =
        dir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { loadFile(it) }
            ?: emptyList()

    fun load(runId: String): RunManifest? =
        File(dir, "$runId.json").takeIf { it.exists() }?.let { loadFile(it) }

    fun clear() {
        dir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?.forEach { it.delete() }
    }

    private fun loadFile(file: File): RunManifest? = runCatching {
        json.decodeFromString<RunManifest>(file.readText())
    }.getOrNull()
}
