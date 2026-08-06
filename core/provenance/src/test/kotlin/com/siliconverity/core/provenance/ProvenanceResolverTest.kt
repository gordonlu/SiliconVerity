package com.siliconverity.core.provenance

import com.siliconverity.core.model.CapabilityStatus
import com.siliconverity.core.model.Confidence
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvenanceResolverTest {

    private fun ev(type: SourceType, value: String?, id: String = "src") = Evidence(type, id, value)

    @Test
    fun public_api_is_high() {
        val f = ProvenanceResolver.resolve("k", listOf(ev(SourceType.PUBLIC_API, "x")), "now")
        assertEquals(Confidence.HIGH, f.confidence)
        assertEquals("x", f.rawValue)
        assertEquals("x", f.displayValue)
    }

    @Test
    fun system_property_is_medium() {
        val f = ProvenanceResolver.resolve("k", listOf(ev(SourceType.SYSTEM_PROPERTY, "x")), "now")
        assertEquals(Confidence.MEDIUM, f.confidence)
    }

    @Test
    fun procfs_is_medium() {
        val f = ProvenanceResolver.resolve("k", listOf(ev(SourceType.PROCFS, "x")), "now")
        assertEquals(Confidence.MEDIUM, f.confidence)
    }

    @Test
    fun database_mapping_is_low() {
        val f = ProvenanceResolver.resolve("k", listOf(ev(SourceType.DATABASE_MAPPING, "x")), "now")
        assertEquals(Confidence.LOW, f.confidence)
    }

    @Test
    fun conflicted_when_same_field_values_differ() {
        val f = ProvenanceResolver.resolve(
            "soc.model",
            listOf(
                ev(SourceType.PUBLIC_API, "MT6991", "Build.SOC_MODEL"),
                ev(SourceType.PROCFS, "MT6991-AB", "/proc/cpuinfo:Hardware"),
            ),
            "now",
        )
        assertEquals(Confidence.CONFLICTED, f.confidence)
        assertEquals(1, f.conflictingEvidence.size)
    }

    @Test
    fun not_conflicted_when_same_field_values_agree() {
        val f = ProvenanceResolver.resolve(
            "soc.model",
            listOf(
                ev(SourceType.PUBLIC_API, "MT6991", "Build.SOC_MODEL"),
                ev(SourceType.PROCFS, "MT6991", "/proc/cpuinfo:Hardware"),
            ),
            "now",
        )
        assertNotEquals(Confidence.CONFLICTED, f.confidence)
        assertEquals(Confidence.HIGH, f.confidence)
        assertTrue(f.conflictingEvidence.isEmpty())
    }

    @Test
    fun empty_evidence_is_unknown() {
        val f = ProvenanceResolver.resolve("k", emptyList(), "now")
        assertEquals(Confidence.UNKNOWN, f.confidence)
        assertNull(f.rawValue)
    }

    @Test
    fun capability_invalid_lowers_to_low() {
        val f = ProvenanceResolver.resolve(
            "thermal.headroom",
            listOf(ev(SourceType.PUBLIC_API, "1.2")),
            "now",
            capabilityStatus = CapabilityStatus.Invalid("err"),
        )
        assertEquals(Confidence.LOW, f.confidence)
    }

    @Test
    fun primary_picks_highest_priority_source() {
        val f = ProvenanceResolver.resolve(
            "k",
            listOf(
                ev(SourceType.SYSTEM_PROPERTY, "v", "ro.x"),
                ev(SourceType.PUBLIC_API, "v", "Build.X"),
            ),
            "now",
        )
        assertEquals(SourceType.PUBLIC_API, f.sourceType)
        assertEquals("Build.X", f.sourceId)
        assertEquals(Confidence.HIGH, f.confidence)
    }
}
