package com.siliconverity.core.benchmark

/**
 * 把现有各管线结果适配为统一 BenchmarkRun (读侧统一, 不重写管线)。
 */

fun RunManifest.toBenchmarkRun(): BenchmarkRun {
    val valid = validityLevel != ValidityLevel.INVALID
    return BenchmarkRun(
        identity = RunIdentity(
            runId = runId,
            sessionId = sessionId,
            benchmarkProtocolVersion = benchmarkProtocolVersion,
            workloadId = workloadId,
            workloadVersion = workloadVersion,
            shaderSourceVersion = shaderSourceVersion,
            spirvHash = spirvHash,
            pipelineConfigVersion = pipelineConfigVersion,
            scoreVersion = scoreVersion,
            arithmeticType = arithmeticType,
            arithmeticContract = arithmeticContract,
            checksumKind = checksumKind,
        ),
        environment = EnvironmentSnapshot(
            appVersion = appVersion,
            engineVersion = benchmarkEngineVersion,
            abi = abi,
            androidVersion = androidVersion,
            securityPatch = securityPatch,
            deviceModel = deviceModel,
            socReported = socReported,
            batteryLevel = batteryLevel,
            chargingState = chargingState,
            powerSaveMode = powerSaveMode,
            thermalStatusStart = thermalStatusStart,
            thermalStatusEnd = thermalStatusEnd,
            thermalTimeline = thermalTimeline,
            gameMode = gameMode,
        ),
        protocol = protocol ?: ProtocolSnapshot(
            protocolVersion = benchmarkProtocolVersion,
            warmupMinSeconds = 0.0,
            warmupMaxSeconds = 0.0,
            warmupConvergeThreshold = 0.0,
            measurementSamplesActual = measurementSamples.size,
            stableCvThreshold = 0.03,
            variableCvThreshold = 0.07,
            targetRoundMillis = 0,
            provisional = true,
        ),
        correctness = correctness ?: CorrectnessResult(
            passed = correctnessStatus,
            kind = checksumKind ?: ChecksumKind.EXACT,
            finite = correctnessStatus,
            reason = if (!correctnessStatus) "checksum" else null,
        ),
        validity = ValidityResult(
            valid = valid,
            scoreEligible = validityLevel == ValidityLevel.STABLE || validityLevel == ValidityLevel.VARIABLE,
            stability = validityLevel,
            robustCv = cv,
            reason = if (!valid) "invalid" else null,
        ),
        payload = BenchmarkPayload.Scalar(
            samples = measurementSamples,
            summary = Statistics.summarize(measurementSamples),
        ),
        startedAt = startedAt,
        endedAt = endedAt.ifEmpty { startedAt },
        actualDurationNanos = actualDurationNanos,
        diagnostics = diagnostics,
        warnings = warnings,
    )
}

fun SustainedResult.toBenchmarkRun(): BenchmarkRun = BenchmarkRun(
    identity = RunIdentity(
        runId = runId,
        benchmarkProtocolVersion = "0.1.0",
        workloadId = workloadId,
        workloadVersion = workloadVersion,
    ),
    environment = EnvironmentSnapshot(
        appVersion = appVersion,
        engineVersion = "",
        abi = "",
        androidVersion = androidVersion,
        securityPatch = "",
        deviceModel = deviceModel,
        socReported = socReported,
        batteryLevel = batteryLevel,
        chargingState = chargingState,
        powerSaveMode = false,
        thermalStatusStart = thermalStatusStart,
        thermalStatusEnd = thermalStatusEnd,
    ),
    protocol = ProtocolSnapshot("0.1.0", 0.0, 0.0, 0.0, samples.size, 0.03, 0.07, 0, true),
    correctness = correctness,
    validity = ValidityResult(
        valid = correctness.passed,
        scoreEligible = false,
        stability = if (correctness.passed) ValidityLevel.STABLE else ValidityLevel.INVALID,
        robustCv = 0.0,
        reason = correctness.reason,
    ),
    payload = BenchmarkPayload.Timeline(result = this),
    startedAt = startedAt,
    endedAt = startedAt,
    actualDurationNanos = actualDurationNanos,
    warnings = if (correctness.passed) emptyList() else listOf(correctness.reason ?: "sustained correctness failed"),
)

fun MemoryLatencyResult.toBenchmarkRun(): BenchmarkRun {
    val ok = points.all { it.latencyNs >= 0 }
    return BenchmarkRun(
        identity = RunIdentity(
            runId = runId,
            sessionId = sessionId,
            benchmarkProtocolVersion = "0.1.0",
            workloadId = "mem.latency.curve",
            workloadVersion = "0.1.0-alpha",
        ),
        environment = EnvironmentSnapshot(
            appVersion = "",
            engineVersion = "",
            abi = abi,
            androidVersion = androidVersion,
            securityPatch = "",
            deviceModel = deviceModel,
            socReported = socReported,
            batteryLevel = -1,
            chargingState = "",
            powerSaveMode = false,
            thermalStatusStart = "",
            thermalStatusEnd = "",
        ),
        protocol = ProtocolSnapshot("0.1.0", 0.0, 0.0, 0.0, points.size, 0.03, 0.07, 0, true),
        correctness = CorrectnessResult(passed = ok, kind = ChecksumKind.EXACT, finite = ok, reason = if (!ok) "latency measurement failed" else null),
        validity = ValidityResult(valid = ok, scoreEligible = false, stability = if (ok) ValidityLevel.STABLE else ValidityLevel.INVALID, robustCv = 0.0),
        payload = BenchmarkPayload.Curve(points = points),
        startedAt = startedAt,
        endedAt = startedAt,
        actualDurationNanos = 0L,
        warnings = if (!ok) listOf("latency measurement failed") else emptyList(),
    )
}

/** 统一历史行的主指标展示 (按 payload 类型)。 */
fun BenchmarkRun.primaryMetric(): String = when (val p = payload) {
    is BenchmarkPayload.Scalar -> "%.2f %s".format(
        WorkloadFormat.scale(identity.workloadId, p.summary.median),
        WorkloadFormat.unit(identity.workloadId),
    )
    is BenchmarkPayload.Timeline -> "retention %.1f%%".format(p.result.retention * 100)
    is BenchmarkPayload.Curve -> {
        val valid = p.points.filter { it.latencyNs >= 0 }
        if (valid.isEmpty()) "-" else "median %.1f ns".format(valid.map { it.latencyNs }.sorted().let { it[it.size / 2] })
    }
    is BenchmarkPayload.Diagnostics -> "diag %d".format(p.metrics.size)
}

/** Scalar payload 的 session median (对比用)。 */
fun BenchmarkRun.payloadSummaryMedian(): Double = when (val p = payload) {
    is BenchmarkPayload.Scalar -> p.summary.median
    else -> 0.0
}
