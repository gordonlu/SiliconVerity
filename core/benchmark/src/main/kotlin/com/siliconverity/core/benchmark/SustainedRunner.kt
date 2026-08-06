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

        while (true) {
            val elapsed = (clockNanos() - start) / 1_000_000_000.0
            if (elapsed >= durationSec) break
            coroutineContext.ensureActive()

            val sample = workload.runOnce()
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

        val head = samples.take(minOf(60, samples.size)).map { it.throughput }
        val tail = samples.takeLast(minOf(60, samples.size)).map { it.throughput }
        val initialMedian = Statistics.median(head)
        val stableMedian = Statistics.median(tail)
        val retention = if (initialMedian > 0) stableMedian / initialMedian else 0.0

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
            thermalStatusStart = thermalStart,
            thermalStatusEnd = env.thermalStatusEnd(),
            batteryLevel = env.batteryLevel,
            chargingState = env.chargingState,
        )
    }
}
