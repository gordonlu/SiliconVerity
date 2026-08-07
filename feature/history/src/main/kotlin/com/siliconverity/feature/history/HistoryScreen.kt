package com.siliconverity.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.designsystem.SvSpacing

private data class Session(val id: String, val header: String, val runs: List<RunManifest>)

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpenRun: (String) -> Unit,
    onClear: () -> Unit,
    onCompare: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                Text("HISTORY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCompare, enabled = !state.loading && state.runs.isNotEmpty()) { Text("对比") }
                TextButton(onClick = onClear, enabled = !state.loading && state.runs.isNotEmpty()) { Text("清空") }
            }
        }
        when {
            state.loading -> item { CircularProgressIndicator() }
            state.runs.isEmpty() -> item {
                Text("尚无运行记录。", style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                val sessions = groupSessions(state.runs)
                sessions.forEach { session ->
                    item(key = "h-${session.id}") {
                        Column(modifier = Modifier.padding(top = SvSpacing.Sm, bottom = SvSpacing.Xs)) {
                            Text(
                                session.header,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${session.runs.size} 项",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(session.runs, key = { "r-${it.runId}" }) { m ->
                        RunRow(m, onOpenRun)
                    }
                }
            }
        }
    }
}

private fun groupSessions(runs: List<RunManifest>): List<Session> {
    val sessions = mutableListOf<Session>()
    for (run in runs) {
        val key = run.sessionId.ifEmpty { run.runId }
        if (sessions.isNotEmpty() && sessions.last().id == key) {
            sessions[sessions.lastIndex] = sessions.last().let { Session(it.id, it.header, it.runs + run) }
        } else {
            val header = run.startedAt.ifEmpty { key.take(8) }
            sessions += Session(key, header, listOf(run))
        }
    }
    return sessions
}

@Composable
private fun RunRow(manifest: RunManifest, onOpenRun: (String) -> Unit) {
    Surface(
        onClick = { onOpenRun(manifest.runId) },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    manifest.workloadId,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ValidityChip(manifest.validityLevel)
            }
            Text(
                "%.2f %s".format(
                    WorkloadFormat.scale(manifest.workloadId, manifest.median),
                    WorkloadFormat.unit(manifest.workloadId),
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "CV %.4f".format(manifest.cv),
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
        level.name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}
