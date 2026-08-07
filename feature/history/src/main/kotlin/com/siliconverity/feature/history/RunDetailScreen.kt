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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.storage.RunManifestStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    runId: String,
    runsDir: File,
    onBack: () -> Unit,
) {
    val manifest by produceState<RunManifest?>(initialValue = null, runId, runsDir) {
        value = withContext(Dispatchers.IO) { RunManifestStore(runsDir).load(runId) }
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RUN MANIFEST", style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val m = manifest
                    if (m != null) {
                        androidx.compose.material3.TextButton(onClick = { RunExport.shareCsv(context, m) }) { Text("CSV") }
                        androidx.compose.material3.TextButton(onClick = { RunExport.shareJsonFile(context, runsDir, runId) }) { Text("JSON") }
                    }
                },
            )
        },
    ) { padding ->
        val m = manifest
        if (m == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(m.workloadId, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("v${m.workloadVersion}  •  ${m.startedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { KvRow("运行 ID", m.runId) }
            item { KvRow("设备", m.deviceModel) }
            item { KvRow("SoC (报告)", m.socReported) }
            item { KvRow("Android", "${m.androidVersion} (${m.securityPatch})") }
            item { KvRow("ABI", m.abi) }
            item { KvRow("电池", "${m.batteryLevel}%  ${m.chargingState}") }
            item { KvRow("热状态", "${m.thermalStatusStart} -> ${m.thermalStatusEnd}") }
            item { KvRow("App 版本", m.appVersion) }
            item { KvRow("引擎版本", m.benchmarkEngineVersion) }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "%.2f %s".format(
                        WorkloadFormat.scale(m.workloadId, m.median),
                        WorkloadFormat.unit(m.workloadId),
                    ),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text("median", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { KvRow("MAD", "%.4f".format(m.mad)) }
            item { KvRow("CV", "%.4f".format(m.cv)) }
            item { KvRow("正确性", if (m.correctnessStatus) "通过" else "失败") }
            item { KvRow("有效性", m.validityLevel.name) }
            item { KvRow("测量样本数", m.measurementSamples.size.toString()) }
            item { KvRow("预热样本数", m.warmupSamples.size.toString()) }
            if (m.warnings.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("警告", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    m.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun KvRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
