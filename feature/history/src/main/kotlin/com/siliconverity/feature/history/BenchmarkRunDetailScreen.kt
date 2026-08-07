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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.benchmark.BenchmarkPayload
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.SustainedSample
import com.siliconverity.core.benchmark.WorkloadFormat
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
                title = { Text("RUN MANIFEST", style = MaterialTheme.typography.labelLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") } },
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
                Text("v${r.identity.workloadVersion}  •  ${r.startedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { SectionTitle("IDENTITY") }
            item { Kv("runId", r.identity.runId) }
            item { Kv("sessionId", r.identity.sessionId.ifEmpty { "-" }) }
            item { Kv("protocol", r.identity.benchmarkProtocolVersion) }
            r.identity.spirvHash?.let { item { Kv("spirv", it) } }
            r.identity.arithmeticType?.let { item { Kv("arith", "$it / ${r.identity.arithmeticContract}") } }
            item { SectionTitle("ENVIRONMENT") }
            item { Kv("device", r.environment.deviceModel) }
            item { Kv("SoC", r.environment.socReported) }
            item { Kv("Android", "${r.environment.androidVersion} (${r.environment.securityPatch})") }
            item { Kv("ABI", r.environment.abi) }
            item { Kv("battery", "${r.environment.batteryLevel}%  ${r.environment.chargingState}") }
            item { Kv("thermal", "${r.environment.thermalStatusStart} -> ${r.environment.thermalStatusEnd}") }
            item { SectionTitle("VALIDITY") }
            item { Kv("level", r.validity.stability.name) }
            item { Kv("robustCv", "%.4f".format(r.validity.robustCv)) }
            item { Kv("scoreEligible", r.validity.scoreEligible.toString()) }
            item { Kv("correctness", "${if (r.correctness.passed) "OK" else "FAIL"} (${r.correctness.kind.name}${if (r.correctness.reason != null) ", ${r.correctness.reason}" else ""})") }
            item { SectionTitle("PROTOCOL") }
            item { Kv("samples", r.protocol.measurementSamplesActual.toString()) }
            item { Kv("thresholds", "%.2f / %.2f".format(r.protocol.stableCvThreshold, r.protocol.variableCvThreshold)) }
            item { SectionTitle("PAYLOAD") }
            item { PayloadSection(r) }
            if (r.warnings.isNotEmpty()) {
                item { SectionTitle("WARNINGS") }
                r.warnings.forEach { item { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
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
            Kv("mad", "%.4f".format(s.mad))
            Kv("min / max", "%.2f / %.2f".format(WorkloadFormat.scale(r.identity.workloadId, s.minimum), WorkloadFormat.scale(r.identity.workloadId, s.maximum)))
            Kv("trendSlope", "%.4f".format(s.trendSlope))
            Kv("relativeTrend", "%.4f".format(s.relativeTrend))
            Kv("outliers", s.outlierCount.toString())
            Text("samples (${p.samples.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.samples.forEach { s0 ->
                Text("#${s0.index}  ${s0.workUnits} units  ${s0.durationNanos} ns", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        is BenchmarkPayload.Timeline -> {
            val res = p.result
            Text("retention %.1f%%".format(res.retention * 100), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
            Kv("initial / stable", "%.2f / %.2f M ops/s".format(res.initialMedian / 1e6, res.stableMedian / 1e6))
            Kv("t90 / t80", "%.0f / %.0f s".format(res.timeTo90Percent, res.timeTo80Percent))
            Kv("worst window", "%.2f M ops/s".format(res.worstStableWindow / 1e6))
            Kv("total work", "%,d".format(res.absoluteWorkCompleted))
            Text("windows: ${res.samples.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            res.samples.take(20).forEach { s0: SustainedSample ->
                Text("t=${s0.elapsedSec.toInt()}s  ${s0.thermalStatus}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        is BenchmarkPayload.Curve -> {
            Text("${p.points.size} points", style = MaterialTheme.typography.bodyMedium)
            p.points.forEach { pt ->
                Text(formatSize(pt.sizeBytes) + "  " + (if (pt.latencyNs >= 0) "%.1f ns".format(pt.latencyNs) else "-"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        is BenchmarkPayload.Diagnostics -> {
            p.metrics.forEach { (k, v) -> Kv(k, "%.1f".format(v)) }
        }
    }
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
