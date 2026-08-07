package com.siliconverity.core.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SustainedRunner(
    private val workload: Workload,
    private val env: BenchmarkEngine.Environment,
    private val clockNanos: () -> Long,
) {

    suspend fun run(
        durationSec: Int,
        windowSec: Int = 1,
        onProgress: (SustainedProgress) -> Unit,
    ): SustainedResult = withContext(Dispatchers.Default) {
        workload.warmUp()
        val start = clockNanos()
        val thermalStart = env.thermalStatusStart
        val windowThroughputs = mutableListOf<Double>()
        val samples = mutableListOf<SustainedSample>()
        var nextWindowEnd = windowSec.toDouble()
        var totalWorkUnits = 0L

        while (true) {
            val elapsed = (clockNanos() - start) / 1_000_000_000.0
            if (elapsed >= durationSec) break
            coroutineContext.ensureActive()

            val sample = workload.runOnce()
            totalWorkUnits += sample.workUnits
            val elapsedAfter = (clockNanos() - start) / 1_000_000_000.0
            windowThroughputs += sample.throughput

            if (elapsedAfter >= nextWindowEnd) {
                val median = Statistics.median(windowThroughputs)
                val thermal = env.thermalStatusEnd()
                samples += SustainedSample(
                    windowIndex = samples.size,
                    elapsedSec = nextWindowEnd,
                    throughput = median,
                    thermalStatus = thermal,
                )
                windowThroughputs.clear()
                nextWindowEnd += windowSec
                onProgress(
                    SustainedProgress(
                        elapsedSec = elapsedAfter,
                        durationSec = durationSec,
                        currentThroughput = median,
                        thermalStatus = thermal,
                        samples = samples.toList(),
                    )
                )
            }
        }

        if (windowThroughputs.isNotEmpty()) {
            val median = Statistics.median(windowThroughputs)
            samples += SustainedSample(
                windowIndex = samples.size,
                elapsedSec = (clockNanos() - start) / 1_000_000_000.0,
                throughput = median,
                thermalStatus = env.thermalStatusEnd(),
            )
        }

        val durationD = durationSec.toDouble()
        val warmupSkip = minOf(5.0, durationD * 0.1)
        val initialLen = maxOf(10.0, durationD * 0.1)
        val stableLen = maxOf(30.0, durationD * 0.2)
        val initialEnd = warmupSkip + initialLen
        val stableStart = (durationD - stableLen).coerceAtLeast(initialEnd)

        val head = samples.filter { it.elapsedSec in warmupSkip..initialEnd }.map { it.throughput }
        val tail = samples.filter { it.elapsedSec in stableStart..durationD }.map { it.throughput }
        val initialMedian = Statistics.median(head)
        val stableMedian = Statistics.median(tail)
        val retention = if (initialMedian > 0) stableMedian / initialMedian else 0.0

        // t90/t80: 连续 3 个窗口低于阈值才算 (防单次抖动)
        fun firstConsecutiveBelow(threshold: Double): Double {
            var run = 0
            for (s in samples) {
                if (s.throughput < threshold) { run++; if (run >= 3) return s.elapsedSec } else run = 0
            }
            return durationD
        }
        val t90 = if (initialMedian > 0) firstConsecutiveBelow(0.9 * initialMedian) else durationD
        val t80 = if (initialMedian > 0) firstConsecutiveBelow(0.8 * initialMedian) else durationD

        // worstStableWindow: 滚动 5 窗口 median 的最小值 (非单秒绝对最小)
        val worstWindow = if (samples.size < 5) {
            if (samples.isEmpty()) 0.0 else samples.minOf { it.throughput }
        } else {
            (0..samples.size - 5).map { i -> Statistics.median(samples.subList(i, i + 5).map { it.throughput }) }.minOrNull() ?: 0.0
        }

        SustainedResult(
            runId = env.runId(),
            workloadId = workload.spec.workloadId,
            workloadVersion = workload.spec.workloadVersion,
            deviceModel = env.deviceModel,
            socReported = env.socReported,
            androidVersion = env.androidVersion,
            appVersion = env.appVersion,
            startedAt = env.nowIso(),
            durationSec = durationSec,
            windowSec = windowSec,
            samples = samples,
            initialMedian = initialMedian,
            stableMedian = stableMedian,
            retention = retention,
            timeTo90Percent = t90,
            timeTo80Percent = t80,
            worstStableWindow = worstWindow,
            absoluteWorkCompleted = totalWorkUnits,
            thermalStatusStart = thermalStart,
            thermalStatusEnd = env.thermalStatusEnd(),
            batteryLevel = env.batteryLevel,
            chargingState = env.chargingState,
        )
    }
}
