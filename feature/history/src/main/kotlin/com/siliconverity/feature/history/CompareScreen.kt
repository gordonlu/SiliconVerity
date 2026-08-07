package com.siliconverity.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.payloadSummaryMedian
import com.siliconverity.core.benchmark.primaryMetric
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvTime
import com.siliconverity.core.designsystem.SvWorkloads
import com.siliconverity.core.designsystem.R as SvR

/**
 * 对比页: 按 workload 分组, 选择同一项目的两次运行对比 A/B。
 * 不同项目不可对比 (提示选择同一项目)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    vm: CompareViewModel,
    onBack: () -> Unit,
) {
    val runs by vm.runs.collectAsState()
    val sel = vm.selected
    val groups = remember(runs) {
        runs.groupBy { it.identity.workloadId }.toList().sortedByDescending { (_, list) -> list.maxOf { it.startedAt } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_compare_count, sel.size), style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.history_back)) }
                },
                actions = {
                    if (sel.isNotEmpty()) TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.history_clear)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = SvSpacing.PageHorizontal, vertical = SvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
        ) {
            item {
                Text(
                    stringResource(R.string.compare_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (runs.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium) }
            }

            // 按 workload 分组, 组内按时间倒序
            groups.forEach { (workloadId, groupRuns) ->
                item(key = "g-$workloadId") {
                    Text(
                        stringResource(SvWorkloads.nameRes(workloadId)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(groupRuns, key = { "r-${it.identity.runId}" }) { run ->
                    val idx = sel.indexOf(run.identity.runId)
                    RunSelectRow(run, selectedIdx = idx, sameWorkload = selectedWorkloadMatches(sel, runs, run.identity.workloadId)) {
                        vm.toggle(run.identity.runId)
                    }
                }
            }

            val a = runs.firstOrNull { it.identity.runId == sel.getOrNull(0) }
            val b = runs.firstOrNull { it.identity.runId == sel.getOrNull(1) }
            if (a != null && b != null) {
                item { Spacer(Modifier.height(SvSpacing.Md)); ComparisonPanel(a, b) }
            }
        }
    }
}

private fun selectedWorkloadMatches(sel: List<String>, runs: List<BenchmarkRun>, workloadId: String): Boolean =
    sel.isNotEmpty() && sel.size == 1 && runs.firstOrNull { it.identity.runId == sel.first() }?.identity?.workloadId == workloadId

@Composable
private fun RunSelectRow(run: BenchmarkRun, selectedIdx: Int, sameWorkload: Boolean, onClick: () -> Unit) {
    val tag = if (selectedIdx >= 0) (if (selectedIdx == 0) "A" else "B") else null
    val rowColor = when {
        tag != null -> MaterialTheme.colorScheme.surfaceVariant
        sameWorkload -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = rowColor,
        border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(SvSpacing.Md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column {
                Text(run.primaryMetric(), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Text(
                    SvTime.formatIso(run.startedAt, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    validityLabel(run.validity.stability),
                    style = MaterialTheme.typography.labelSmall,
                    color = validityColor(run.validity.stability),
                    fontWeight = FontWeight.SemiBold,
                )
                if (tag != null) {
                    Spacer(Modifier.padding(start = SvSpacing.Sm))
                    Text(tag, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ComparisonPanel(a: BenchmarkRun, b: BenchmarkRun) {
    val aW = a.identity.workloadId
    val bW = b.identity.workloadId
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            if (aW != bW) {
                Text(
                    stringResource(R.string.compare_mismatch),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(SvSpacing.Sm))
            }
            Kv(stringResource(R.string.history_compare_a, aW), a.primaryMetric())
            Kv(stringResource(R.string.history_compare_b, bW), b.primaryMetric())
            if (aW == bW) {
                val aVal = a.payloadSummaryMedian()
                val bVal = b.payloadSummaryMedian()
                val delta = if (aVal != 0.0) (bVal - aVal) / aVal * 100.0 else 0.0
                val deltaColor = if (delta >= 0) SvColors.Accent else MaterialTheme.colorScheme.error
                Text(
                    stringResource(R.string.history_delta_median, delta),
                    style = MaterialTheme.typography.titleMedium,
                    color = deltaColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Kv(stringResource(R.string.history_cv_ab), "%.4f / %.4f".format(a.validity.robustCv, b.validity.robustCv))
                Kv(
                    stringResource(R.string.history_validity_ab),
                    "${validityLabel(a.validity.stability)} / ${validityLabel(b.validity.stability)}",
                )
                Kv(
                    stringResource(R.string.history_correctness_ab),
                    "${if (a.correctness.passed) "OK" else "FAIL"} / ${if (b.correctness.passed) "OK" else "FAIL"}",
                )
                Kv(
                    stringResource(R.string.history_thermal_ab),
                    "${a.environment.thermalStatusStart} / ${b.environment.thermalStatusStart}",
                )
            }
        }
    }
}

@Composable
private fun validityLabel(level: ValidityLevel): String = when (level) {
    ValidityLevel.STABLE -> stringResource(SvR.string.sv_validity_stable)
    ValidityLevel.VARIABLE -> stringResource(SvR.string.sv_validity_variable)
    ValidityLevel.RETEST_RECOMMENDED -> stringResource(SvR.string.sv_validity_retest)
    ValidityLevel.INVALID -> stringResource(SvR.string.sv_validity_invalid)
}

@Composable
private fun validityColor(level: ValidityLevel) = when (level) {
    ValidityLevel.STABLE -> MaterialTheme.colorScheme.primary
    ValidityLevel.VARIABLE -> MaterialTheme.colorScheme.tertiary
    ValidityLevel.RETEST_RECOMMENDED, ValidityLevel.INVALID -> MaterialTheme.colorScheme.error
}

@Composable
private fun Kv(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
