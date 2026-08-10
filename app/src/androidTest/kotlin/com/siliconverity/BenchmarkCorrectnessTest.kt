package com.siliconverity

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.siliconverity.core.benchmark.Workload
import com.siliconverity.nativecpu.CompressionWorkload
import com.siliconverity.nativecpu.CpuIntegerWorkload
import com.siliconverity.nativecpu.Fp32FmaWorkload
import com.siliconverity.nativecpu.IntBranchWorkload
import com.siliconverity.nativecpu.MultithreadWorkload
import com.siliconverity.nativegpu.GpuWorkload
import com.siliconverity.nativegpu.VulkanBench
import com.siliconverity.nativememory.MemoryCopyWorkload
import com.siliconverity.nativememory.MemoryReadWorkload
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation 正确性测试 (需设备, connectedAndroidTest)。
 * 锁住各 workload 的 correctnessCheck + 正向吞吐, 以及 GPU checksum
 * (会抓到如 FP32 Independent 跨 dispatch 溢出导致 checksum 失败这类回归)。
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkCorrectnessTest {

    private fun assertCpuCorrectness(w: Workload) {
        w.calibrate(100)
        w.warmUp()
        val id = w.spec.workloadId
        assertTrue("$id correctness", w.correctnessCheck().passed)
        val s = w.runOnce()
        assertTrue("$id positive duration", s.durationNanos > 0)
        assertTrue("$id positive throughput", s.throughput > 0)
    }

    @Test
    fun cpuInteger() = assertCpuCorrectness(CpuIntegerWorkload())

    @Test
    fun cpuFp32Fma() = assertCpuCorrectness(Fp32FmaWorkload())

    @Test
    fun cpuIntBranch() = assertCpuCorrectness(IntBranchWorkload())

    @Test
    fun cpuCompression() = assertCpuCorrectness(CompressionWorkload())

    @Test
    fun cpuMultithread() = assertCpuCorrectness(MultithreadWorkload())

    @Test
    fun memoryReadMeasuredBuffer() = assertCpuCorrectness(MemoryReadWorkload())

    @Test
    fun memoryCopyMeasuredBuffer() = assertCpuCorrectness(MemoryCopyWorkload())

    private fun assertGpuChecksum(wl: GpuWorkload) {
        val r = VulkanBench().run(wl, 200)
        assertTrue("$wl supported", r.supported)
        assertTrue("$wl checksum (catches overflow/NaN-style regressions)", r.checksumValid)
        assertTrue("$wl metric", (r.metricValue ?: 0.0) > 0.0)
        assertTrue("$wl native samples", r.sampleNanos.size == 12)
    }

    private fun runGpuSuite() {
        assertGpuChecksum(GpuWorkload.FP32_INDEPENDENT)
        assertGpuChecksum(GpuWorkload.FP32_DEPENDENCY)
        assertGpuChecksum(GpuWorkload.BUFFER_THROUGHPUT)
    }

    @Test
    fun gpuSuiteChecksum() = runGpuSuite()

}
