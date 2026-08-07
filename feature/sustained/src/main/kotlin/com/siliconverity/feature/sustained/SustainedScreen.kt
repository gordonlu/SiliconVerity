package com.siliconverity.feature.sustained

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.SustainedSample
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SustainedScreen(
    state: SustainedUiState,
    onStart: (durationSec: Int) -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var durationMin by remember { mutableIntStateOf(1) }
    val backAction = {
        if (state is SustainedUiState.Running) onStop()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sustained_title), style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = backAction) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.sustained_back))
                    }
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
                Text(stringResource(R.string.sustained_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when (state) {
                is SustainedUiState.Idle -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
                            listOf(1, 5, 10).forEach { m ->
                                val selected = durationMin == m
                                OutlinedButton(
                                    onClick = { durationMin = m },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        contentColor = if (selected) SvColors.Background else MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) { Text(stringResource(R.string.sustained_min, m)) }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { onStart(durationMin * 60) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = MaterialTheme.shapes.small,
                        ) { Text(stringResource(R.string.sustained_start, durationMin), fontWeight = FontWeight.SemiBold) }
                    }
                }

                is SustainedUiState.Running -> {
                    val p = state.progress
                    item {
                        val fraction = (p.elapsedSec / p.durationSec).coerceIn(0.0, 1.0).toFloat()
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Text(stringResource(R.string.sustained_elapsed, p.elapsedSec, p.durationSec), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.sustained_now, p.currentThroughput / 1_000_000.0), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                        Text(stringResource(R.string.sustained_thermal, p.thermalStatus), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { ThroughputCurve(p.samples, Modifier.fillMaxWidth().height(160.dp)) }
                    item {
                        Button(onClick = onStop, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.sustained_stop)) }
                    }
                }

                is SustainedUiState.Done -> {
                    val r = state.result
                    item {
                        Text(stringResource(R.string.sustained_retention), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "%.1f%%".format(r.retention * 100),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (r.retention >= 0.8) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    item {
                        Text(stringResource(R.string.sustained_initial, r.initialMedian / 1_000_000.0), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        Text(stringResource(R.string.sustained_stable, r.stableMedian / 1_000_000.0), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        Text(stringResource(R.string.sustained_worst, r.worstStableWindow / 1_000_000.0), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        Text(stringResource(R.string.sustained_t90, r.timeTo90Percent, r.timeTo80Percent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.sustained_total_work, r.absoluteWorkCompleted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.sustained_duration, r.durationSec, r.workloadId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.sustained_thermal_range, r.thermalStatusStart, r.thermalStatusEnd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.savedPath?.let { Text(stringResource(R.string.sustained_saved, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    item { ThroughputCurve(r.samples, Modifier.fillMaxWidth().height(180.dp)) }
                    item {
                        Button(onClick = onReset, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.sustained_done)) }
                    }
                }

                is SustainedUiState.Error -> {
                    item { Text(stringResource(R.string.sustained_error, state.message), color = MaterialTheme.colorScheme.error) }
                    item { Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sustained_back)) } }
                }
            }
        }
    }
}

@Composable
private fun ThroughputCurve(samples: List<SustainedSample>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val maxT = samples.maxOf { it.throughput }.coerceAtLeast(1.0)
        val n = samples.size
        val dx = if (n > 1) size.width / (n - 1) else size.width
        val path = Path()
        samples.forEachIndexed { i, s ->
            val x = i * dx
            val y = size.height - (s.throughput / maxT).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(color = muted, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1f)
        drawPath(path = path, color = accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}
