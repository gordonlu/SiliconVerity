package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class WorkloadRef(
    val weight: Double,
    val lowerIsBetter: Boolean = false,
)

@Serializable
data class CategorySpec(
    val weight: Double,
    val workloads: Map<String, WorkloadRef>,
)

@Serializable
data class ScorePack(
    val scoreVersion: String,
    val referencePackVersion: String,
    val overallWeights: Map<String, Double>,
    val categories: Map<String, CategorySpec>,
    val references: Map<String, Double>,
    val clampMin: Double = 0.20,
    val clampMax: Double = 5.00,
    val multiplier: Double = 10.0,
    val itemBase: Double = 1000.0,
    val sustainedBase: Double = 1000.0,
)

object ScorePackLoader {
    fun load(scoreVersion: String = "SVS-1.0"): ScorePack? {
        val stream = javaClass.getResourceAsStream("/scorepacks/$scoreVersion.json") ?: return null
        return runCatching {
            val json = stream.bufferedReader().use { it.readText() }
            JsonParser.decodeFromString<ScorePack>(json)
        }.getOrNull()
    }

    fun loadDefault(): ScorePack? = load()
}

private val JsonParser = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
