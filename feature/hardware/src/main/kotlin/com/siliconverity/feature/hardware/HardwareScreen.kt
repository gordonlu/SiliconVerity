package com.siliconverity.feature.hardware

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.model.Confidence
import com.siliconverity.core.model.HardwareFact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScreen(
    hardwareState: HardwareUiState,
    benchmarkState: BenchmarkUiState,
    onRefresh: () -> Unit,
    onRunBenchmark: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("芯鉴 · SiliconVerity") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BenchmarkCard(benchmarkState, onRunBenchmark) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("硬件信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = onRefresh) { Text("刷新") }
                }
            }
            if (hardwareState.loading && hardwareState.facts.isEmpty()) {
                item { CircularProgressIndicator() }
            }
            hardwareState.error?.let {
                item { Text("采集出错: $it", color = MaterialTheme.colorScheme.error) }
            }
            items(hardwareState.facts, key = { it.key }) { fact ->
                FactCard(fact)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactCard(fact: HardwareFact) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fact.key, fontWeight = FontWeight.SemiBold)
                Text(fact.displayValue ?: "未知", fontFamily = FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    fact.sourceType.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfidenceChip(fact.confidence)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("来源 ID: ${fact.sourceId}", style = MaterialTheme.typography.bodySmall)
                Text("原始值: ${fact.rawValue}", style = MaterialTheme.typography.bodySmall)
                Text("采集时间: ${fact.collectedAt}", style = MaterialTheme.typography.bodySmall)
                fact.capabilityStatus?.let {
                    Text("能力状态: $it", style = MaterialTheme.typography.bodySmall)
                }
                if (fact.warnings.isNotEmpty()) {
                    Text(
                        "警告: ${fact.warnings.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (fact.conflictingEvidence.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("冲突证据:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    fact.conflictingEvidence.forEach { ev ->
                        Text(
                            "  - [${ev.sourceType}] ${ev.sourceId} = ${ev.rawValue}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: Confidence) {
    val color = when (confidence) {
        Confidence.HIGH -> MaterialTheme.colorScheme.primary
        Confidence.MEDIUM -> MaterialTheme.colorScheme.tertiary
        Confidence.LOW, Confidence.UNKNOWN, Confidence.CONFLICTED -> MaterialTheme.colorScheme.error
    }
    Text(
        confidence.name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BenchmarkCard(state: BenchmarkUiState, onRun: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("CPU 整数 ALU 测试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRun, enabled = state !is BenchmarkUiState.Running) {
                Text(if (state is BenchmarkUiState.Running) "运行中..." else "运行 CPU Integer workload")
            }
            Spacer(Modifier.height(8.dp))
            when (state) {
                is BenchmarkUiState.Idle -> Text("尚未运行", style = MaterialTheme.typography.bodyMedium)
                is BenchmarkUiState.Running -> Text("预热 + 7 轮测量进行中…", style = MaterialTheme.typography.bodyMedium)
                is BenchmarkUiState.Done -> BenchmarkResult(state)
            }
        }
    }
}

@Composable
private fun BenchmarkResult(state: BenchmarkUiState.Done) {
    val m = state.manifest
    if (state.error != null) {
        Text("出错: ${state.error}", color = MaterialTheme.colorScheme.error)
        return
    }
    if (m == null) {
        Text("无结果", color = MaterialTheme.colorScheme.error)
        return
    }
    val medianMops = m.median / 1_000_000.0
    Column {
        Text("workload: ${m.workloadId} v${m.workloadVersion}", style = MaterialTheme.typography.bodySmall)
        Text("运行 ID: ${m.runId}", style = MaterialTheme.typography.bodySmall)
        Text(
            "中位数吞吐: %.2f M ops/s".format(medianMops),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text("MAD: %.2f M ops/s".format(m.mad / 1_000_000.0), style = MaterialTheme.typography.bodySmall)
        Text("CV: %.4f".format(m.cv), style = MaterialTheme.typography.bodySmall)
        Text(
            "有效性: ${m.validityLevel.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = validityColor(m.validityLevel),
        )
        Text("正确性校验: ${if (m.correctnessStatus) "通过" else "失败"}", style = MaterialTheme.typography.bodySmall)
        Text("热状态: ${m.thermalStatusStart} -> ${m.thermalStatusEnd}", style = MaterialTheme.typography.bodySmall)
        Text("设备: ${m.deviceModel} / ${m.socReported}", style = MaterialTheme.typography.bodySmall)
        Text("Android: ${m.androidVersion} (${m.securityPatch})", style = MaterialTheme.typography.bodySmall)
        Text("ABI: ${m.abi}", style = MaterialTheme.typography.bodySmall)
        if (m.warnings.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "警告: ${m.warnings.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun validityColor(level: ValidityLevel): Color = when (level) {
    ValidityLevel.CLEAN -> MaterialTheme.colorScheme.primary
    ValidityLevel.ACCEPTABLE -> MaterialTheme.colorScheme.tertiary
    ValidityLevel.NOISY -> MaterialTheme.colorScheme.error
    ValidityLevel.INVALID -> MaterialTheme.colorScheme.error
}
