package com.siliconverity.core.benchmark

import kotlin.math.exp
import kotlin.math.ln

/**
 * 评分引擎 (SV v1.0):
 * - 单项分 = itemBase × clamp(measured/reference, clampMin, clampMax)
 * - 分类分 = 加权几何平均 (eligible workload, 权重重归一化)
 * - 综合分 = 分类加权几何平均 × multiplier (须 4 分类齐全且无 RETEST)
 * - 门禁: correctness 失败/INVALID/RETEST -> eligible=false; scoreEligible 判定
 */
class ScoringEngine(private val pack: ScorePack) {

    fun score(runs: List<BenchmarkRun>): ScoreReport {
        val latest = runs.groupBy { it.identity.workloadId }
            .mapValues { (_, rs) -> rs.maxByOrNull { it.startedAt } }
        val sessionId = runs.maxByOrNull { it.startedAt }?.identity?.sessionId ?: ""

        val workloadScores = mutableListOf<WorkloadScore>()
        val exclusions = mutableListOf<ScoreExclusion>()

        for ((workloadId, ref) in pack.flatWorkloads()) {
            val run = latest[workloadId]
            if (run == null) { exclusions += ScoreExclusion(workloadId, "no run"); continue }
            val refValue = pack.references[workloadId] ?: 0.0
            if (refValue <= 0.0) { exclusions += ScoreExclusion(workloadId, "reference not frozen"); continue }
            val measured = (run.payload as? BenchmarkPayload.Scalar)?.summary?.median
                ?: run { Double.NaN }
            if (!measured.isFinite() || measured <= 0.0) { exclusions += ScoreExclusion(workloadId, "no valid measurement"); continue }

            val passed = run.correctness.passed
            val stability = run.validity.stability
            val eligible = passed && (stability == ValidityLevel.STABLE || stability == ValidityLevel.VARIABLE)
            val ratio = (measured / refValue).coerceIn(pack.clampMin, pack.clampMax)
            val normalized = pack.itemBase * ratio
            val reason = when {
                !passed -> "correctness failed"
                stability == ValidityLevel.INVALID -> "INVALID"
                stability == ValidityLevel.RETEST_RECOMMENDED -> "RETEST_RECOMMENDED"
                else -> null
            }
            workloadScores += WorkloadScore(
                workloadId = workloadId,
                workloadVersion = run.identity.workloadVersion,
                measuredValue = measured,
                referenceValue = refValue,
                normalizedScore = normalized,
                weight = ref.weight,
                eligible = eligible,
                exclusionReason = reason,
            )
        }

        val categoryScores = mutableMapOf<String, Double>()
        var retestBlocked = false
        for ((cat, spec) in pack.categories) {
            val catScores = workloadScores.filter { it.workloadId in spec.workloads.keys }
            if (catScores.any { it.exclusionReason == "RETEST_RECOMMENDED" }) retestBlocked = true
            val eligible = catScores.filter { it.eligible }
            if (eligible.isEmpty()) continue
            val totalW = eligible.sumOf { spec.workloads.getValue(it.workloadId).weight }
            val geo = exp(eligible.sumOf { (spec.workloads.getValue(it.workloadId).weight / totalW) * ln(it.normalizedScore) })
            categoryScores[cat] = geo
        }

        val allPresent = pack.overallWeights.keys.all { categoryScores.containsKey(it) }
        val overall = if (allPresent && !retestBlocked) {
            val geo = exp(categoryScores.entries.sumOf { (cat, s) -> pack.overallWeights.getValue(cat) * ln(s) })
            Math.round(geo * pack.multiplier).toInt()
        } else null

        val eligibleCount = workloadScores.count { it.eligible }
        val total = workloadScores.size
        val hasVariable = workloadScores.any { it.eligible && it.exclusionReason == null && it.workloadId.let { id -> latest[id]?.validity?.stability == ValidityLevel.VARIABLE } }
        val confidence = when {
            eligibleCount == 0 -> ScoreConfidence("LOW", 0, total)
            hasVariable -> ScoreConfidence("MEDIUM", eligibleCount, total)
            else -> ScoreConfidence("HIGH", eligibleCount, total)
        }

        return ScoreReport(
            sessionId = sessionId,
            scoreVersion = pack.scoreVersion,
            referencePackVersion = pack.referencePackVersion,
            overallScore = overall,
            cpuScore = Math.round(categoryScores["CPU"] ?: 0.0).toInt().let { if (categoryScores.containsKey("CPU")) it else null },
            gpuScore = Math.round(categoryScores["GPU"] ?: 0.0).toInt().let { if (categoryScores.containsKey("GPU")) it else null },
            memoryScore = Math.round(categoryScores["Memory"] ?: 0.0).toInt().let { if (categoryScores.containsKey("Memory")) it else null },
            appIoScore = Math.round(categoryScores["AppIO"] ?: 0.0).toInt().let { if (categoryScores.containsKey("AppIO")) it else null },
            confidence = confidence,
            coveragePercent = if (total > 0) eligibleCount * 100.0 / total else 0.0,
            exclusions = exclusions,
            workloadScores = workloadScores,
        )
    }

    private fun ScorePack.flatWorkloads(): List<Pair<String, WorkloadRef>> =
        categories.values.flatMap { it.workloads.entries }.map { it.key to it.value }
}
