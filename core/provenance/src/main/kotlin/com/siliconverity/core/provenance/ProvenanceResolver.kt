package com.siliconverity.core.provenance

import com.siliconverity.core.model.CapabilityStatus
import com.siliconverity.core.model.Confidence
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.HardwareFact
import com.siliconverity.core.model.SourceType

object ProvenanceResolver {

    private val sourcePriority: List<SourceType> = listOf(
        SourceType.PUBLIC_API,
        SourceType.NDK_API,
        SourceType.PROCFS,
        SourceType.DRIVER_REPORTED,
        SourceType.SYSFS,
        SourceType.SYSTEM_PROPERTY,
        SourceType.DATABASE_MAPPING,
        SourceType.ALGORITHM_INFERENCE,
        SourceType.USER_PROVIDED,
    )

    fun rank(type: SourceType): Int = sourcePriority.indexOf(type).let { if (it < 0) Int.MAX_VALUE else it }

    fun resolve(
        key: String,
        evidence: List<Evidence>,
        collectedAt: String,
        capabilityStatus: CapabilityStatus? = null,
        warnings: List<String> = emptyList(),
    ): HardwareFact {
        if (evidence.isEmpty()) {
            return HardwareFact(
                key = key,
                rawValue = null,
                displayValue = null,
                sourceType = SourceType.PUBLIC_API,
                sourceId = "none",
                collectedAt = collectedAt,
                confidence = Confidence.UNKNOWN,
                warnings = warnings + "no evidence collected",
                capabilityStatus = capabilityStatus,
            )
        }

        val primary = evidence.minByOrNull { rank(it.sourceType) }
            ?: evidence.first()

        val distinctValues = evidence.mapNotNull { it.rawValue }.distinct()
        val conflicted = distinctValues.size > 1

        val confidence = when {
            capabilityStatus is CapabilityStatus.Invalid -> Confidence.LOW
            conflicted -> Confidence.CONFLICTED
            primary.sourceType == SourceType.PUBLIC_API ||
                primary.sourceType == SourceType.NDK_API -> Confidence.HIGH
            primary.sourceType == SourceType.PROCFS ||
                primary.sourceType == SourceType.DRIVER_REPORTED ||
                primary.sourceType == SourceType.SYSFS ||
                primary.sourceType == SourceType.SYSTEM_PROPERTY -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        val conflicts = if (conflicted) evidence.filter { it.rawValue != primary.rawValue } else emptyList()

        return HardwareFact(
            key = key,
            rawValue = primary.rawValue,
            displayValue = primary.rawValue,
            sourceType = primary.sourceType,
            sourceId = primary.sourceId,
            collectedAt = collectedAt,
            confidence = confidence,
            warnings = warnings,
            conflictingEvidence = conflicts,
            capabilityStatus = capabilityStatus,
        )
    }
}
