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

    private external fun nativeRunOnce(seed: Long, iterations: Long): LongArray
    private external fun nativeCorrectnessCheck(): Boolean

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.int.branch",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_MICRO",
        measurementTarget = "integer ALU with data-dependent branch throughput (iterations/s, calibrated ~300ms)",
        algorithm = "branch on acc parity (two ALU paths), iterations calibrated to target",
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

    private companion object {
        const val DEFAULT_ITERATIONS = 50_000_000L
        const val MIN_ITERATIONS = 5_000_000L
        const val MAX_ITERATIONS = 5_000_000_000L
    }

    private var iterations = DEFAULT_ITERATIONS
    private var seedCounter = 0x1357L

    override fun calibrate(targetMillis: Long) {
        val probe = nativeRunOnce(nextSeed(), DEFAULT_ITERATIONS)
        val probeMs = probe[1] / 1_000_000.0
        if (probeMs > 0) {
            iterations = (DEFAULT_ITERATIONS.toDouble() * targetMillis / probeMs).toLong()
                .coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
        }
    }

    override fun warmUp() { nativeRunOnce(nextSeed(), iterations) }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed(), iterations)
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

    private external fun nativeRunOnce(seed: Long, iterations: Long): LongArray
    private external fun nativeCorrectnessCheck(): Boolean

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.hash.cached",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_SCENARIO",
        measurementTarget = "cached serial rolling-hash rate (MB/s of input processed)",
        algorithm = "single dependency-chain rolling hash over repeatedly scanned 256KiB buffer (非缓存带宽, 是处理速率)",
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

    private companion object {
        const val DEFAULT_ITERATIONS = 200L
        const val MIN_ITERATIONS = 20L
        const val MAX_ITERATIONS = 20_000L
    }

    private var iterations = DEFAULT_ITERATIONS
    private var seedCounter = 0x2468L

    override fun calibrate(targetMillis: Long) {
        val probe = nativeRunOnce(nextSeed(), DEFAULT_ITERATIONS)
        val probeMs = probe[1] / 1_000_000.0
        if (probeMs > 0) {
            iterations = (DEFAULT_ITERATIONS.toDouble() * targetMillis / probeMs).toLong()
                .coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
        }
    }

    override fun warmUp() { nativeRunOnce(nextSeed(), iterations) }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed(), iterations)
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

    private external fun nativeRunOnce(seed: Long, itersPerThread: Long): LongArray
    private external fun nativeThreadCount(): Int
    private external fun nativeCorrectnessCheck(): Boolean

    val threadCount: Int by lazy { nativeThreadCount() }

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "cpu.multithread",
        workloadVersion = "0.1.0-alpha",
        category = "CPU_MICRO",
        measurementTarget = "multi-thread integer ALU total throughput (iterations/s, all online cores)",
        algorithm = "N=online cores threads, each iters calibrated to target, barrier sync",
        implementationBackend = "NDK C++20 std::thread + std::barrier (arm64-v8a)",
        dataSize = 8_000_000L,
        threadPolicy = "online cores (probed via sysconf)",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC) around barrier window",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "deterministic re-run equality",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("scheduler migration", "thermal", "background load", "DVFS"),
    )

    private companion object {
        const val DEFAULT_ITERS_PER_THREAD = 8_000_000L
        const val MIN_ITERS_PER_THREAD = 800_000L
        const val MAX_ITERS_PER_THREAD = 800_000_000L
    }

    private var itersPerThread = DEFAULT_ITERS_PER_THREAD
    private var seedCounter = 0x369CL

    override fun calibrate(targetMillis: Long) {
        val probe = nativeRunOnce(nextSeed(), DEFAULT_ITERS_PER_THREAD)
        val probeMs = probe[1] / 1_000_000.0
        if (probeMs > 0) {
            itersPerThread = (DEFAULT_ITERS_PER_THREAD.toDouble() * targetMillis / probeMs).toLong()
                .coerceIn(MIN_ITERS_PER_THREAD, MAX_ITERS_PER_THREAD)
        }
    }

    override fun warmUp() { nativeRunOnce(nextSeed(), itersPerThread) }

    override fun runOnce(): Sample {
        val r = nativeRunOnce(nextSeed(), itersPerThread)
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
