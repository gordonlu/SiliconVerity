package com.siliconverity.core.benchmark

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SustainedRunnerTest {

    @Test
    fun calibratesBuildsRealWindowsAndPropagatesCorrectness() = runTest {
        var now = 0L
        val workload = object : Workload {
            var calibratedTo = 0L
            var verified = false

            override val spec = BenchmarkSpec(
                workloadId = "cpu.test",
                workloadVersion = "1.0.0",
                category = "CPU",
                measurementTarget = "test",
                algorithm = "test",
                implementationBackend = "test",
                dataSize = 1,
                threadPolicy = "single",
                timingMethod = "fake",
                warmupMinMillis = 0,
                warmupMaxMillis = 0,
                warmupConvergeThreshold = 0.0,
                measurementRepetitions = 1,
                correctnessCheck = "fake",
            )

            override fun calibrate(targetMillis: Long) { calibratedTo = targetMillis }
            override fun warmUp() = Unit
            override fun runOnce(): Sample {
                now += 100_000_000L
                return Sample(0, 100L, 100_000_000L, "t")
            }
            override fun correctnessCheck(): CorrectnessResult {
                verified = true
                return CorrectnessResult(false, ChecksumKind.EXACT, reason = "expected failure")
            }
        }
        val result = SustainedRunner(workload, FakeEnvironment) { now }
            .run(durationSec = 2, windowSec = 1) {}

        assertEquals(100L, workload.calibratedTo)
        assertTrue(workload.verified)
        assertFalse(result.correctness.passed)
        assertEquals(2_000_000_000L, result.actualDurationNanos)
        assertEquals(2, result.samples.size)
        assertTrue(result.samples.all { it.throughput == 1000.0 })
    }

    private object FakeEnvironment : BenchmarkEngine.Environment {
        override val appVersion = "test"
        override val engineVersion = "test"
        override val abi = "test"
        override val androidVersion = "test"
        override val securityPatch = "test"
        override val deviceModel = "test"
        override val socReported = "test"
        override val batteryLevel = 100
        override val chargingState = "battery"
        override val powerSaveMode = false
        override val thermalStatusStart = "NONE"
        override fun thermalStatusEnd() = "NONE"
        override fun nowIso() = "2026-08-10T00:00:00Z"
        override fun runId() = "run"
    }
}
