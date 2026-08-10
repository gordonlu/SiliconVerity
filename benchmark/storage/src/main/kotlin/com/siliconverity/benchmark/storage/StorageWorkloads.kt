package com.siliconverity.benchmark.storage

import com.siliconverity.core.benchmark.BenchmarkSpec
import com.siliconverity.core.benchmark.ChecksumKind
import com.siliconverity.core.benchmark.CorrectnessResult
import com.siliconverity.core.benchmark.Sample
import com.siliconverity.core.benchmark.Workload
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Instant
import java.util.Random

/**
 * 分块流式存储 workload (OOM 安全: 无 sizeBytes 级内存分配, 只用 64KB chunk)。
 * 预生成数据: 一次性生成到 <file>.src (分块, 不计时), 计时写 = 流式拷贝源->目标。
 */
abstract class StorageWorkload(
    protected val dir: File,
    protected val sizeBytes: Long,
    protected val fileName: String,
) {
    init { dir.mkdirs() }
    protected val file: File get() = File(dir, fileName)
    private val sourceFile: File get() = File(dir, "$fileName.src")
    private val chunk = ByteArray(64 * 1024)
    private var expectedChecksum = 0L
    private var sourceReady = false

    protected fun ensureSource() {
        if (sourceReady) return
        if (dir.freeSpace < sizeBytes * 2) {
            throw IllegalStateException("storage space insufficient: need ${sizeBytes * 2} bytes")
        }
        val r = Random(0xC0FFEEL)
        var cs = 0L
        FileOutputStream(sourceFile).use { fos ->
            var written = 0L
            while (written < sizeBytes) {
                r.nextBytes(chunk)
                val n = minOf(chunk.size.toLong(), sizeBytes - written).toInt()
                fos.write(chunk, 0, n)
                for (i in 0 until n) cs += chunk[i].toLong() and 0xFF
                written += n
            }
        }
        expectedChecksum = cs
        sourceReady = true
    }

    protected fun copySourceToTarget() {
        FileInputStream(sourceFile).use { fis ->
            FileOutputStream(file).use { fos ->
                var n = fis.read(chunk)
                while (n > 0) { fos.write(chunk, 0, n); n = fis.read(chunk) }
            }
        }
    }

    /** 计时: 仅写 (buffered, 无 fsync)。 */
    protected fun writeBuffered() {
        ensureSource()
        copySourceToTarget()
    }

    /** 计时: 写 + fdatasync (durable)。 */
    protected fun writeDurable() {
        ensureSource()
        FileInputStream(sourceFile).use { fis ->
            FileOutputStream(file).use { fos ->
                var n = fis.read(chunk)
                while (n > 0) { fos.write(chunk, 0, n); n = fis.read(chunk) }
                fos.fd.sync()
            }
        }
    }

    /** 计时: 仅读 (warm, 分块)。 */
    protected fun readInto(): Long {
        var total = 0L
        FileInputStream(file).use { fis ->
            var n = fis.read(chunk)
            while (n > 0) { total += n; n = fis.read(chunk) }
        }
        return total
    }

    /** 不计时: 读回校验 checksum。 */
    protected fun verifyReadback(): Boolean {
        if (file.length() != sizeBytes) return false
        var cs = 0L
        FileInputStream(file).use { fis ->
            var n = fis.read(chunk)
            while (n > 0) {
                for (i in 0 until n) cs += chunk[i].toLong() and 0xFF
                n = fis.read(chunk)
            }
        }
        return cs == expectedChecksum
    }
}

class StorageWriteWorkload(
    dir: File,
    sizeBytes: Long = 32 * 1024 * 1024,
) : StorageWorkload(dir, sizeBytes, "sv_storage_write.bin"), Workload {

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "storage.seq_write.buffered",
        workloadVersion = "0.2.0-alpha",
        category = "STORAGE",
        measurementTarget = "buffered sequential write throughput (MB/s, page cache, no fsync)",
        algorithm = "stream 32MB pre-generated source -> target, calibrated repeated buffered copies in one sample",
        implementationBackend = "Kotlin java.io (chunked, OOM-safe)",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around copy only (source pre-generated)",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "file size + readback checksum",
        invalidationRules = listOf("size mismatch", "checksum mismatch"),
        knownInterferences = listOf("page cache", "background I/O", "free space"),
    )

    private var seedCounter = 0x5AL
    private var repeats = 1

    override fun calibrate(targetMillis: Long) {
        ensureSource()
        val t0 = System.nanoTime()
        writeBuffered()
        repeats = storageRepeats(System.nanoTime() - t0, targetMillis)
    }

    override fun warmUp() { writeBuffered() }

    override fun runOnce(): Sample {
        val t0 = System.nanoTime()
        repeat(repeats) { writeBuffered() }
        val t1 = System.nanoTime()
        return Sample(index = -1, workUnits = sizeBytes * repeats, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        val ok = verifyReadback()
        return CorrectnessResult(passed = ok, kind = ChecksumKind.EXACT, finite = true, reason = if (!ok) "size/checksum mismatch" else null)
    }

    private fun nextSeed(): Long { seedCounter = seedCounter * 6364136223846793005L + 1442695040888963407L; return seedCounter }
}

class StorageDurableWriteWorkload(
    dir: File,
    sizeBytes: Long = 256 * 1024 * 1024,
) : StorageWorkload(dir, sizeBytes, "sv_storage_durable.bin"), Workload {

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "storage.seq_write.durable",
        workloadVersion = "1.0.0",
        category = "STORAGE",
        measurementTarget = "durable sequential write throughput (MB/s, incl fdatasync, 256MB 超出页缓存)",
        algorithm = "stream 256MB source -> target + fd.sync(), 文件大于缓存使计时以真实设备写为主",
        implementationBackend = "Kotlin java.io (chunked, OOM-safe)",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around copy + fd.sync()",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "file size + readback checksum",
        invalidationRules = listOf("sync failed", "checksum mismatch", "space insufficient"),
        knownInterferences = listOf("flash controller", "GC/WA", "background I/O"),
    )

    override fun warmUp() { writeDurable() }

    override fun runOnce(): Sample {
        val t0 = System.nanoTime()
        writeDurable()
        val t1 = System.nanoTime()
        return Sample(index = -1, workUnits = sizeBytes, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        val ok = verifyReadback()
        return CorrectnessResult(passed = ok, kind = ChecksumKind.EXACT, finite = true, reason = if (!ok) "durable write verify failed" else null)
    }
}

class StorageRandomWriteFsyncWorkload(
    dir: File,
    sizeBytes: Long = 32 * 1024 * 1024,
) : StorageWorkload(dir, sizeBytes, "sv_storage_random.bin"), Workload {

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "storage.random_write.fsync",
        workloadVersion = "2.0.0",
        category = "STORAGE",
        measurementTarget = "random 4KB write + fdatasync throughput (MB/s, 闪存随机写 + GC 受限)",
        algorithm = "calibrated N×256 次 4KB deterministic-random offset writes + fdatasync per write",
        implementationBackend = "Kotlin java.io RandomAccessFile",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around N random writes + fd.sync() each",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "file size + 抽查写入块",
        invalidationRules = listOf("write failed", "verify mismatch"),
        knownInterferences = listOf("flash GC/WA", "background I/O"),
    )

    // 闪存随机写受 GC/WA 影响天然高抖动, 稳定阈值放宽到 5%
    override val stableCvThresholdOverride: Double? = 0.05

    private val blockSize = 4096
    private val blockCount = (sizeBytes / blockSize).toInt()
    private val writesPerRound = 256
    private var offsets = IntArray(0)
    private var block = ByteArray(blockSize)
    private var warmed = false
    private var repeats = 1
    private val offsetRandom = Random(0xBEEF)

    private fun prepareFile() {
        ensureSource()
        copySourceToTarget()
        offsetRandom.nextBytes(block)
    }

    private fun prepareOffsets() {
        offsets = IntArray(writesPerRound * repeats) { offsetRandom.nextInt(blockCount) }
    }

    private fun runRound() {
        RandomAccessFile(file, "rw").use { raf ->
            for (i in offsets.indices) {
                raf.seek(offsets[i].toLong() * blockSize)
                raf.write(block)
                raf.fd.sync()
            }
        }
    }

    override fun warmUp() {
        if (!warmed) { prepareFile(); warmed = true }
        prepareOffsets()
        runRound()
    }

    override fun calibrate(targetMillis: Long) {
        if (!warmed) { prepareFile(); warmed = true }
        repeats = 1
        prepareOffsets()
        val t0 = System.nanoTime()
        runRound()
        repeats = storageRepeats(System.nanoTime() - t0, targetMillis)
    }

    override fun runOnce(): Sample {
        prepareOffsets()
        val t0 = System.nanoTime()
        runRound()
        val t1 = System.nanoTime()
        val bytes = offsets.size * blockSize.toLong()
        return Sample(index = -1, workUnits = bytes, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        if (file.length() != sizeBytes) {
            return CorrectnessResult(passed = false, kind = ChecksumKind.EXACT, finite = true, reason = "file size mismatch")
        }
        var ok = true
        val probe = offsets.distinct()
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(blockSize)
            for (off in probe) {
                raf.seek(off.toLong() * blockSize)
                raf.readFully(buf)
                if (!buf.contentEquals(block)) { ok = false; break }
            }
        }
        return CorrectnessResult(passed = ok, kind = ChecksumKind.EXACT, finite = true, reason = if (!ok) "random write verify mismatch" else null)
    }
}

class StorageReadWorkload(
    dir: File,
    sizeBytes: Long = 32 * 1024 * 1024,
) : StorageWorkload(dir, sizeBytes, "sv_storage_read.bin"), Workload {

    // IO 调度/后台写入竞争, 放宽到 7%
    override val stableCvThresholdOverride: Double? = 0.07

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "storage.seq_read.warm",
        workloadVersion = "0.2.0-alpha",
        category = "STORAGE",
        measurementTarget = "warm sequential read throughput (MB/s, page-cached)",
        algorithm = "read 32MB file chunked, calibrated repeated warm reads in one sample",
        implementationBackend = "Kotlin java.io (chunked, OOM-safe)",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around read only",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "readback checksum matches written",
        invalidationRules = listOf("checksum mismatch"),
        knownInterferences = listOf("page cache", "background I/O"),
    )

    private var warmed = false
    private var repeats = 1

    override fun calibrate(targetMillis: Long) {
        if (!warmed) { writeBuffered(); warmed = true }
        val t0 = System.nanoTime()
        readInto()
        repeats = storageRepeats(System.nanoTime() - t0, targetMillis)
    }

    override fun warmUp() {
        if (!warmed) { writeBuffered(); warmed = true }
        readInto()
    }

    override fun runOnce(): Sample {
        val t0 = System.nanoTime()
        repeat(repeats) { readInto() }
        val t1 = System.nanoTime()
        return Sample(index = -1, workUnits = sizeBytes * repeats, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        val ok = verifyReadback()
        return CorrectnessResult(passed = ok, kind = ChecksumKind.EXACT, finite = true, reason = if (!ok) "checksum mismatch" else null)
    }
}

private fun storageRepeats(probeNanos: Long, targetMillis: Long): Int {
    if (probeNanos <= 0L) return 1
    return kotlin.math.round(targetMillis.coerceAtLeast(50L) * 1_000_000.0 / probeNanos)
        .toInt()
        .coerceIn(1, 512)
}
