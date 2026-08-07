package com.siliconverity.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

private data class Session(val id: String, val header: String, val runs: List<BenchmarkRun>)

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpenRun: (String) -> Unit,
    onClear: () -> Unit,
    onCompare: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onRefresh() }
    val sessions = remember(state.runs) { groupSessions(state.runs) }

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
                sessions.forEach { session ->
                    item(key = "h-${session.id}") {
                        Column(modifier = Modifier.padding(top = SvSpacing.Sm, bottom = SvSpacing.Xs)) {
                            Text(
                                SvTime.formatIso(session.header, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday)),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.history_items, session.runs.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(session.runs, key = { "r-${it.identity.runId}" }) { run ->
                        RunRow(run, onOpenRun)
                    }
                }
            }
        }
    }
}

private fun groupSessions(runs: List<BenchmarkRun>): List<Session> {
    val groups = LinkedHashMap<String, MutableList<BenchmarkRun>>()
    for (run in runs) {
        val key = run.identity.sessionId.ifEmpty { run.identity.runId }
        groups.getOrPut(key) { mutableListOf() }.add(run)
    }
    return groups.map { (id, groupedRuns) ->
        Session(
            id = id,
            header = groupedRuns.first().startedAt.ifEmpty { id.take(8) },
            runs = groupedRuns,
        )
    }
}

@Composable
private fun RunRow(run: BenchmarkRun, onOpenRun: (String) -> Unit) {
    Surface(
        onClick = { onOpenRun(run.identity.runId) },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    run.identity.workloadId,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ValidityChip(run.validity.stability)
            }
            Text(
                run.primaryMetric(),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                stringResource(R.string.history_cv, run.validity.robustCv),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValidityChip(level: ValidityLevel) {
    val color = when (level) {
        ValidityLevel.STABLE -> MaterialTheme.colorScheme.primary
        ValidityLevel.VARIABLE -> MaterialTheme.colorScheme.tertiary
        ValidityLevel.RETEST_RECOMMENDED -> MaterialTheme.colorScheme.error
        ValidityLevel.INVALID -> MaterialTheme.colorScheme.error
    }
    Text(
        validityLabel(level),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun validityLabel(level: ValidityLevel): String = when (level) {
    ValidityLevel.STABLE -> stringResource(SvR.string.sv_validity_stable)
    ValidityLevel.VARIABLE -> stringResource(SvR.string.sv_validity_variable)
    ValidityLevel.RETEST_RECOMMENDED -> stringResource(SvR.string.sv_validity_retest)
    ValidityLevel.INVALID -> stringResource(SvR.string.sv_validity_invalid)
}
