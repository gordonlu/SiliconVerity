package com.siliconverity.feature.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.BenchmarkCategory
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.benchmark.RunResult
import com.siliconverity.core.benchmark.ScoreReport
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvPanel
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.core.designsystem.SvTime
import com.siliconverity.core.designsystem.SvWorkloads
import com.siliconverity.core.designsystem.R as SvR
import com.siliconverity.core.model.HardwareFact

/**
 * 基准测试结果页 — 第一屏: 综合分 (最大) + 四分类分 (×10 尺度) + 设备信息。
 * 通用参数: 既可用于跑完自动进入, 也可从历史/最近会话回看。
 */
@Composable
fun ResultScreen(
    score: ScoreReport?,
    sessionStartedAt: String?,
    error: String?,
    hardwareFacts: List<HardwareFact>,
    onRunAgain: () -> Unit,
    onHistory: () -> Unit,
    onShare: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(
            start = SvSpacing.PageHorizontal,
            end = SvSpacing.PageHorizontal,
            top = SvSpacing.Sm,
            bottom = SvSpacing.Sm,
        ),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(stringResource(R.string.home_brand), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.result_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (score != null) {
            item {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.home_sv_performance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "%,d".format(score.overallScore ?: 0),
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    val confidence = when (score.confidence.level) {
                        "HIGH" -> stringResource(R.string.result_high_confidence)
                        "MEDIUM" -> "MEDIUM"
                        else -> "LOW"
                    }
                    Text(
                        "${stringResource(R.string.result_valid)} · $confidence",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                val context = LocalContext.current
                val zh = java.util.Locale.getDefault().language.startsWith("zh")
                val soc = factValue(hardwareFacts, "soc.model")?.let {
                    com.siliconverity.core.hardware.SocNameResolver.displayWithCode(context, it, zh)
                } ?: ""
                val ram = factValue(hardwareFacts, "memory.totalMem")?.toLongOrNull()?.let { "%.0f GB".format(it / 1_000_000_000.0) } ?: ""
                val android = factValue(hardwareFacts, "device.android_version") ?: ""
                val date = sessionStartedAt?.let {
                    SvTime.formatIso(it, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday))
                } ?: ""
                Text(
                    listOfNotNull(soc, ram, "Android $android", date).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
                    CategoryScoreRow(BenchmarkCategory.CPU, score.cpuScore, hardwareFacts)
                    CategoryScoreRow(BenchmarkCategory.GPU, score.gpuScore, hardwareFacts)
                    CategoryScoreRow(BenchmarkCategory.MEMORY, score.memoryScore, hardwareFacts)
                    CategoryScoreRow(BenchmarkCategory.APP_IO, score.appIoScore, hardwareFacts)
                }
            }
            item {
                Text(
                    stringResource(R.string.result_reference_line, score.scoreVersion),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                val reason = error ?: stringResource(R.string.home_score_not_generated)
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Spacer(Modifier.height(SvSpacing.Xs))
            Button(
                onClick = onRunAgain,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = SvColors.Background,
                ),
            ) { Text(stringResource(R.string.result_run_again), fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(SvSpacing.Xs))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = MaterialTheme.shapes.small,
                ) { Text(stringResource(R.string.result_history), fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = MaterialTheme.shapes.small,
                ) { Text(stringResource(R.string.result_share), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** 分类协调色: CPU 蓝 / GPU 紫 / Memory 青 / I/O 琥珀 (暗色友好)。 */
private fun categoryColor(category: BenchmarkCategory): Color = when (category) {
    BenchmarkCategory.CPU -> Color(0xFF8AB4F8)
    BenchmarkCategory.GPU -> Color(0xFFD7AEFB)
    BenchmarkCategory.MEMORY -> Color(0xFF7FE0C3)
    BenchmarkCategory.APP_IO -> Color(0xFFFFCC80)
}

/** 分类分 (index × 10 展示, 参考水平 = 10,000) + 静态条。 */
@Composable
private fun CategoryScoreRow(
    category: BenchmarkCategory,
    categoryScore: Int?,
    hardwareFacts: List<HardwareFact>,
) {
    val color = categoryColor(category)
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.Sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(SvWorkloads.categoryNameRes(category)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Text(
                    categoryScore?.let { "%,d".format(it * 10) } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(2.dp))
            val fraction = (categoryScore ?: 0) * 10f / 20_000f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(color, MaterialTheme.shapes.small),
                )
            }
        }
    }
}

private fun factValue(facts: List<HardwareFact>, key: String): String? =
    facts.firstOrNull { it.key == key }?.rawValue
