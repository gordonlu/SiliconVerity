package com.siliconverity.core.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class ThermalSample(val elapsedSec: Double, val status: String)
