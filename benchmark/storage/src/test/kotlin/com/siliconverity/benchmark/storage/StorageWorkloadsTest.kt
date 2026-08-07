package com.siliconverity.benchmark.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageWorkloadsTest {

    private fun newTempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "sv_test_${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun readWorkloadWriteThenReadChecksumMatches() {
        val dir = newTempDir()
        val workload = StorageReadWorkload(dir, sizeBytes = 1024 * 1024)
        workload.warmUp()
        assertTrue("correctness after warmup", workload.correctnessCheck().passed)
        val sample = workload.runOnce()
        assertEquals(1024 * 1024L, sample.workUnits)
        assertTrue("duration > 0", sample.durationNanos > 0)
        assertTrue("throughput > 0", sample.throughput > 0)
        dir.deleteRecursively()
    }

    @Test
    fun writeWorkloadProducesFullSizeFile() {
        val dir = newTempDir()
        val workload = StorageWriteWorkload(dir, sizeBytes = 512 * 1024)
        workload.warmUp()
        assertTrue("correctness after write", workload.correctnessCheck().passed)
        assertEquals(512 * 1024L, workload.runOnce().workUnits)
        dir.deleteRecursively()
    }

    @Test
    fun durableWriteWorkloadProducesFullSizeFile() {
        val dir = newTempDir()
        val workload = StorageDurableWriteWorkload(dir, sizeBytes = 512 * 1024)
        workload.warmUp()
        assertTrue("correctness after durable write", workload.correctnessCheck().passed)
        assertEquals(512 * 1024L, workload.runOnce().workUnits)
        dir.deleteRecursively()
    }
}
