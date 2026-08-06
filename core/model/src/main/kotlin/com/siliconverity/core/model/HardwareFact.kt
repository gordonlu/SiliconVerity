package com.siliconverity.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HardwareFact(
    val key: String,
    val rawValue: String?,
    val displayValue: String?,
    val sourceType: SourceType,
    val sourceId: String,
    val collectedAt: String,
    val confidence: Confidence,
    val isInferred: Boolean = false,
    val inferenceRuleVersion: String? = null,
    val apiLevelRequirement: Int? = null,
    val warnings: List<String> = emptyList(),
    val conflictingEvidence: List<Evidence> = emptyList(),
    val capabilityStatus: CapabilityStatus? = null,
) {
    companion object
}
