package com.siliconverity.feature.sustained

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.SustainedSample
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvSpacing

@Composable
fun SustainedScreen(
    state: SustainedUiState,
    onStart: (durationSec: Int) -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    var durationMin by remember { mutableIntStateOf(1) }

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
            Text("SUSTAINED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "持续 CPU 负载, 观察热衰减与性能保持率",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                            ) { Text("$m min") }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { onStart(durationMin * 60) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.small,
                    ) { Text("START SUSTAINED ($durationMin min)", fontWeight = FontWeight.SemiBold) }
                }
            }

            is SustainedUiState.Running -> {
                val p = state.progress
                item {
                    val fraction = (p.elapsedSec / p.durationSec).coerceIn(0.0, 1.0).toFloat()
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("elapsed %.0f / %d s".format(p.elapsedSec, p.durationSec), style = MaterialTheme.typography.bodyMedium)
                    Text("now  %.2f M ops/s".format(p.currentThroughput / 1_000_000.0), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    Text("thermal ${p.thermalStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { ThroughputCurve(p.samples, Modifier.fillMaxWidth().height(160.dp)) }
                item {
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) { Text("STOP") }
                }
            }

            is SustainedUiState.Done -> {
                val r = state.result
                item {
                    Text("retention", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "%.1f%%".format(r.retention * 100),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (r.retention >= 0.8) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                item {
                    Text("initial  %.2f M ops/s".format(r.initialMedian / 1_000_000.0), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    Text("stable   %.2f M ops/s".format(r.stableMedian / 1_000_000.0), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    Text("duration ${r.durationSec}s, workload ${r.workloadId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("thermal ${r.thermalStatusStart} -> ${r.thermalStatusEnd}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.savedPath?.let { Text("saved: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
                item { ThroughputCurve(r.samples, Modifier.fillMaxWidth().height(180.dp)) }
                item {
                    Button(onClick = onReset, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) { Text("完成") }
                }
            }

            is SustainedUiState.Error -> {
                item { Text("出错: ${state.message}", color = MaterialTheme.colorScheme.error) }
                item { Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("返回") } }
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
        drawLine(
            color = muted,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f,
        )
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
}
