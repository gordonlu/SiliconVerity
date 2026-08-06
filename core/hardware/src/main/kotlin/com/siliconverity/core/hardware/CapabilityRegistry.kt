package com.siliconverity.core.hardware

import android.os.PowerManager
import com.siliconverity.core.model.CapabilityStatus

object CapabilityRegistry {

    fun thermalHeadroom(pm: PowerManager, forecastSeconds: Int = 1): CapabilityStatus {
        return runCatching {
            val h = pm.getThermalHeadroom(forecastSeconds)
            when {
                h.isNaN() -> CapabilityStatus.TemporarilyUnavailable
                else -> CapabilityStatus.Supported
            }
        }.getOrElse {
            CapabilityStatus.Invalid(it.message ?: "thermal headroom unavailable")
        }
    }

    fun thermalHeadroomValue(pm: PowerManager, forecastSeconds: Int = 1): Float? {
        return runCatching { pm.getThermalHeadroom(forecastSeconds) }
            .getOrNull()
            ?.takeUnless { it.isNaN() }
    }
}
