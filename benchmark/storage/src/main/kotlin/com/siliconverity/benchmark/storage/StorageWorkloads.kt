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

abstract class StorageWorkload(
    protected val dir: File,
    protected val sizeBytes: Long,
    protected val fileName: String,
) {
    init { dir.mkdirs() }
    protected val file: File get() = File(dir, fileName)
    private val chunkBuf = ByteArray(64 * 1024)
    private val fullData = ByteArray(sizeBytes.toInt())
    private val readBuf = ByteArray(sizeBytes.toInt())
    private var expectedChecksum = 0L
    private var dataReady = false

    protected fun ensureData() {
        if (dataReady) return
        val r = Random(0xC0FFEEL)
        var off = 0
        while (off < fullData.size) {
            r.nextBytes(chunkBuf)
            val n = minOf(chunkBuf.size, fullData.size - off)
            System.arraycopy(chunkBuf, 0, fullData, off, n)
            off += n
        }
        var cs = 0L
        for (b in fullData) cs += b.toLong() and 0xFF
        expectedChecksum = cs
        dataReady = true
    }

    /** 计时: 仅写 (buffered, 无 fsync)。 */
    protected fun writeBuffered() {
        ensureData()
        FileOutputStream(file).use { it.write(fullData) }
    }

    /** 计时: 写 + fdatasync (durable)。 */
    protected fun writeDurable() {
        ensureData()
        FileOutputStream(file).use { fos ->
            fos.write(fullData)
            fos.fd.sync()
        }
    }

    /** 计时: 仅读 (返回读到的字节数)。 */
    protected fun readInto(): Int {
        var total = 0
        FileInputStream(file).use { fis ->
            while (total < readBuf.size) {
                val n = fis.read(readBuf, total, readBuf.size - total)
                if (n <= 0) break
                total += n
            }
        }
        return total
    }

    /** 不计时: 校验读回内容。 */
    protected fun verifyReadback(): Boolean {
        if (file.length() != sizeBytes) return false
        val len = readInto()
        if (len != sizeBytes.toInt()) return false
        var cs = 0L
        for (i in 0 until len) cs += readBuf[i].toLong() and 0xFF
        return cs == expectedChecksum
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
        measurementTarget = "buffered sequential write throughput (MB/s, page cache, no fsync)",
        algorithm = "write 32MB pre-generated high-entropy data, FileOutputStream (buffered, no fsync)",
        implementationBackend = "Kotlin java.io",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around write only (data pre-generated)",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "file size + readback checksum",
        invalidationRules = listOf("size mismatch", "checksum mismatch"),
        knownInterferences = listOf("page cache", "background I/O", "free space"),
    )

    private var seedCounter = 0x5AL

    override fun warmUp() { writeBuffered() }

    override fun runOnce(): Sample {
        val t0 = System.nanoTime()
        writeBuffered()
        val t1 = System.nanoTime()
        return Sample(index = -1, workUnits = sizeBytes, durationNanos = t1 - t0, timestamp = Instant.now().toString())
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
        algorithm = "write 256MB + FileOutputStream.fd.sync() (fdatasync), 文件大于缓存使计时以真实设备写为主",
        implementationBackend = "Kotlin java.io",
        dataSize = sizeBytes,
        threadPolicy = "single thread",
        timingMethod = "System.nanoTime around write + fd.sync()",
        warmupMinMillis = 500,
        warmupMaxMillis = 3000,
        warmupConvergeThreshold = 0.05,
        measurementRepetitions = 7,
        correctnessCheck = "file size + readback checksum",
        invalidationRules = listOf("sync failed", "checksum mismatch"),
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
        workloadVersion = "1.0.0",
        category = "STORAGE",
        measurementTarget = "random 4KB write + fdatasync throughput (MB/s, 闪存随机写 + GC 受限)",
        algorithm = "256 次 4KB 随机偏移写 + 每次 fdatasync (预生成偏移与数据, 只计时写+sync)",
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

    private val blockSize = 4096
    private val blockCount = (sizeBytes / blockSize).toInt()
    private val writesPerRound = 256
    private var offsets = IntArray(0)
    private var block = ByteArray(blockSize)
    private var warmed = false

    private fun prepare() {
        ensureData()
        writeBuffered()
        val r = java.util.Random(0xBEEF)
        offsets = IntArray(writesPerRound) { r.nextInt(blockCount) }
        r.nextBytes(block)
    }

    private fun runRound() {
        RandomAccessFile(file, "rw").use { raf ->
            for (i in 0 until writesPerRound) {
                raf.seek(offsets[i].toLong() * blockSize)
                raf.write(block)
                raf.fd.sync()
            }
        }
    }

    override fun warmUp() {
        if (!warmed) { prepare(); warmed = true }
        runRound()
    }

    override fun runOnce(): Sample {
        val t0 = System.nanoTime()
        runRound()
        val t1 = System.nanoTime()
        val bytes = writesPerRound * blockSize.toLong()
        return Sample(index = -1, workUnits = bytes, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        if (file.length() != sizeBytes) {
            return CorrectnessResult(passed = false, kind = ChecksumKind.EXACT, finite = true, reason = "file size mismatch")
        }
        // 抽查: 读回若干写入偏移, 应与 block 一致
        var ok = true
        val probe = IntArray(minOf(8, offsets.size)) { offsets[it] }
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

    override val spec: BenchmarkSpec = BenchmarkSpec(
        workloadId = "storage.seq_read.warm",
        workloadVersion = "0.1.0-alpha",
        category = "STORAGE",
        measurementTarget = "warm sequential read throughput (MB/s, page-cached)",
        algorithm = "read 32MB file into buffer (warm, page-cached)",
        implementationBackend = "Kotlin java.io",
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

    override fun warmUp() {
        if (!warmed) { writeBuffered(); warmed = true }
        readInto()
    }

    override fun runOnce(): Sample {
        val t0 = System.nanoTime()
        readInto()
        val t1 = System.nanoTime()
        return Sample(index = -1, workUnits = sizeBytes, durationNanos = t1 - t0, timestamp = Instant.now().toString())
    }

    override fun correctnessCheck(): CorrectnessResult {
        val ok = verifyReadback()
        return CorrectnessResult(passed = ok, kind = ChecksumKind.EXACT, finite = true, reason = if (!ok) "checksum mismatch" else null)
    }
}
