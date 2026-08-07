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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.ScoreReport
import com.siliconverity.core.benchmark.payloadSummaryMedian
import com.siliconverity.core.benchmark.primaryMetric
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvTime
import com.siliconverity.core.designsystem.SvWorkloads
import com.siliconverity.core.designsystem.R as SvR

/**
 * 会话级对比: 选择两个完整会话 (A/B), 对比综合分/分类分/逐项成绩。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    vm: CompareViewModel,
    onBack: () -> Unit,
) {
    val sessions by vm.sessions.collectAsState()
    val sel = vm.selected

    LaunchedEffect(Unit) { vm.load() }

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
            if (sessions.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            items(sessions, key = { it.id }) { session ->
                val idx = sel.indexOf(session.id)
                SessionSelectRow(session, selectedIdx = idx) { vm.toggle(session.id) }
            }

            val a = sessions.firstOrNull { it.id == sel.getOrNull(0) }
            val b = sessions.firstOrNull { it.id == sel.getOrNull(1) }
            if (a != null && b != null) {
                item { Spacer(Modifier.height(SvSpacing.Md)); SessionComparisonPanel(a, b) }
            }
        }
    }
}

@Composable
private fun SessionSelectRow(session: SessionAggregate, selectedIdx: Int, onClick: () -> Unit) {
    val tag = if (selectedIdx >= 0) (if (selectedIdx == 0) "A" else "B") else null
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (tag != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(SvSpacing.Md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    SvTime.formatIso(session.startedAt, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.history_items, session.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                session.score?.overallScore?.let { "%,d".format(it) } ?: stringResource(R.string.history_score_not_generated),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = if (session.score?.overallScore != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tag != null) {
                Spacer(Modifier.padding(start = SvSpacing.Sm))
                Text(tag, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SessionComparisonPanel(a: SessionAggregate, b: SessionAggregate) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Text(
                stringResource(R.string.compare_sessions, svTime(a.startedAt), svTime(b.startedAt)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(SvSpacing.Sm))

            // 综合分
            val aOverall = a.score?.overallScore
            val bOverall = b.score?.overallScore
            if (aOverall != null && bOverall != null) {
                DeltaRow(
                    label = stringResource(R.string.compare_overall),
                    aText = "%,d".format(aOverall),
                    bText = "%,d".format(bOverall),
                    delta = (bOverall - aOverall).toDouble() / aOverall * 100.0,
                )
            } else {
                Kv(stringResource(R.string.compare_overall), stringResource(R.string.history_score_not_generated))
            }

            // 分类分
            listOf(
                "CPU" to (a.score?.cpuScore to b.score?.cpuScore),
                "GPU" to (a.score?.gpuScore to b.score?.gpuScore),
                stringResource(R.string.history_cat_memory) to (a.score?.memoryScore to b.score?.memoryScore),
                stringResource(R.string.history_cat_io) to (a.score?.appIoScore to b.score?.appIoScore),
            ).forEach { (label, pair) ->
                val av = pair.first?.times(10)
                val bv = pair.second?.times(10)
                if (av != null && bv != null) {
                    DeltaRow(
                        label = label,
                        aText = "%,d".format(av),
                        bText = "%,d".format(bv),
                        delta = (bv - av).toDouble() / av * 100.0,
                    )
                } else {
                    Kv(label, "—")
                }
            }

            Spacer(Modifier.height(SvSpacing.Sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
            Spacer(Modifier.height(SvSpacing.Sm))

            // 逐项对比 (按 workloadId 对齐)
            val aByW = a.runs.associateBy { it.identity.workloadId }
            val bByW = b.runs.associateBy { it.identity.workloadId }
            val ids = (aByW.keys + bByW.keys).sorted()
            ids.forEach { w ->
                val ar = aByW[w]
                val br = bByW[w]
                if (ar != null && br != null && ar.payloadSummaryMedian() != 0.0) {
                    DeltaRow(
                        label = stringResource(SvWorkloads.nameRes(w)),
                        aText = ar.primaryMetric(),
                        bText = br.primaryMetric(),
                        delta = (br.payloadSummaryMedian() - ar.payloadSummaryMedian()) / ar.payloadSummaryMedian() * 100.0,
                        deltaSuffix = null,
                    )
                } else {
                    Kv(stringResource(SvWorkloads.nameRes(w)), "${ar?.primaryMetric() ?: "—"} / ${br?.primaryMetric() ?: "—"}")
                }
            }
        }
    }
}

@Composable
private fun DeltaRow(label: String, aText: String, bText: String, delta: Double, deltaSuffix: String? = "%") {
    val deltaColor = if (delta >= 0) SvColors.Accent else MaterialTheme.colorScheme.error
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A  $aText", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text("B  $bText", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Text(
            "${if (delta >= 0) "+" else ""}%.2f$deltaSuffix".format(delta),
            style = MaterialTheme.typography.bodySmall,
            color = deltaColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun svTime(iso: String): String =
    SvTime.formatIso(iso, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday))

@Composable
private fun Kv(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
