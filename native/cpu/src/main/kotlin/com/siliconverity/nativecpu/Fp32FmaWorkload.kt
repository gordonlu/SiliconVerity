package com.siliconverity.nativecpu

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.ChecksumKind
import com.siliconverity.core.benchmark.CorrectnessResult
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import java.time.Instant

class Fp32FmaWorkload : Workload {

    init {
        System.loadLibrary("sv_cpu_int")
    }

    private external fun nativeRunOnce(seed: Long, iterations: Long): LongArray
    private external fun nativeCorrectnessCheck(): Boolean

    override val checksumKind: ChecksumKind get() = ChecksumKind.ULP

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.fp32.fma",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_MICRO",
        measurementTarget = "FP32 FMA throughput (GFLOPS, calibrated ~300ms)",
        algorithm = "8 independent FP32 accumulators, a*x+y per iter (FMA via -ffp-contract=fast)",
        implementationBackend = "NDK C++20 (arm64-v8a)",
        dataSize = 25_000_000L,
        threadPolicy = "single thread, scheduler default",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC)",
        warmupMinMillis = 800,
        warmupMaxMillis = 4000,
        warmupConvergeThreshold = 0.03,
        measurementRepetitions = 7,
        correctnessCheck = "deterministic re-run equality (incl. NaN/Inf)",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("thermal throttling", "background load", "DVFS", "scheduler migration"),
    )

    private companion object {
        const val DEFAULT_ITERATIONS = 25_000_000L
        const val MIN_ITERATIONS = 2_500_000L
        const val MAX_ITERATIONS = 2_500_000_000L
    }

    private var iterations = DEFAULT_ITERATIONS
    private var seedCounter = 0x4321L

    override fun calibrate(targetMillis: Long) {
        val probe = nativeRunOnce(nextSeed(), DEFAULT_ITERATIONS)
        val probeMs = probe[1] / 1_000_000.0
        if (probeMs > 0) {
            iterations = (DEFAULT_ITERATIONS.toDouble() * targetMillis / probeMs).toLong()
                .coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
        }
    }

    override fun warmUp() {
        nativeRunOnce(nextSeed(), iterations)
    }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed(), iterations)
        return Sample(
            index = -1,
            workUnits = r[0],
            durationNanos = r[1],
            timestamp = Instant.now().toString(),
        )
    }

    override fun correctnessCheck(): CorrectnessResult {
        val ok = nativeCorrectnessCheck()
        return CorrectnessResult(passed = ok, kind = checksumKind, finite = ok, reason = if (!ok) "checksum/determinism (incl. NaN/Inf)" else null)
    }

    private fun nextSeed(): Long {
        seedCounter = seedCounter * 6364136223846793005L + 1442695040888963407L
        return seedCounter
    }
}
