package com.siliconverity.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.primaryMetric
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvTime
import com.siliconverity.core.designsystem.R as SvR

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpenRun: (String) -> Unit,
    onClear: () -> Unit,
    onCompare: () -> Unit,
    onRefresh: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
) {
    LaunchedEffect(Unit) { onRefresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(
            start = SvSpacing.PageHorizontal,
            end = SvSpacing.PageHorizontal,
            top = SvSpacing.Md,
            bottom = SvSpacing.Md,
        ),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCompare, enabled = !state.loading && state.runs.isNotEmpty()) { Text(stringResource(R.string.history_compare)) }
                TextButton(onClick = onClear, enabled = !state.loading && state.runs.isNotEmpty()) { Text(stringResource(R.string.history_clear)) }
            }
        }
        when {
            state.loading && state.runs.isEmpty() -> item { CircularProgressIndicator() }
            state.runs.isEmpty() -> item {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                items(state.sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        expanded = remember(session.id) { mutableStateOf(false) },
                        onOpenSession = { onOpenSession(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionAggregate,
    expanded: androidx.compose.runtime.MutableState<Boolean>,
    onOpenSession: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SvTime.formatIso(session.startedAt, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.history_items, session.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            session.score?.overallScore?.let { overall ->
                Spacer(Modifier.height(SvSpacing.Xs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%,d".format(overall),
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.history_sv_performance),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val catLine = listOfNotNull(
                    session.score?.cpuScore?.let { "CPU %1$,d".format(it * 10) },
                    session.score?.gpuScore?.let { "GPU %1$,d".format(it * 10) },
                    session.score?.memoryScore?.let { "${stringResource(R.string.history_cat_memory)} %1$,d".format(it * 10) },
                    session.score?.appIoScore?.let { "${stringResource(R.string.history_cat_io)} %1$,d".format(it * 10) },
                ).joinToString(" · ")
                if (catLine.isNotEmpty()) {
                    Text(catLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } ?: Text(
                stringResource(R.string.history_score_not_generated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(SvSpacing.Xs))
            Text(
                stringResource(
                    R.string.history_validity_summary,
                    session.stableCount,
                    session.total,
                    session.variableCount,
                    session.total,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expanded.value) {
                Spacer(Modifier.height(SvSpacing.Xs))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
                Spacer(Modifier.height(SvSpacing.Xs))
                session.runs.forEach { run ->
                    RunRow(run, onOpenRun = { onOpenSession() })
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
                TextButton(onClick = { expanded.value = !expanded.value }) {
                    Text(if (expanded.value) stringResource(R.string.history_collapse) else stringResource(R.string.history_expand))
                }
                TextButton(onClick = onOpenSession) {
                    Text(stringResource(R.string.history_open_result))
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: BenchmarkRun, onOpenRun: () -> Unit) {
    Surface(
        onClick = onOpenRun,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = SvSpacing.Sm, vertical = SvSpacing.Sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(run.identity.workloadId, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Text(run.primaryMetric(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Text(
                validityLabel(run.validity.stability),
                style = MaterialTheme.typography.labelSmall,
                color = validityColor(run.validity.stability),
                fontWeight = FontWeight.SemiBold,
            )
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
