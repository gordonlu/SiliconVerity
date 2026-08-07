package com.siliconverity.benchmark

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.R
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.benchmark.LatencyPoint
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatencyScreen(
    state: LatencyUiState,
    onRun: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.latency_title), style = MaterialTheme.typography.labelLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.latency_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = SvSpacing.PageHorizontal, vertical = SvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
        ) {
            item {
                Text(stringResource(R.string.latency_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                val running = state is LatencyUiState.Running
                Button(onClick = onRun, enabled = !running, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text(if (running) stringResource(R.string.latency_measuring) else stringResource(R.string.latency_run), fontWeight = FontWeight.SemiBold)
                }
            }
            when (state) {
                is LatencyUiState.Idle -> item { Text(stringResource(R.string.latency_not_run), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                is LatencyUiState.Running -> item { Text(stringResource(R.string.latency_running), style = MaterialTheme.typography.bodyMedium) }
                is LatencyUiState.Error -> item { Text(stringResource(R.string.latency_error, state.message), color = MaterialTheme.colorScheme.error) }
                is LatencyUiState.Done -> {
                    item { LatencyCurve(state.points, Modifier.fillMaxWidth().height(180.dp)) }
                    items(state.points.size) { idx ->
                        val p = state.points[idx]
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatSize(p.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (p.latencyNs >= 0) stringResource(R.string.latency_ns, p.latencyNs) else "-", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatencyCurve(points: List<LatencyPoint>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val valid = points.filter { it.latencyNs >= 0 }
        if (valid.size < 2) return@Canvas
        val minLog = ln(points.first().sizeBytes.toDouble())
        val maxLog = ln(points.last().sizeBytes.toDouble())
        val logSpan = (maxLog - minLog).coerceAtLeast(1.0)
        val maxLat = valid.maxOf { it.latencyNs }.coerceAtLeast(1.0)
        val path = Path()
        valid.forEachIndexed { i, p ->
            val x = (((ln(p.sizeBytes.toDouble()) - minLog) / logSpan) * size.width).toFloat()
            val y = (size.height - (p.latencyNs / maxLat).toFloat() * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(muted, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        drawPath(path, accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / 1024 / 1024} MiB"
    bytes >= 1024 -> "${bytes / 1024} KiB"
    else -> "$bytes B"
}
