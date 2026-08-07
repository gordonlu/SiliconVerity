package com.siliconverity.nativecpu

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.CorrectnessResult
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import java.time.Instant

class CpuIntegerWorkload : Workload {

    init {
        System.loadLibrary("sv_cpu_int")
    }

    private external fun nativeProbe(): String
    private external fun nativeRunOnce(seed: Long): LongArray
    private external fun nativeCorrectnessCheck(): Boolean

    val probe: String get() = nativeProbe()

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.int.alu",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_MICRO",
        measurementTarget = "integer ALU throughput (ops/s)",
        algorithm = "hash-like mix of mul/shift/xor over 50M iterations",
        implementationBackend = "NDK C++20 (arm64-v8a)",
        dataSize = 50_000_000L,
        threadPolicy = "single thread, scheduler default",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC)",
        warmupMinMillis = 800,
        warmupMaxMillis = 4000,
        warmupConvergeThreshold = 0.03,
        measurementRepetitions = 7,
        correctnessCheck = "deterministic re-run equality",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("thermal throttling", "background load", "scheduler migration"),
    )

    private var seedCounter = 0x1234L

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

    override fun correctnessCheck(): CorrectnessResult {
        val ok = nativeCorrectnessCheck()
        return CorrectnessResult(passed = ok, kind = checksumKind, finite = ok, reason = if (!ok) "checksum/determinism" else null)
    }

    private fun nextSeed(): Long {
        seedCounter = seedCounter * 6364136223846793005L + 1442695040888963407L
        return seedCounter
    }
}
