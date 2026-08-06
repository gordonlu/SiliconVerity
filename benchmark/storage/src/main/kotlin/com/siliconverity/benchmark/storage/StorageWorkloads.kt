package com.siliconverity.benchmark.storage

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.util.Random

abstract class StorageWorkload(
    protected val dir: File,
    protected val sizeBytes: Long,
    protected val fileName: String,
) {
    init {
        dir.mkdirs()
    }

    protected val file: File get() = File(dir, fileName)
    protected val buf = ByteArray(64 * 1024)

    protected fun writeDeterministic(seed: Long): Long {
        val r = Random(seed)
        FileOutputStream(file).use { fos ->
            var written = 0L
            var checksum = 0L
            while (written < sizeBytes) {
                r.nextBytes(buf)
                val n = minOf(buf.size.toLong(), sizeBytes - written).toInt()
                fos.write(buf, 0, n)
                for (i in 0 until n) checksum += buf[i].toLong() and 0xFF
                written += n
            }
            return checksum
        }
    }

    protected fun readAndChecksum(): Long {
        FileInputStream(file).use { fis ->
            var checksum = 0L
            while (true) {
                val n = fis.read(buf)
                if (n <= 0) break
                for (i in 0 until n) checksum += buf[i].toLong() and 0xFF
            }
            return checksum
        }
    }
}

class StorageWriteWorkload(
    dir: File,
    sizeBytes: Long = 32 * 1024 * 1024,
) : StorageWorkload(dir, sizeBytes, "sv_storage_write.bin"), Workload {

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "storage.seq_write.buffered",
        workloadVersion = "0.1.0-alpha",
        category = "STORAGE",
        measurementTarget = "buffered sequential write throughput (MB/s)",
        algorithm = "write 32MB high-entropy PRNG data, FileOutputStream (buffered, no fsync)",
        implementationBackend = "Kotlin java.io",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around write",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "file size == sizeBytes",
        invalidationRules = listOf("write incomplete"),
        knownInterferences = listOf("page cache", "background I/O", "free space"),
    )

    private var seedCounter = 0x5AL

    override fun warmUp() {
        writeDeterministic(nextSeed())
    }

    override fun runOnce(): Sample {
        val t0 = System.nanoTime()
        writeDeterministic(nextSeed())
        val t1 = System.nanoTime()
        return Sample(index = -1, workUnits = sizeBytes, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): Boolean = file.length() == sizeBytes

    private fun nextSeed(): Long {
        seedCounter = seedCounter * 6364136223846793005L + 1442695040888963407L
        return seedCounter
    }
}

class StorageReadWorkload(
    dir: File,
    sizeBytes: Long = 32 * 1024 * 1024,
) : StorageWorkload(dir, sizeBytes, "sv_storage_read.bin"), Workload {

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "storage.seq_read.warm",
        workloadVersion = "0.1.0-alpha",
        category = "STORAGE",
        measurementTarget = "warm sequential read throughput (MB/s)",
        algorithm = "read 32MB file sequentially (warm, page-cached)",
        implementationBackend = "Kotlin java.io",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around read",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "checksum matches written",
        invalidationRules = listOf("checksum mismatch"),
        knownInterferences = listOf("page cache", "background I/O"),
    )

    private var expectedChecksum: Long = 0L
    private var warmed = false

    override fun warmUp() {
        runOnce()
    }

    override fun runOnce(): Sample {
        if (!warmed) {
            expectedChecksum = writeDeterministic(0xC0FFEEL)
            warmed = true
        }
        val t0 = System.nanoTime()
        readAndChecksum()
        val t1 = System.nanoTime()
        return Sample(index = -1, workUnits = sizeBytes, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): Boolean {
        if (file.length() != sizeBytes) return false
        return readAndChecksum() == expectedChecksum
    }
}
