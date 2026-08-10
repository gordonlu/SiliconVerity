package com.siliconverity.core.benchmark

class BenchmarkEngine(
    private val monotonicClockNanos: () -> Long,
    private val environment: Environment,
) {

    interface Environment {
        val appVersion: String
        val engineVersion: String
        val abi: String
        val androidVersion: String
        val securityPatch: String
        val deviceModel: String
        val socReported: String
        val batteryLevel: Int
        val chargingState: String
        val powerSaveMode: Boolean
        val thermalStatusStart: String
        fun thermalStatusEnd(): String
        fun nowIso(): String
        fun runId(): String
    }

    fun execute(
        workload: Workload,
        protocol: BenchmarkProtocol = DefaultBenchmarkProtocol,
        onPhase: PhaseListener? = null,
    ): RunManifest {
        val stableCvThreshold = workload.stableCvThresholdOverride ?: protocol.stableCvThreshold
        val spec = workload.spec
        onPhase?.onPhase(BenchmarkPhase.CALIBRATING, null, null)
        workload.calibrate(protocol.targetRoundMillis.toLong())
        val startedAt = environment.nowIso()
        val runStart = monotonicClockNanos()
        val thermalStart = environment.thermalStatusStart
        val batteryStart = environment.batteryLevel
        val chargingStart = environment.chargingState
        val powerSaveStart = environment.powerSaveMode
        workload.warmUp()
        val warmupSamples = mutableListOf<Sample>()
        val warmupDeadlineMs = spec.warmupMaxMillis
        val warmupStart = monotonicClockNanos()
        val recent = ArrayDeque<Double>()

        var stable = false
        while (true) {
            val sample = workload.runOnce()
            warmupSamples.add(sample)
            recent.addLast(sample.throughput)
            if (recent.size > 5) recent.removeFirst()
            onPhase?.onPhase(BenchmarkPhase.WARMING_UP, recent.size, 5)
            val elapsedMs = (monotonicClockNanos() - warmupStart) / 1_000_000
            if (elapsedMs >= spec.warmupMinMillis && recent.size >= 5) {
                val wcv = Statistics.cv(recent.toList())
                if (!wcv.isNaN() && wcv < spec.warmupConvergeThreshold) {
                    stable = true
                    break
                }
            }
            if (elapsedMs >= warmupDeadlineMs) break
        }

        // 自适应 5/7/11: 5 轮后若稳定(CV<=stableCv)则停, 否则扩到 7, 再到 11
        val measurementSamples = mutableListOf<Sample>()
        val sampleTargets = listOf(
            protocol.measurementMinSamples,
            protocol.measurementRecommendedSamples,
            protocol.measurementMaxSamples,
        )
        for (target in sampleTargets) {
            onPhase?.onPhase(BenchmarkPhase.MEASURING, 0, target)
            while (measurementSamples.size < target) {
                measurementSamples.add(workload.runOnce().copy(index = measurementSamples.size))
                onPhase?.onPhase(BenchmarkPhase.MEASURING, measurementSamples.size, target)
            }
            val mcv = Statistics.cv(measurementSamples.map { it.throughput })
            if (!mcv.isNaN() && mcv <= stableCvThreshold) break
        }

        onPhase?.onPhase(BenchmarkPhase.VERIFYING, null, null)
        val correctness = workload.correctnessCheck()
        val correctnessOk = correctness.passed
        val summary = Statistics.summarize(measurementSamples)
        val cv = summary.cv

        val validity = when {
            !correctnessOk -> ValidityLevel.INVALID
            cv.isNaN() -> ValidityLevel.INVALID
            cv <= stableCvThreshold -> ValidityLevel.STABLE
            cv <= protocol.variableCvThreshold -> ValidityLevel.VARIABLE
            else -> ValidityLevel.RETEST_RECOMMENDED
        }

        onPhase?.onPhase(BenchmarkPhase.FINALIZING, null, null)

        val warnings = mutableListOf<String>()
        if (!stable && warmupDeadlineMs > 0) warnings += "warmup did not converge within max time"
        if (cv.isNaN()) warnings += "cv unavailable (zero median)"
        if (cv > protocol.variableCvThreshold) warnings += "cv %.4f exceeds threshold %.4f".format(cv, protocol.variableCvThreshold)

        val endedAt = environment.nowIso()
        val actualDurationNanos = monotonicClockNanos() - runStart
        val thermalEnd = environment.thermalStatusEnd()
        return RunManifest(
            runId = environment.runId(),
            sessionId = "",
            benchmarkProtocolVersion = protocol.protocolVersion,
            appVersion = environment.appVersion,
            benchmarkEngineVersion = environment.engineVersion,
            workloadId = spec.workloadId,
            workloadVersion = spec.workloadVersion,
            startedAt = startedAt,
            endedAt = endedAt,
            actualDurationNanos = actualDurationNanos,
            abi = environment.abi,
            androidVersion = environment.androidVersion,
            securityPatch = environment.securityPatch,
            deviceModel = environment.deviceModel,
            socReported = environment.socReported,
            batteryLevel = batteryStart,
            chargingState = chargingStart,
            powerSaveMode = powerSaveStart,
            thermalStatusStart = thermalStart,
            thermalStatusEnd = thermalEnd,
            testOrder = listOf(spec.workloadId),
            warmupSamples = warmupSamples,
            measurementSamples = measurementSamples,
            median = summary.median,
            mad = summary.mad,
            cv = summary.cv,
            minimum = summary.minimum,
            maximum = summary.maximum,
            trendSlope = summary.trendSlope,
            outlierCount = summary.outlierCount,
            correctnessStatus = correctnessOk,
            correctness = correctness,
            protocol = ProtocolSnapshot(
                protocolVersion = protocol.protocolVersion,
                warmupMinSeconds = spec.warmupMinMillis / 1000.0,
                warmupMaxSeconds = spec.warmupMaxMillis / 1000.0,
                warmupConvergeThreshold = spec.warmupConvergeThreshold,
                measurementSamplesActual = measurementSamples.size,
                stableCvThreshold = stableCvThreshold,
                variableCvThreshold = protocol.variableCvThreshold,
                targetRoundMillis = protocol.targetRoundMillis,
                provisional = protocol.provisional,
            ),
            validityLevel = validity,
            checksumKind = workload.checksumKind,
            thermalTimeline = listOf(
                ThermalSample(0.0, thermalStart),
                ThermalSample(actualDurationNanos / 1_000_000_000.0, thermalEnd),
            ),
            diagnostics = workload.diagnostics,
            warnings = warnings,
        )
    }
}
