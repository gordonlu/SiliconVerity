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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.designsystem.SvSpacing

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpenRun: (String) -> Unit,
) {
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
            Text(
                "HISTORY",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = SvSpacing.Sm),
            )
        }
        when {
            state.loading -> item { CircularProgressIndicator() }
            state.runs.isEmpty() -> item {
                Text("尚无运行记录。运行一次 benchmark 后此处显示历史。", style = MaterialTheme.typography.bodyMedium)
            }
            else -> items(state.runs, key = { it.runId }) { m ->
                RunRow(m, onOpenRun)
            }
        }
    }
}

@Composable
private fun RunRow(manifest: RunManifest, onOpenRun: (String) -> Unit) {
    androidx.compose.material3.Surface(
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
                "CV %.4f  •  %s".format(manifest.cv, manifest.startedAt.ifEmpty { manifest.runId.take(8) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValidityChip(level: ValidityLevel) {
    val color = when (level) {
        ValidityLevel.CLEAN -> MaterialTheme.colorScheme.primary
        ValidityLevel.ACCEPTABLE -> MaterialTheme.colorScheme.tertiary
        ValidityLevel.NOISY -> MaterialTheme.colorScheme.error
        ValidityLevel.INVALID -> MaterialTheme.colorScheme.error
    }
    Text(
        level.name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}
