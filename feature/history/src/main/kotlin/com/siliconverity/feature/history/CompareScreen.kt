package com.siliconverity.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

            // 分类分: 双条图 + 数字
            Spacer(Modifier.height(SvSpacing.Sm))
            Text(stringResource(R.string.compare_categories), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            listOf(
                "CPU" to (a.score?.cpuScore to b.score?.cpuScore),
                "GPU" to (a.score?.gpuScore to b.score?.gpuScore),
                stringResource(R.string.history_cat_memory) to (a.score?.memoryScore to b.score?.memoryScore),
                stringResource(R.string.history_cat_io) to (a.score?.appIoScore to b.score?.appIoScore),
            ).forEach { (label, pair) ->
                val av = pair.first?.times(10) ?: 0
                val bv = pair.second?.times(10) ?: 0
                DualBarRow(
                    label = label,
                    aFraction = av / 20_000f,
                    bFraction = bv / 20_000f,
                    aText = if (pair.first != null) "%,d".format(av) else "—",
                    bText = if (pair.second != null) "%,d".format(bv) else "—",
                )
            }

            Spacer(Modifier.height(SvSpacing.Sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
            Spacer(Modifier.height(SvSpacing.Sm))

            // 逐项对比: 相对参考归一化双条图 (参考线 = 1.0)
            Text(stringResource(R.string.compare_workloads), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            val aByW = a.runs.associateBy { it.identity.workloadId }
            val bByW = b.runs.associateBy { it.identity.workloadId }
            val ids = (aByW.keys + bByW.keys).sorted()
            ids.forEach { w ->
                val ar = aByW[w]
                val br = bByW[w]
                val aMed = ar?.payloadSummaryMedian() ?: 0.0
                val bMed = br?.payloadSummaryMedian() ?: 0.0
                val ref = com.siliconverity.core.designsystem.SessionScorer.refValue(
                    androidx.compose.ui.platform.LocalContext.current,
                    w,
                ) ?: 0.0
                DualBarRow(
                    label = stringResource(SvWorkloads.nameRes(w)),
                    aFraction = if (ref > 0) (aMed / ref).toFloat() else 0f,
                    bFraction = if (ref > 0) (bMed / ref).toFloat() else 0f,
                    aText = ar?.primaryMetric() ?: "—",
                    bText = br?.primaryMetric() ?: "—",
                    referenceLine = true,
                )
            }
        }
    }
}

/** A/B 双条对比图: 上条 = A, 下条 = B; 可选参考线 (参考线=0.5 处, 即 fraction=1.0)。 */
@Composable
private fun DualBarRow(
    label: String,
    aFraction: Float,
    bFraction: Float,
    aText: String,
    bText: String,
    referenceLine: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text("A $aText  ·  B $bText", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small),
        ) {
            val trackWidth = maxWidth
            if (referenceLine) {
                // 参考线: 轨道中点 (fraction = 1.0)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .offset(x = trackWidth * 0.5f - 0.5.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.45f)
                    .fillMaxWidth(aFraction.coerceIn(0f, 2f) / 2f)
                    .align(Alignment.TopStart)
                    .background(CompareColors.A, MaterialTheme.shapes.small),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.45f)
                    .fillMaxWidth(bFraction.coerceIn(0f, 2f) / 2f)
                    .align(Alignment.BottomStart)
                    .background(CompareColors.B, MaterialTheme.shapes.small),
            )
        }
    }
}

/** A/B 对比色。 */
private object CompareColors {
    val A = androidx.compose.ui.graphics.Color(0xFF8AB4F8)
    val B = androidx.compose.ui.graphics.Color(0xFFD7AEFB)
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
            formatDelta(delta, deltaSuffix),
            style = MaterialTheme.typography.bodySmall,
            color = deltaColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** delta 展示格式化 (纯函数, 可单测; suffix 可空, 含 % 需转义)。 */
internal fun formatDelta(delta: Double, suffix: String?): String =
    "${if (delta >= 0) "+" else ""}%.2f${suffix?.replace("%", "%%") ?: ""}".format(delta)

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
