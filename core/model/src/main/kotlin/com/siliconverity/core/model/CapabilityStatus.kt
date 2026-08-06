package com.siliconverity.core.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface CapabilityStatus {
    @Serializable
    data object Supported : CapabilityStatus

    @Serializable
    data object Unsupported : CapabilityStatus

    @Serializable
    data object TemporarilyUnavailable : CapabilityStatus

    @Serializable
    data class Invalid(val reason: String) : CapabilityStatus
}
