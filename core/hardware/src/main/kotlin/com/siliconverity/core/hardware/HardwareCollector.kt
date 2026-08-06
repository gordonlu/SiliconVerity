package com.siliconverity.core.hardware

import android.content.Context

interface HardwareCollector {
    val key: String
    fun collect(context: Context): List<CollectedFact>
}
