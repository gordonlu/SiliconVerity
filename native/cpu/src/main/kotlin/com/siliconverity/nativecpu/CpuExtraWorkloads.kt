package com.siliconverity.nativecpu

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.ChecksumKind
import com.siliconverity.core.benchmark.CorrectnessResult
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import java.time.Instant

class IntBranchWorkload : Workload {

    init {
        System.loadLibrary("sv_cpu_int")
    }

    private external fun nativeRunOnce(seed: Long): LongArray
    private external fun nativeCorrectnessCheck(): Boolean

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.int.branch",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_MICRO",
        measurementTarget = "integer ALU with data-dependent branch throughput (ops/s)",
        algorithm = "50M iter, branch on acc parity (two ALU paths)",
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
        knownInterferences = listOf("branch mispredict", "thermal", "background load"),
    )

    private var seedCounter = 0x1357L

    override fun warmUp() { nativeRunOnce(nextSeed()) }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed())
        return Sample(index = -1, workUnits = r[0], durationNanos = r[1], timestamp = Instant.now().toString())
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

class CompressionWorkload : Workload {

    init {
        System.loadLibrary("sv_cpu_int")
    }

    private external fun nativeRunOnce(seed: Long): LongArray
    private external fun nativeCorrectnessCheck(): Boolean

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.compress.mixed",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_SCENARIO",
        measurementTarget = "mixed int+memory throughput (MB/s, rolling hash over 256KB)",
        algorithm = "200 passes of rolling hash over 256KB buffer (int + cache mixed)",
        implementationBackend = "NDK C++20 (arm64-v8a)",
        dataSize = 200L * 256 * 1024,
        threadPolicy = "single thread, scheduler default",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC)",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "deterministic re-run equality",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("cache pollution", "background load"),
    )

    private var seedCounter = 0x2468L

    override fun warmUp() { nativeRunOnce(nextSeed()) }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed())
        return Sample(index = -1, workUnits = r[0], durationNanos = r[1], timestamp = Instant.now().toString())
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

class MultithreadWorkload : Workload {

    init {
        System.loadLibrary("sv_cpu_int")
    }

    private external fun nativeRunOnce(seed: Long): LongArray
    private external fun nativeThreadCount(): Int
    private external fun nativeCorrectnessCheck(): Boolean

    val threadCount: Int by lazy { nativeThreadCount() }

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.multithread",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_MICRO",
        measurementTarget = "multi-thread integer ALU total throughput (ops/s, all online cores)",
        algorithm = "N=online cores threads, each 8M iter int mix, join + sum",
        implementationBackend = "NDK C++20 std::thread (arm64-v8a)",
        dataSize = 8_000_000L,
        threadPolicy = "online cores (probed via sysconf)",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC) around spawn+join",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "deterministic re-run equality",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("scheduler migration", "thermal", "background load", "DVFS"),
    )

    private var seedCounter = 0x369CL

    override fun warmUp() { nativeRunOnce(nextSeed()) }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed())
        return Sample(index = -1, workUnits = r[0], durationNanos = r[1], timestamp = Instant.now().toString())
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
