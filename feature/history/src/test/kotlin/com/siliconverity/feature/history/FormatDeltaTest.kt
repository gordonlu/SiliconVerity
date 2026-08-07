package com.siliconverity.feature.history

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDeltaTest {

    @Test
    fun nullSuffix_doesNotCrash() {
        assertEquals("+1.25", formatDelta(1.25, null))
        assertEquals("-0.50", formatDelta(-0.5, null))
    }

    @Test
    fun percentSuffix() {
        assertEquals("+10.00%", formatDelta(10.0, "%"))
        assertEquals("-3.33%", formatDelta(-3.33, "%"))
    }

    @Test
    fun zeroDelta() {
        assertEquals("+0.00%", formatDelta(0.0, "%"))
    }
}
