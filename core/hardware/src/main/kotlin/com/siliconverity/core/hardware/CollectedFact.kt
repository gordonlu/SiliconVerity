package com.siliconverity.core.hardware

import com.siliconverity.core.model.CapabilityStatus
import com.siliconverity.core.model.Evidence

data class CollectedFact(
    val key: String,
    val evidence: List<Evidence>,
    val capabilityStatus: CapabilityStatus? = null,
    val warnings: List<String> = emptyList(),
)
