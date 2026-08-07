package com.siliconverity.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvPanel
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.model.HardwareFact

@Composable
fun HomeScreen(
    hardwareFacts: List<HardwareFact>,
    lastRun: RunManifest?,
    benchmarkState: BenchmarkUiState,
    onStartBenchmark: () -> Unit,
    onOpenHardware: () -> Unit,
    onOpenSustained: () -> Unit,
    onOpenGpu: () -> Unit,
    onOpenLatency: () -> Unit,
    onOpenRun: (String) -> Unit,
) {
    val deviceId = rememberDeviceId()
    val running = benchmarkState is BenchmarkUiState.Running

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(
            start = SvSpacing.PageHorizontal,
            end = SvSpacing.PageHorizontal,
            top = SvSpacing.Md,
            bottom = SvSpacing.Md,
        ),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.Md),
    ) {
        item { BrandRow(deviceId, onOpenHardware) }
        item { HeroTitle(running) }
        item { MetricMatrix(hardwareFacts) }
        item { LastRun(lastRun, onOpenRun) }
        val score = (benchmarkState as? BenchmarkUiState.Done)?.score
        if (score != null) {
            item { ScoreCard(score) }
        }
        item {
            PrimaryCta(running = running, onClick = onStartBenchmark)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
                OutlinedButton(
                    onClick = onOpenSustained,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text("SUSTAINED", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onOpenGpu,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text("GPU", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onOpenLatency,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text("LATENCY", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun BrandRow(deviceId: String, onOpenHardware: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column {
            Text("芯鉴", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("SiliconVerity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
            onClick = onOpenHardware,
        ) {
            Column(modifier = Modifier.padding(horizontal = SvSpacing.Sm, vertical = SvSpacing.Xs)) {
                Text("DEVICE ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(deviceId, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HeroTitle(running: Boolean) {
    val status = if (running) "MEASURING" else "READY"
    val statusColor = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column {
        Text("SILICON", fontSize = 58.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
        Text("VERITY", fontSize = 58.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
        Spacer(Modifier.height(SvSpacing.Xs))
        Text("VERIFY HARDWARE. TRUST PERFORMANCE.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(SvSpacing.Sm))
        Text(status, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = statusColor)
        if (!running) {
            Text("FOR BENCHMARK", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricMatrix(facts: List<HardwareFact>) {
    val cpu = factValue(facts, "cpu.cores.configured") ?: "—"
    val mem = bytesToGb(factValue(facts, "memory.availMem"))
    val storage = bytesToGb(factValue(facts, "storage.fs.total"))
    val thermal = factValue(facts, "thermal.status") ?: "—"
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(modifier = Modifier.weight(1f), label = "CPU", value = cpu, unit = "CORES")
                VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
                MetricCell(modifier = Modifier.weight(1f), label = "MEMORY", value = mem, unit = "AVAILABLE")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(modifier = Modifier.weight(1f), label = "STORAGE", value = storage, unit = "TOTAL")
                VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
                MetricCell(modifier = Modifier.weight(1f), label = "THERMAL", value = thermal, unit = "STATUS")
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MetricCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
) {
    Column(modifier = modifier.padding(SvSpacing.Md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LastRun(lastRun: RunManifest?, onOpenRun: (String) -> Unit) {
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        if (lastRun == null) {
            Column(modifier = Modifier.padding(SvSpacing.Md)) {
                Text("LAST RUN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("NO OFFICIAL SCORE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Alpha 阶段不提供正式总分", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                onClick = { onOpenRun(lastRun.runId) },
            ) {
                Column(modifier = Modifier.padding(SvSpacing.Md)) {
                    Text("LAST RUN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%.2f %s".format(
                                WorkloadFormat.scale(lastRun.workloadId, lastRun.median),
                                WorkloadFormat.unit(lastRun.workloadId),
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                        ValidityChip(lastRun.validityLevel)
                    }
                    Text(
                        "${lastRun.workloadId}  •  ${lastRun.startedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
    Text(level.name, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PrimaryCta(running: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !running,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = SvColors.Background,
        ),
    ) {
        Text(
            if (running) "MEASURING..." else "START BENCHMARK",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ScoreCard(score: com.siliconverity.core.benchmark.ScoreReport) {
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Text("SV PERFORMANCE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(SvSpacing.Xs))
            score.overallScore?.let {
                Text("%,d".format(it), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            } ?: Text("综合分未生成（有 RETEST/INVALID 或分类缺失）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(
                "评分 ${score.scoreVersion}  •  参考 ${score.referencePackVersion}  •  可信度 ${score.confidence.level}  •  覆盖 %.0f%%".format(score.coveragePercent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SvSpacing.Sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CPU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(score.cpuScore?.let { "%,d".format(it) } ?: "—", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GPU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(score.gpuScore?.let { "%,d".format(it) } ?: "—", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("内存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(score.memoryScore?.let { "%,d".format(it) } ?: "—", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("应用 I/O", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(score.appIoScore?.let { "%,d".format(it) } ?: "—", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun factValue(facts: List<HardwareFact>, key: String): String? =
    facts.firstOrNull { it.key == key }?.rawValue

private fun bytesToGb(bytes: String?): String {
    val b = bytes?.toLongOrNull() ?: return "—"
    return "%.1f GB".format(b / 1_000_000_000.0)
}
