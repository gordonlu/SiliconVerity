package com.siliconverity.nativecpu

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import java.time.Instant

class Fp32FmaWorkload : Workload {

    init {
        System.loadLibrary("sv_cpu_int")
    }

    private external fun nativeRunOnce(seed: Long): LongArray
    private external fun nativeCorrectnessCheck(): Boolean

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.fp32.fma",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_MICRO",
        measurementTarget = "FP32 FMA throughput (GFLOPS)",
        algorithm = "8 independent FP32 accumulators, a*x+y per iter, 25M iters (FMA via -ffp-contract=fast)",
        implementationBackend = "NDK C++20 (arm64-v8a)",
        dataSize = 25_000_000L,
        threadPolicy = "single thread, scheduler default",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC)",
        warmupMinMillis = 800,
        warmupMaxMillis = 4000,
        warmupConvergeThreshold = 0.03,
        measurementRepetitions = 7,
        correctnessCheck = "deterministic re-run equality",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("thermal throttling", "background load", "DVFS", "scheduler migration"),
    )

    private var seedCounter = 0x4321L

    override fun warmUp() {
        nativeRunOnce(nextSeed())
    }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed())
        return Sample(
            index = -1,
            workUnits = r[0],
            durationNanos = r[1],
            timestamp = Instant.now().toString(),
        )
    }

    override fun correctnessCheck(): Boolean = nativeCorrectnessCheck()

    private fun nextSeed(): Long {
        seedCounter = seedCounter * 6364136223846793005L + 1442695040888963407L
        return seedCounter
    }
}
