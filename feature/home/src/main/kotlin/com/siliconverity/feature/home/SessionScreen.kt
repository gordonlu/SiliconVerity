package com.siliconverity.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siliconverity.core.benchmark.BenchmarkCategory
import com.siliconverity.core.benchmark.BenchmarkPhase
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.benchmark.WorkloadProgress
import com.siliconverity.core.designsystem.SvPanel
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvThermalStatus
import com.siliconverity.core.designsystem.SvWorkloads
import com.siliconverity.core.designsystem.R as SvR

/**
 * Benchmark Session 页: 静态仪表, 仅在阶段边界更新。
 * 无动画/无实时成绩, 避免与被测 GPU/CPU 抢资源。
 */
@Composable
fun SessionScreen(
    state: BenchmarkUiState.Running,
    onStop: () -> Unit,
) {
    val percent = state.index * 100 / state.total
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SvSpacing.PageHorizontal, vertical = SvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(stringResource(R.string.home_brand), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.session_badge), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }

        // 大进度
        Text(stringResource(R.string.session_perf_test), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(
                "%02d / %d".format(state.index, state.total),
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text("$percent%", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
        StaticProgressBar(fraction = state.index.toFloat() / state.total)

        // 分类分段: CPU 5 / MEMORY 3 / GPU 3 / I/O 4
        CategorySegments(state)

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)

        // 当前项目
        val name = stringResource(SvWorkloads.nameRes(state.workloadId))
        val desc = stringResource(SvWorkloads.descRes(state.workloadId))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(SvWorkloads.categoryNameRes(state.category)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(SvSpacing.Xs))
            if (state.paused) {
                Text(
                    stringResource(R.string.session_paused),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                PhaseLine(state.phase, state.sampleIndex, state.sampleCount)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)

        // 分类状态
        BenchmarkCategory.entries.forEach { category ->
            CategoryStatusRow(category, state)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = SvSpacing.StructureLine)

        // 环境 3 项
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
            EnvCell(stringResource(R.string.session_env_thermal), SvThermalStatus.short(state.environment.thermal))
            EnvCell(stringResource(R.string.session_env_battery), state.environment.batteryTempC?.let { "%.1f°C".format(it) } ?: "—")
            EnvCell(stringResource(R.string.session_env_power), state.environment.power)
        }

        Text(
            stringResource(R.string.session_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.error),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.session_stop), fontWeight = FontWeight.SemiBold) }
    }
}

/** 不带动画的进度线。 */
@Composable
private fun StaticProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        )
    }
}

/** 分类分段: 每 workload 一小格, 完成=primary, 当前=tertiary, 等待=outline。 */
@Composable
private fun CategorySegments(state: BenchmarkUiState.Running) {
    val doneIds = state.completed.map { it.workloadId }.toSet()
    val segments = mutableListOf<Pair<BenchmarkCategory, SegmentState>>()
    for (category in BenchmarkCategory.entries) {
        val ids = categoryWorkloadIds[category] ?: emptyList()
        for (id in ids) {
            val segState = when {
                id in doneIds -> SegmentState.DONE
                id == state.workloadId -> SegmentState.CURRENT
                else -> SegmentState.WAITING
            }
            segments.add(category to segState)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            segments.forEach { (_, segState) ->
                val color = when (segState) {
                    SegmentState.DONE -> MaterialTheme.colorScheme.primary
                    SegmentState.CURRENT -> MaterialTheme.colorScheme.tertiary
                    SegmentState.WAITING -> MaterialTheme.colorScheme.surfaceContainer
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(color, MaterialTheme.shapes.small),
                )
            }
        }
        BenchmarkCategory.entries.forEach { category ->
            val start = SvWorkloads.categoryStartIndex(category)
            val size = SvWorkloads.categorySize(category)
            Text(
                "${stringResource(SvWorkloads.categoryNameRes(category))}  %02d–%02d".format(start, start + size - 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class SegmentState { DONE, CURRENT, WAITING }

private val categoryWorkloadIds: Map<BenchmarkCategory, List<String>> = mapOf(
    BenchmarkCategory.CPU to listOf("cpu.int.ilp", "cpu.fp32.fma", "cpu.int.branch", "cpu.hash.cached", "cpu.multithread"),
    BenchmarkCategory.MEMORY to listOf("mem.bandwidth.read", "mem.bandwidth.copy", "mem.latency.curve"),
    BenchmarkCategory.GPU to listOf("vulkan.fp32.independent", "vulkan.fp32.dependency", "vulkan.buffer.throughput"),
    BenchmarkCategory.APP_IO to listOf("storage.seq_write.buffered", "storage.seq_write.durable", "storage.random_write.fsync", "storage.seq_read.warm"),
)

@Composable
private fun PhaseLine(phase: BenchmarkPhase, sampleIndex: Int?, sampleCount: Int?) {
    val phaseName = when (phase) {
        BenchmarkPhase.CALIBRATING -> stringResource(R.string.session_phase_calibrating)
        BenchmarkPhase.WARMING_UP -> stringResource(R.string.session_phase_warming)
        BenchmarkPhase.MEASURING -> stringResource(R.string.session_phase_measuring)
        BenchmarkPhase.VERIFYING -> stringResource(R.string.session_phase_verifying)
        BenchmarkPhase.FINALIZING -> stringResource(R.string.session_phase_finalizing)
    }
    val sampleText = when {
        sampleIndex == null || sampleCount == null -> ""
        phase == BenchmarkPhase.WARMING_UP ->
            "  ·  ${stringResource(R.string.session_phase_warming_sub, sampleIndex, sampleCount)}"
        phase == BenchmarkPhase.MEASURING ->
            "  ·  ${stringResource(R.string.session_phase_measuring_sub, sampleIndex, sampleCount)}"
        else -> ""
    }
    Text(
        phaseName + sampleText,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CategoryStatusRow(category: BenchmarkCategory, state: BenchmarkUiState.Running) {
    val size = SvWorkloads.categorySize(category)
    val doneCount = state.completed.count { it.category == category }
    val isCurrent = state.category == category
    val (statusLabel, statusColor) = when {
        doneCount >= size -> stringResource(R.string.session_status_complete) to MaterialTheme.colorScheme.primary
        isCurrent -> stringResource(R.string.session_status_running) to MaterialTheme.colorScheme.tertiary
        else -> stringResource(R.string.session_status_waiting) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(SvWorkloads.categoryNameRes(category)),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "$statusLabel  $doneCount / $size",
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EnvCell(label: String, value: String) {
    SvPanel(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(SvSpacing.Xs)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
