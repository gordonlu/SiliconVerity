package com.siliconverity.core.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    private fun pack(
        overallWeights: Map<String, Double>,
        categories: Map<String, CategorySpec>,
        references: Map<String, Double>,
    ) = ScorePack(
        scoreVersion = "SVS-1.0",
        referencePackVersion = "SV-REF-TEST",
        overallWeights = overallWeights,
        categories = categories,
        references = references,
        workloadVersions = references.keys.associateWith { "1.0.0" },
    )

    private fun run(id: String, median: Double, stability: ValidityLevel = ValidityLevel.STABLE, passed: Boolean = true): BenchmarkRun {
        val sample = Sample(0, median.toLong(), 1_000_000_000L, "t")
        return BenchmarkRun(
            identity = RunIdentity(runId = "r-$id", sessionId = "s", benchmarkProtocolVersion = "1.0", workloadId = id, workloadVersion = "1.0.0"),
            environment = EnvironmentSnapshot("", "", "", "", "", "", "", -1, "", false, "", ""),
            protocol = ProtocolSnapshot("1.0", 0.0, 0.0, 0.0, 7, 0.03, 0.07, 300, false),
            correctness = CorrectnessResult(passed = passed, kind = ChecksumKind.EXACT),
            validity = ValidityResult(valid = passed && stability != ValidityLevel.INVALID, scoreEligible = passed && (stability == ValidityLevel.STABLE || stability == ValidityLevel.VARIABLE), stability = stability, robustCv = 0.01),
            payload = BenchmarkPayload.Scalar(samples = listOf(sample), summary = Statistics.summarize(listOf(sample))),
            startedAt = "2026-08-07T00:00:00Z",
            endedAt = "2026-08-07T00:00:00Z",
            actualDurationNanos = 1_000_000_000L,
        )
    }

    private val cpuGpuPack = pack(
        overallWeights = mapOf("CPU" to 0.5, "GPU" to 0.5),
        categories = mapOf(
            "CPU" to CategorySpec(0.5, mapOf("cpu.a" to WorkloadRef(0.6), "cpu.b" to WorkloadRef(0.4))),
            "GPU" to CategorySpec(0.5, mapOf("gpu.x" to WorkloadRef(1.0))),
        ),
        references = mapOf("cpu.a" to 100.0, "cpu.b" to 200.0, "gpu.x" to 500.0),
    )

    @Test
    fun referenceDeviceScores10000() {
        val runs = listOf(run("cpu.a", 100.0), run("cpu.b", 200.0), run("gpu.x", 500.0))
        val report = ScoringEngine(cpuGpuPack).score(runs)
        assertEquals(1000, report.cpuScore)
        assertEquals(1000, report.gpuScore)
        assertEquals(10000, report.overallScore)
        assertEquals("HIGH", report.confidence.level)
    }

    @Test
    fun measured120PercentScales() {
        val runs = listOf(run("cpu.a", 120.0), run("cpu.b", 240.0), run("gpu.x", 600.0))
        val report = ScoringEngine(cpuGpuPack).score(runs)
        assertEquals(1200, report.cpuScore)
        assertEquals(1200, report.gpuScore)
        assertEquals(12000, report.overallScore)
    }

    @Test
    fun weightedGeoMean_shortBoardMatters() {
        // 短板: gpu.x 实测 250 (参考 500, 0.5x) -> gpuScore = 500, 拖低分类分
        val runs2 = listOf(run("cpu.a", 100.0), run("cpu.b", 200.0), run("gpu.x", 250.0))
        val report2 = ScoringEngine(cpuGpuPack).score(runs2)
        assertEquals(1000, report2.cpuScore)
        assertEquals(500, report2.gpuScore)
    }

    @Test
    fun retestBlocksOverall() {
        val runs = listOf(run("cpu.a", 100.0, ValidityLevel.STABLE), run("cpu.b", 200.0, ValidityLevel.RETEST_RECOMMENDED), run("gpu.x", 500.0))
        val report = ScoringEngine(cpuGpuPack).score(runs)
        assertNull("RETEST blocks overall", report.overallScore)
        assertTrue("retest workload ineligible", report.workloadScores.first { it.workloadId == "cpu.b" }.let { !it.eligible && it.exclusionReason == "RETEST_RECOMMENDED" })
    }

    @Test
    fun missingCategoryNullsOverall() {
        val runs = listOf(run("cpu.a", 100.0), run("cpu.b", 200.0))
        val report = ScoringEngine(cpuGpuPack).score(runs)
        assertNull("missing GPU -> overall null", report.overallScore)
        assertNotNull("cpuScore still present", report.cpuScore)
        assertNull(report.gpuScore)
    }

    @Test
    fun correctnessFailExcluded() {
        val runs = listOf(run("cpu.a", 100.0, passed = false), run("cpu.b", 200.0), run("gpu.x", 500.0))
        val report = ScoringEngine(cpuGpuPack).score(runs)
        assertTrue(report.workloadScores.first { it.workloadId == "cpu.a" }.let { !it.eligible && it.exclusionReason == "correctness failed" })
        assertNull("incomplete CPU category must not be scored", report.cpuScore)
        assertNull("incomplete category blocks overall", report.overallScore)
        assertEquals("LOW", report.confidence.level)
        assertTrue(report.exclusions.any { it.workloadId == "cpu.a" && it.reason == "correctness failed" })
    }

    @Test
    fun clampLimitsRatio() {
        val pack2 = pack(
            overallWeights = mapOf("CPU" to 1.0),
            categories = mapOf("CPU" to CategorySpec(1.0, mapOf("cpu.a" to WorkloadRef(1.0)))),
            references = mapOf("cpu.a" to 100.0),
        )
        // 实测 10000x 参考 -> ratio clamp 5.0 -> normalized 5000
        val runs = listOf(run("cpu.a", 1_000_000.0))
        val report = ScoringEngine(pack2).score(runs)
        assertEquals(5000, report.cpuScore)
        assertEquals(5000.0, report.workloadScores.first().normalizedScore, 0.001)
    }

    @Test
    fun workloadVersionMismatchIsExcluded() {
        val mismatched = run("cpu.a", 100.0).copy(
            identity = run("cpu.a", 100.0).identity.copy(workloadVersion = "2.0.0"),
        )
        val report = ScoringEngine(cpuGpuPack).score(
            listOf(mismatched, run("cpu.b", 200.0), run("gpu.x", 500.0)),
        )
        assertNull(report.cpuScore)
        assertNull(report.overallScore)
        assertTrue(report.exclusions.any { it.workloadId == "cpu.a" && it.reason.contains("version mismatch") })
    }

    @Test
    fun lowerIsBetterUsesInverseRatio() {
        val latencyPack = pack(
            overallWeights = mapOf("CPU" to 1.0),
            categories = mapOf("CPU" to CategorySpec(1.0, mapOf("cpu.latency" to WorkloadRef(1.0, lowerIsBetter = true)))),
            references = mapOf("cpu.latency" to 100.0),
        )
        val report = ScoringEngine(latencyPack).score(listOf(run("cpu.latency", 50.0)))
        assertEquals(2000, report.cpuScore)
    }
}
