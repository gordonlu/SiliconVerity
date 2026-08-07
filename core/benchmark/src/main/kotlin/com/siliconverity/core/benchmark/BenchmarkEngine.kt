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

    fun execute(workload: Workload, protocol: BenchmarkProtocol = DefaultBenchmarkProtocol): RunManifest {
        val spec = workload.spec
        val runStart = monotonicClockNanos()
        val thermalStart = environment.thermalStatusStart
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
            while (measurementSamples.size < target) {
                measurementSamples.add(workload.runOnce().copy(index = measurementSamples.size))
            }
            val mcv = Statistics.cv(measurementSamples.map { it.throughput })
            if (!mcv.isNaN() && mcv <= protocol.stableCvThreshold) break
        }

        val correctness = workload.correctnessCheck()
        val correctnessOk = correctness.passed
        val summary = Statistics.summarize(measurementSamples)
        val cv = summary.cv

        val validity = when {
            !correctnessOk -> ValidityLevel.INVALID
            cv.isNaN() -> ValidityLevel.INVALID
            cv <= protocol.stableCvThreshold -> ValidityLevel.STABLE
            cv <= protocol.variableCvThreshold -> ValidityLevel.VARIABLE
            else -> ValidityLevel.RETEST_RECOMMENDED
        }

        val warnings = mutableListOf<String>()
        if (!stable) warnings += "warmup did not converge within max time"
        if (cv.isNaN()) warnings += "cv unavailable (zero median)"
        if (cv > protocol.variableCvThreshold) warnings += "cv %.4f exceeds threshold %.4f".format(cv, protocol.variableCvThreshold)

        return RunManifest(
            runId = environment.runId(),
            sessionId = "",
            benchmarkProtocolVersion = protocol.protocolVersion,
            appVersion = environment.appVersion,
            benchmarkEngineVersion = environment.engineVersion,
            workloadId = spec.workloadId,
            workloadVersion = spec.workloadVersion,
            startedAt = environment.nowIso(),
            abi = environment.abi,
            androidVersion = environment.androidVersion,
            securityPatch = environment.securityPatch,
            deviceModel = environment.deviceModel,
            socReported = environment.socReported,
            batteryLevel = environment.batteryLevel,
            chargingState = environment.chargingState,
            powerSaveMode = environment.powerSaveMode,
            thermalStatusStart = environment.thermalStatusStart,
            thermalStatusEnd = environment.thermalStatusEnd(),
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
                warmupMinSeconds = protocol.warmupMinSeconds,
                warmupMaxSeconds = protocol.warmupMaxSeconds,
                warmupConvergeThreshold = protocol.warmupConvergeThreshold,
                measurementSamplesActual = measurementSamples.size,
                stableCvThreshold = protocol.stableCvThreshold,
                variableCvThreshold = protocol.variableCvThreshold,
                targetRoundMillis = protocol.targetRoundMillis,
                provisional = protocol.provisional,
            ),
            validityLevel = validity,
            checksumKind = workload.checksumKind,
            thermalTimeline = listOf(
                ThermalSample(0.0, thermalStart),
                ThermalSample((monotonicClockNanos() - runStart) / 1_000_000_000.0, environment.thermalStatusEnd()),
            ),
            warnings = warnings,
        )
    }
}
