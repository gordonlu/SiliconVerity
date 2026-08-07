package com.siliconverity.nativememory

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.CorrectnessResult
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import java.time.Instant

object MemoryNative {
    init {
        System.loadLibrary("sv_mem")
    }

    external fun nativeRead(sizeBytes: Long, seed: Long): LongArray
    external fun nativeCopy(sizeBytes: Long, seed: Long): LongArray
    external fun nativeCorrectness(): Boolean
}

class MemoryReadWorkload(
    private val sizeBytes: Long = 64L * 1024 * 1024,
) : Workload {

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "mem.bandwidth.read",
        workloadVersion = "0.1.0-alpha",
        category = "MEMORY",
        measurementTarget = "sequential read bandwidth (GB/s)",
        algorithm = "64MB buffer, 8-accumulator uint64 reduction (ILP), > LLC",
        implementationBackend = "NDK C++20 (arm64-v8a)",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC)",
        warmupMinMillis = 300,
        warmupMaxMillis = 2000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "read determinism + copy equality",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("background memory pressure", "DVFS"),
    )

    private var seedCounter = 0x9E37L

    override fun warmUp() {
        MemoryNative.nativeRead(sizeBytes, nextSeed())
    }

    override fun runOnce(): Sample {
        val r = MemoryNative.nativeRead(sizeBytes, nextSeed())
        return Sample(index = -1, workUnits = r[0], durationNanos = r[1], timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        val ok = MemoryNative.nativeCorrectness()
        return CorrectnessResult(passed = ok, kind = checksumKind, finite = ok, reason = if (!ok) "checksum/determinism" else null)
    }

    private fun nextSeed(): Long {
        seedCounter = seedCounter * 6364136223846793005L + 1442695040888963407L
        return seedCounter
    }
}

class MemoryCopyWorkload(
    private val sizeBytes: Long = 64L * 1024 * 1024,
) : Workload {

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "mem.bandwidth.copy",
        workloadVersion = "0.1.0-alpha",
        category = "MEMORY",
        measurementTarget = "sequential copy bandwidth (GB/s, memcpy)",
        algorithm = "memcpy 64MB src->dst, > LLC",
        implementationBackend = "NDK C++20 (arm64-v8a, libc memcpy)",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "native clock_gettime(CLOCK_MONOTONIC)",
        warmupMinMillis = 300,
        warmupMaxMillis = 2000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "read determinism + copy equality",
        invalidationRules = listOf("correctness mismatch"),
        knownInterferences = listOf("background memory pressure", "DVFS"),
    )

    private var seedCounter = 0x1234L

    override fun warmUp() {
        MemoryNative.nativeCopy(sizeBytes, nextSeed())
    }

    override fun runOnce(): Sample {
        val r = MemoryNative.nativeCopy(sizeBytes, nextSeed())
        return Sample(index = -1, workUnits = r[0], durationNanos = r[1], timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        val ok = MemoryNative.nativeCorrectness()
        return CorrectnessResult(passed = ok, kind = checksumKind, finite = ok, reason = if (!ok) "checksum/determinism" else null)
    }

    private fun nextSeed(): Long {
        seedCounter = seedCounter * 6364136223846793005L + 1442695040888963407L
        return seedCounter
    }
}
