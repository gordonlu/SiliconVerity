package com.siliconverity.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Evidence(
    val sourceType: SourceType,
    val sourceId: String,
    val rawValue: String?,
    val note: String? = null,
)
