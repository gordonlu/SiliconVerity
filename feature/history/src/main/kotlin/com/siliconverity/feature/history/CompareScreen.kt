package com.siliconverity.feature.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    vm: CompareViewModel,
    onBack: () -> Unit,
) {
    val runs by vm.runs.collectAsState()
    val sel = vm.selected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("COMPARE ${sel.size}/2", style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (sel.isNotEmpty()) TextButton(onClick = { vm.clear() }) { Text("清除") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = SvSpacing.PageHorizontal, vertical = SvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
        ) {
            if (runs.isEmpty()) {
                item { Text("尚无运行记录。", style = MaterialTheme.typography.bodyMedium) }
            }
            items(runs, key = { it.runId }) { m ->
                val idx = sel.indexOf(m.runId)
                RunSelectRow(m, selectedIdx = idx) { vm.toggle(m.runId) }
            }

            val a = runs.firstOrNull { it.runId == sel.getOrNull(0) }
            val b = runs.firstOrNull { it.runId == sel.getOrNull(1) }
            if (a != null && b != null) {
                item { Spacer(Modifier.height(SvSpacing.Md)); ComparisonPanel(a, b) }
            }
        }
    }
}

@Composable
private fun RunSelectRow(m: RunManifest, selectedIdx: Int, onClick: () -> Unit) {
    val tag = if (selectedIdx >= 0) (if (selectedIdx == 0) "A" else "B") else null
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (tag != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(SvSpacing.Md), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(m.workloadId, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(m.startedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "%.2f %s".format(WorkloadFormat.scale(m.workloadId, m.median), WorkloadFormat.unit(m.workloadId)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            if (tag != null) Text(tag, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ComparisonPanel(a: RunManifest, b: RunManifest) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(SvSpacing.StructureLine, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Text("A: ${a.workloadId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("B: ${b.workloadId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.workloadId != b.workloadId) {
                Text("⚠ workload 不同, 对比仅作参考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(SvSpacing.Sm))
            Kv("median A", "%.2f %s".format(WorkloadFormat.scale(a.workloadId, a.median), WorkloadFormat.unit(a.workloadId)))
            Kv("median B", "%.2f %s".format(WorkloadFormat.scale(b.workloadId, b.median), WorkloadFormat.unit(b.workloadId)))
            val delta = if (a.median != 0.0) (b.median - a.median) / a.median * 100.0 else 0.0
            val deltaColor = if (delta >= 0) SvColors.Accent else MaterialTheme.colorScheme.error
            Text("Δ median %.2f%%".format(delta), style = MaterialTheme.typography.titleMedium, color = deltaColor, fontWeight = FontWeight.SemiBold)
            Kv("cv A / B", "%.4f / %.4f".format(a.cv, b.cv))
            Kv("validity A / B", "${a.validityLevel.name} / ${b.validityLevel.name}")
            Kv("correctness A / B", "${if (a.correctnessStatus) "OK" else "FAIL"} / ${if (b.correctnessStatus) "OK" else "FAIL"}")
            Kv("thermal A / B", "${a.thermalStatusStart} / ${b.thermalStatusStart}")
        }
    }
}

@Composable
private fun Kv(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
