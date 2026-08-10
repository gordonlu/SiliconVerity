package com.siliconverity.nativegpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGpuResultTest {

    @Test
    fun parsesComputeSaturationDiagnosticsWithoutBreakingKeyValueProtocol() {
        val result = NativeGpuResult.parse(
            "supported=1;deviceName=GPU;driverVersion=1.2.3;vulkanVersion=1.3.0;" +
                "metricValue=1434.25;metricUnit=GFLOPS;medianNs=300000000;cv=0.012;" +
                "checksumValid=1;commandRecordingNs=1000;queueSubmitNs=2000;" +
                "gpuExecNs=299000000;completionWaitNs=299900000;spirvHash=abc;" +
                "arithType=FP32;arithContract=DEVICE_DEFAULT;retest=0;" +
                "sampleNs=298000000,300000000,301000000;" +
                "diag=groups=4096 primeRounds=120 adpf=1,0,0,128,0;invalidReason=",
        )

        assertTrue(result.supported)
        assertEquals(1434.25, result.metricValue)
        assertEquals(0.012, result.coefficientOfVariation)
        assertTrue(result.checksumValid)
        assertEquals(listOf(298000000L, 300000000L, 301000000L), result.sampleNanos)
        assertEquals("groups=4096 primeRounds=120 adpf=1,0,0,128,0", result.diag)
        assertNull(result.invalidReason)
    }

    @Test
    fun parsesGraphicsSwapchainCounters() {
        val result = NativeGpuResult.parse(
            "supported=1;metricValue=86.2;metricUnit=scene/s;medianNs=11600000;cv=0.02;" +
                "checksumValid=1;gpuExecNs=22100000;sampleNs=23000000,23500000;" +
                "totalFrames=850;elapsedNs=20000000000;p95FrameNs=26000000;" +
                "surfaceWidth=1920;surfaceHeight=1080;presentMode=MAILBOX;" +
                "presentedFps=42.5;workloadIterations=4;measuredSceneIterations=3400;invalidReason=",
        )

        assertEquals(850L, result.totalFrames)
        assertEquals(20_000_000_000L, result.elapsedNanos)
        assertEquals(26_000_000L, result.p95FrameNanos)
        assertEquals(1920, result.surfaceWidth)
        assertEquals(1080, result.surfaceHeight)
        assertEquals("MAILBOX", result.presentMode)
        assertEquals(42.5, result.presentedFps)
        assertEquals(4, result.workloadIterations)
        assertEquals(3400L, result.measuredSceneIterations)
    }
}
