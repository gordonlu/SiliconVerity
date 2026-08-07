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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvPanel
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvThermalStatus
import com.siliconverity.core.designsystem.SvTime
import com.siliconverity.core.designsystem.R as SvR
import com.siliconverity.core.model.HardwareFact

@Composable
fun HomeScreen(
    hardwareFacts: List<HardwareFact>,
    lastRun: RunManifest?,
    benchmarkState: BenchmarkUiState,
    onStartBenchmark: () -> Unit,
    onStopBenchmark: () -> Unit,
    onOpenHardware: () -> Unit,
    onOpenSustained: () -> Unit,
    onOpenGpu: () -> Unit,
    onOpenLatency: () -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenResult: () -> Unit = {},
    lastSessionScore: com.siliconverity.core.benchmark.ScoreReport? = null,
    lastSessionStartedAt: String? = null,
) {
    when (val state = benchmarkState) {
        is BenchmarkUiState.Running -> {
            SessionScreen(state = state, onStop = onStopBenchmark)
            return
        }
        BenchmarkUiState.Scoring -> {
            CalculatingScreen()
            return
        }
        else -> {}
    }
    val deviceId = rememberDeviceId()
    val running = false

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
        item {
            PrimaryCta(running = running, onClick = onStartBenchmark)
        }
        item { MetricMatrix(hardwareFacts) }
        val done = benchmarkState as? BenchmarkUiState.Done
        val score = done?.score ?: lastSessionScore
        if (score != null) {
            item {
                LastScoreCard(
                    score = score,
                    sessionStartedAt = done?.sessionStartedAt ?: lastSessionStartedAt,
                    onOpenResult = onOpenResult,
                )
            }
        } else {
            item { LastRun(lastRun, onOpenRun) }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
                OutlinedButton(
                    onClick = onOpenSustained,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text(stringResource(R.string.home_sustained), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                OutlinedButton(
                    onClick = onOpenGpu,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text(stringResource(R.string.home_gpu), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                OutlinedButton(
                    onClick = onOpenLatency,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text(stringResource(R.string.home_latency), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** 评分计算过渡页 (静态, 短暂)。 */
@Composable
private fun CalculatingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(SvSpacing.PageHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.result_calculating),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LastScoreCard(
    score: com.siliconverity.core.benchmark.ScoreReport,
    sessionStartedAt: String?,
    onOpenResult: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        onClick = onOpenResult,
        border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Text(stringResource(R.string.home_last_score), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(SvSpacing.Xs))
            score.overallScore?.let {
                Text("%,d".format(it), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            } ?: Text(stringResource(R.string.home_score_not_generated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            val catLine = listOfNotNull(
                score.cpuScore?.let { "CPU %1$,d".format(it * 10) },
                score.gpuScore?.let { "GPU %1$,d".format(it * 10) },
                score.memoryScore?.let { "${stringResource(R.string.home_cat_memory)} %1$,d".format(it * 10) },
                score.appIoScore?.let { "${stringResource(R.string.home_cat_io)} %1$,d".format(it * 10) },
            ).joinToString(" · ")
            if (catLine.isNotEmpty()) {
                Text(catLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val date = sessionStartedAt?.let {
                SvTime.formatIso(it, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday))
            } ?: ""
            Text(
                stringResource(R.string.home_last_score_line, score.overallScore ?: 0, "$date · ${stringResource(SvR.string.sv_validity_stable)}"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BrandRow(deviceId: String, onOpenHardware: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column {
            Text(stringResource(R.string.home_brand), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("SiliconVerity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
            onClick = onOpenHardware,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SvSpacing.Sm, vertical = SvSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.home_device_id), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(SvSpacing.Xs))
                Text(deviceId, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HeroTitle(running: Boolean) {
    val status = if (running) stringResource(R.string.home_status_measuring) else stringResource(R.string.home_status_ready)
    val statusColor = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column {
        Text(stringResource(R.string.home_hero_silicon), fontSize = 50.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
        Text(stringResource(R.string.home_hero_verity), fontSize = 50.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
        Spacer(Modifier.height(SvSpacing.Xs))
        Text(stringResource(R.string.home_hero_tagline), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(SvSpacing.Xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(status, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = statusColor)
            if (!running) {
                Spacer(Modifier.width(SvSpacing.Sm))
                Text(
                    stringResource(R.string.home_for_benchmark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricMatrix(facts: List<HardwareFact>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val zh = java.util.Locale.getDefault().language.startsWith("zh")
    val cpuSoc = factValue(facts, "soc.model")?.let {
        com.siliconverity.core.hardware.SocNameResolver.displayName(context, it, zh)
    } ?: "—"
    val cpuCores = factValue(facts, "cpu.cores.configured") ?: "—"
    val memTotalBytes = factValue(facts, "memory.totalMem")?.toLongOrNull()
    val memAvailBytes = factValue(facts, "memory.availMem")?.toLongOrNull()
    val memFree = if (memTotalBytes != null && memAvailBytes != null && memTotalBytes > 0) {
        "%.0f%%".format(memAvailBytes * 100.0 / memTotalBytes)
    } else "—"
    val memTotal = bytesHuman(memTotalBytes?.toString())
    val storageTotal = bytesHuman(factValue(facts, "storage.fs.total"))
    val storageAvail = bytesHuman(factValue(facts, "storage.fs.available"))
    val thermal = factValue(facts, "thermal.status") ?: "—"
    val thermalShort = if (thermal != "—") SvThermalStatus.short(thermal) else "—"
    val thermalDetail = if (thermal != "—") SvThermalStatus.detail(thermal) else null
    val batteryTemp = factValue(facts, "battery.temperature")?.toDoubleOrNull()?.let { "%.1f°C".format(it) } ?: "—"
    val batteryLevel = factValue(facts, "battery.level")?.let { "$it%" } ?: "—"
    val android = factValue(facts, "device.android_version") ?: "—"
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(modifier = Modifier.weight(1f), label = stringResource(R.string.home_metric_cpu), value = cpuSoc, unit = stringResource(R.string.home_unit_soc), sub = "$cpuCores ${stringResource(R.string.home_unit_cores)}")
                VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
                MetricCell(modifier = Modifier.weight(1f), label = stringResource(R.string.home_metric_memory), value = memFree, unit = stringResource(R.string.home_unit_free), sub = "$memTotal ${stringResource(R.string.home_unit_total)}")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(modifier = Modifier.weight(1f), label = stringResource(R.string.home_metric_storage), value = storageTotal, unit = stringResource(R.string.home_unit_total), sub = "$storageAvail ${stringResource(R.string.home_unit_free)}")
                VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
                MetricCell(modifier = Modifier.weight(1f), label = stringResource(R.string.home_metric_thermal), value = thermalShort, unit = stringResource(R.string.home_unit_status), sub = thermalDetail)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(modifier = Modifier.weight(1f), label = stringResource(R.string.home_metric_battery), value = batteryTemp, unit = stringResource(R.string.home_unit_temp), sub = "$batteryLevel ${stringResource(R.string.home_unit_chg)}")
                VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)
                MetricCell(modifier = Modifier.weight(1f), label = stringResource(R.string.home_metric_system), value = android, unit = stringResource(R.string.home_unit_android))
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
    sub: String? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = SvSpacing.Md, vertical = SvSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sub?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LastRun(lastRun: RunManifest?, onOpenRun: (String) -> Unit) {
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        if (lastRun == null) {
            Column(modifier = Modifier.padding(SvSpacing.Md)) {
                Text(stringResource(R.string.home_last_run), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.home_no_official_score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_alpha_no_score), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                onClick = { onOpenRun(lastRun.runId) },
            ) {
                Column(modifier = Modifier.padding(SvSpacing.Md)) {
                    Text(stringResource(R.string.home_last_run), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        stringResource(R.string.home_last_run_line, lastRun.workloadId, SvTime.formatIso(lastRun.startedAt, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday))),
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
    Text(validityLabel(level), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun validityLabel(level: ValidityLevel): String = when (level) {
    ValidityLevel.STABLE -> stringResource(SvR.string.sv_validity_stable)
    ValidityLevel.VARIABLE -> stringResource(SvR.string.sv_validity_variable)
    ValidityLevel.RETEST_RECOMMENDED -> stringResource(SvR.string.sv_validity_retest)
    ValidityLevel.INVALID -> stringResource(SvR.string.sv_validity_invalid)
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
            if (running) stringResource(R.string.home_measuring_btn) else stringResource(R.string.home_start_benchmark),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun factValue(facts: List<HardwareFact>, key: String): String? =
    facts.firstOrNull { it.key == key }?.rawValue

private fun bytesHuman(bytes: String?): String {
    val b = bytes?.toLongOrNull() ?: return "—"
    if (b >= 1_000_000_000_000) return "%.2f TB".format(b / 1_000_000_000_000.0)
    return "%.1f GB".format(b / 1_000_000_000.0)
}
