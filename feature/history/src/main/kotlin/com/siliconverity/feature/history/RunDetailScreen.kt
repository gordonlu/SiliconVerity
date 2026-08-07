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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.RunManifest
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.designsystem.SvTime
import com.siliconverity.core.designsystem.R as SvR
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
                title = { Text(stringResource(R.string.history_run_manifest_title), style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.history_back))
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
                Text(
                    stringResource(R.string.history_version_time, m.workloadVersion, SvTime.formatIso(m.startedAt, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { KvRow(stringResource(R.string.history_run_id), m.runId) }
            item { KvRow(stringResource(R.string.history_device), m.deviceModel) }
            item { KvRow(stringResource(R.string.history_soc_reported), m.socReported) }
            item { KvRow(stringResource(R.string.history_android), "${m.androidVersion} (${m.securityPatch})") }
            item { KvRow(stringResource(R.string.history_abi), m.abi) }
            item { KvRow(stringResource(R.string.history_battery), "${m.batteryLevel}%  ${m.chargingState}") }
            item { KvRow(stringResource(R.string.history_thermal), "${m.thermalStatusStart} -> ${m.thermalStatusEnd}") }
            item { KvRow(stringResource(R.string.history_app_version), m.appVersion) }
            item { KvRow(stringResource(R.string.history_engine_version), m.benchmarkEngineVersion) }
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
                Text(stringResource(R.string.history_median), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { KvRow(stringResource(R.string.history_mad), "%.4f".format(m.mad)) }
            item { KvRow(stringResource(R.string.history_cv_label), "%.4f".format(m.cv)) }
            item { KvRow(stringResource(R.string.history_correctness), if (m.correctnessStatus) stringResource(R.string.history_passed) else stringResource(R.string.history_failed)) }
            item { KvRow(stringResource(R.string.history_validity), validityLabel(m.validityLevel)) }
            item { KvRow(stringResource(R.string.history_meas_samples), m.measurementSamples.size.toString()) }
            item { KvRow(stringResource(R.string.history_warmup_samples), m.warmupSamples.size.toString()) }
            if (m.warnings.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.history_warnings), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    m.warnings.forEach { Text(stringResource(R.string.history_warning_item, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun validityLabel(level: ValidityLevel): String = when (level) {
    ValidityLevel.STABLE -> stringResource(SvR.string.sv_validity_stable)
    ValidityLevel.VARIABLE -> stringResource(SvR.string.sv_validity_variable)
    ValidityLevel.RETEST_RECOMMENDED -> stringResource(SvR.string.sv_validity_retest)
    ValidityLevel.INVALID -> stringResource(SvR.string.sv_validity_invalid)
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
