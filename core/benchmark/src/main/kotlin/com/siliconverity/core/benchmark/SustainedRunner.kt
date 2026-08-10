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
        require(durationSec > 0) { "durationSec must be positive" }
        require(windowSec > 0) { "windowSec must be positive" }
        workload.calibrate(100)
        workload.warmUp()
        val startedAt = env.nowIso()
        val start = clockNanos()
        val thermalStart = env.thermalStatusStart
        var windowWorkUnits = 0L
        var windowDurationNanos = 0L
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
            windowWorkUnits += sample.workUnits
            windowDurationNanos += sample.durationNanos

            if (elapsedAfter >= nextWindowEnd) {
                val throughput = if (windowDurationNanos > 0L) {
                    windowWorkUnits.toDouble() / (windowDurationNanos / 1_000_000_000.0)
                } else 0.0
                val thermal = env.thermalStatusEnd()
                samples += SustainedSample(
                    windowIndex = samples.size,
                    elapsedSec = elapsedAfter,
                    throughput = throughput,
                    thermalStatus = thermal,
                )
                windowWorkUnits = 0L
                windowDurationNanos = 0L
                nextWindowEnd = (kotlin.math.floor(elapsedAfter / windowSec) + 1.0) * windowSec
                onProgress(
                    SustainedProgress(
                        elapsedSec = elapsedAfter,
                        durationSec = durationSec,
                        currentThroughput = throughput,
                        thermalStatus = thermal,
                        samples = samples.toList(),
                    )
                )
            }
        }

        val actualDurationNanos = clockNanos() - start
        val actualDurationSec = actualDurationNanos / 1_000_000_000.0
        if (windowDurationNanos > 0L) {
            val throughput = windowWorkUnits.toDouble() / (windowDurationNanos / 1_000_000_000.0)
            samples += SustainedSample(
                windowIndex = samples.size,
                elapsedSec = actualDurationSec,
                throughput = throughput,
                thermalStatus = env.thermalStatusEnd(),
            )
        }

        val durationD = actualDurationSec
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
                if (s.elapsedSec < initialEnd) continue
                if (s.throughput < threshold) { run++; if (run >= 3) return s.elapsedSec } else run = 0
            }
            return durationD
        }
        val t90 = if (initialMedian > 0) firstConsecutiveBelow(0.9 * initialMedian) else durationD
        val t80 = if (initialMedian > 0) firstConsecutiveBelow(0.8 * initialMedian) else durationD

        // worstStableWindow: 滚动 60 秒窗口中位数；短测试使用全部可用窗口。
        val rollingWindowCount = maxOf(1, 60 / windowSec)
        val worstWindow = if (samples.size < rollingWindowCount) {
            Statistics.median(samples.map { it.throughput })
        } else {
            (0..samples.size - rollingWindowCount).map { i ->
                Statistics.median(samples.subList(i, i + rollingWindowCount).map { it.throughput })
            }.minOrNull() ?: 0.0
        }

        val correctness = workload.correctnessCheck()

        SustainedResult(
            runId = env.runId(),
            workloadId = workload.spec.workloadId,
            workloadVersion = workload.spec.workloadVersion,
            deviceModel = env.deviceModel,
            socReported = env.socReported,
            androidVersion = env.androidVersion,
            appVersion = env.appVersion,
            startedAt = startedAt,
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
            actualDurationNanos = actualDurationNanos,
            correctness = correctness,
        )
    }
}
