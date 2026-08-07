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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.BenchmarkPayload
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.SustainedSample
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.benchmark.WorkloadFormat
import com.siliconverity.core.designsystem.SvTime
import com.siliconverity.core.designsystem.R as SvR
import com.siliconverity.core.storage.BenchmarkRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkRunDetailScreen(runId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val run by produceState<BenchmarkRun?>(initialValue = null, runId) {
        value = withContext(Dispatchers.IO) {
            val app = context.applicationContext
            BenchmarkRunStore(app.filesDir).load(runId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_run_manifest_title), style = MaterialTheme.typography.labelLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.history_back)) } },
            )
        },
    ) { padding ->
        val r = run
        if (r == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(r.identity.workloadId, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.history_version_time, r.identity.workloadVersion, SvTime.formatIso(r.startedAt, stringResource(SvR.string.sv_today), stringResource(SvR.string.sv_yesterday))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionTitle(stringResource(R.string.history_sec_identity)) }
            item { Kv(stringResource(R.string.history_kv_run_id), r.identity.runId) }
            item { Kv(stringResource(R.string.history_kv_session_id), r.identity.sessionId.ifEmpty { "-" }) }
            item { Kv(stringResource(R.string.history_kv_protocol), r.identity.benchmarkProtocolVersion) }
            r.identity.spirvHash?.let { item { Kv(stringResource(R.string.history_kv_spirv), it) } }
            r.identity.arithmeticType?.let { item { Kv(stringResource(R.string.history_kv_arith), "$it / ${r.identity.arithmeticContract}") } }
            item { SectionTitle(stringResource(R.string.history_sec_environment)) }
            item { Kv(stringResource(R.string.history_kv_device), r.environment.deviceModel) }
            item { Kv(stringResource(R.string.history_kv_soc), r.environment.socReported) }
            item { Kv(stringResource(R.string.history_kv_android), "${r.environment.androidVersion} (${r.environment.securityPatch})") }
            item { Kv(stringResource(R.string.history_kv_abi), r.environment.abi) }
            item { Kv(stringResource(R.string.history_kv_battery), "${r.environment.batteryLevel}%  ${r.environment.chargingState}") }
            item { Kv(stringResource(R.string.history_kv_thermal), "${r.environment.thermalStatusStart} -> ${r.environment.thermalStatusEnd}") }
            item { SectionTitle(stringResource(R.string.history_sec_validity)) }
            item { Kv(stringResource(R.string.history_kv_level), validityLabel(r.validity.stability)) }
            item { Kv(stringResource(R.string.history_kv_robust_cv), "%.4f".format(r.validity.robustCv)) }
            item { Kv(stringResource(R.string.history_kv_score_eligible), r.validity.scoreEligible.toString()) }
            item { Kv(stringResource(R.string.history_kv_correctness), "${if (r.correctness.passed) "OK" else "FAIL"} (${r.correctness.kind.name}${if (r.correctness.reason != null) ", ${r.correctness.reason}" else ""})") }
            item { SectionTitle(stringResource(R.string.history_sec_protocol)) }
            item { Kv(stringResource(R.string.history_kv_samples), r.protocol.measurementSamplesActual.toString()) }
            item { Kv(stringResource(R.string.history_kv_thresholds), "%.2f / %.2f".format(r.protocol.stableCvThreshold, r.protocol.variableCvThreshold)) }
            item { SectionTitle(stringResource(R.string.history_sec_payload)) }
            item { PayloadSection(r) }
            if (r.warnings.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.history_sec_warnings)) }
                r.warnings.forEach { item { Text(stringResource(R.string.history_warning_item, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun PayloadSection(r: BenchmarkRun) {
    when (val p = r.payload) {
        is BenchmarkPayload.Scalar -> {
            val s = p.summary
            Text(
                "%.2f %s".format(WorkloadFormat.scale(r.identity.workloadId, s.median), WorkloadFormat.unit(r.identity.workloadId)),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            Kv(stringResource(R.string.history_kv_mad), "%.4f".format(s.mad))
            Kv(stringResource(R.string.history_kv_min_max), "%.2f / %.2f".format(WorkloadFormat.scale(r.identity.workloadId, s.minimum), WorkloadFormat.scale(r.identity.workloadId, s.maximum)))
            Kv(stringResource(R.string.history_kv_trend_slope), "%.4f".format(s.trendSlope))
            Kv(stringResource(R.string.history_kv_relative_trend), "%.4f".format(s.relativeTrend))
            Kv(stringResource(R.string.history_kv_outliers), s.outlierCount.toString())
            Text(stringResource(R.string.history_samples_count, p.samples.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.samples.forEach { s0 ->
                Text(stringResource(R.string.history_sample_line, s0.index, s0.workUnits, s0.durationNanos), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        is BenchmarkPayload.Timeline -> {
            val res = p.result
            Text(stringResource(R.string.history_retention_value, res.retention * 100), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
            Kv(stringResource(R.string.history_kv_initial_stable), stringResource(R.string.history_m_ops_s, res.initialMedian / 1e6, res.stableMedian / 1e6))
            Kv(stringResource(R.string.history_kv_t90), stringResource(R.string.history_t90_line, res.timeTo90Percent, res.timeTo80Percent))
            Kv(stringResource(R.string.history_kv_worst), stringResource(R.string.history_worst_line, res.worstStableWindow / 1e6))
            Kv(stringResource(R.string.history_kv_total_work), stringResource(R.string.history_total_work_line, res.absoluteWorkCompleted))
            Text(stringResource(R.string.history_windows, res.samples.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            res.samples.take(20).forEach { s0: SustainedSample ->
                Text(stringResource(R.string.history_timeline_sample, s0.elapsedSec.toInt(), s0.thermalStatus), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        is BenchmarkPayload.Curve -> {
            Text(stringResource(R.string.history_points, p.points.size), style = MaterialTheme.typography.bodyMedium)
            p.points.forEach { pt ->
                Text(stringResource(R.string.history_point_line, formatSize(pt.sizeBytes), if (pt.latencyNs >= 0) stringResource(R.string.history_latency_ns, pt.latencyNs) else "-"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        is BenchmarkPayload.Diagnostics -> {
            p.metrics.forEach { (k, v) -> Kv(k, "%.1f".format(v)) }
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
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Kv(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / 1024 / 1024} MiB"
    bytes >= 1024 -> "${bytes / 1024} KiB"
    else -> "$bytes B"
}
