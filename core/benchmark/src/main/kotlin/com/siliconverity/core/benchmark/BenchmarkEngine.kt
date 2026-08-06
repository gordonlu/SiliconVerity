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

    fun execute(workload: Workload, validityCvThreshold: Double = 0.07): RunManifest {
        val spec = workload.spec
        val warmupSamples = mutableListOf<Sample>()
        val warmupDeadlineMs = spec.warmupMaxMillis
        val warmupStart = monotonicClockNanos()

        var lastWindow = 0.0
        var stable = false
        while (true) {
            val sample = workload.runOnce()
            warmupSamples.add(sample)
            val elapsedMs = (monotonicClockNanos() - warmupStart) / 1_000_000
            if (elapsedMs >= spec.warmupMinMillis) {
                val ratio = if (lastWindow > 0) kotlin.math.abs(sample.throughput - lastWindow) / lastWindow else 1.0
                if (ratio < spec.warmupConvergeThreshold) {
                    stable = true
                    break
                }
            }
            lastWindow = sample.throughput
            if (elapsedMs >= warmupDeadlineMs) break
        }

        val measurementSamples = mutableListOf<Sample>()
        repeat(spec.measurementRepetitions) { idx ->
            val s = workload.runOnce()
            measurementSamples.add(s.copy(index = idx))
        }

        val correctnessOk = workload.correctnessCheck()
        val summary = Statistics.summarize(measurementSamples)
        val cv = summary.cv

        val validity = when {
            !correctnessOk -> ValidityLevel.INVALID
            cv.isNaN() -> ValidityLevel.INVALID
            cv <= 0.03 -> ValidityLevel.CLEAN
            cv <= validityCvThreshold -> ValidityLevel.ACCEPTABLE
            else -> ValidityLevel.NOISY
        }

        val warnings = mutableListOf<String>()
        if (!stable) warnings += "warmup did not converge within max time"
        if (cv.isNaN()) warnings += "cv unavailable (zero median)"
        if (cv > validityCvThreshold) warnings += "cv %.4f exceeds threshold %.4f".format(cv, validityCvThreshold)

        return RunManifest(
            runId = environment.runId(),
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
            correctnessStatus = correctnessOk,
            validityLevel = validity,
            warnings = warnings,
        )
    }
}
