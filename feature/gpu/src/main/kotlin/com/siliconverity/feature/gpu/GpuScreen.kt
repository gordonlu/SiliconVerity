package com.siliconverity.feature.gpu

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvPanel
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.nativegpu.NativeGpuResult

@Composable
fun GpuScreen(
    state: GpuUiState,
    onRun: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(
            start = SvSpacing.PageHorizontal,
            end = SvSpacing.PageHorizontal,
            top = SvSpacing.Md,
            bottom = SvSpacing.Md,
        ),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.Sm),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GPU COMPUTE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onBack) { Text("返回") }
            }
            Text(
                "Vulkan Compute MiniBench (无图形管线)。仅 Shader 吞吐与 Buffer 吞吐，不等于完整图形性能。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val running = state is GpuUiState.Running
        item {
            Button(
                onClick = onRun,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (running) "MEASURING..." else "RUN GPU COMPUTE", fontWeight = FontWeight.SemiBold)
            }
        }

        when (state) {
            is GpuUiState.Idle -> item {
                Text("尚未运行", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is GpuUiState.Running -> item {
                Text("FP32 Independent / Dependency / Buffer 各 3 预热 + 7 测量…", style = MaterialTheme.typography.bodyMedium)
            }
            is GpuUiState.Done -> {
                state.error?.let { item { Text("出错: $it", color = MaterialTheme.colorScheme.error) } }
                item { ResultCard("FP32 INDEPENDENT", state.independent) }
                item { ResultCard("FP32 DEPENDENCY", state.dependency) }
                item { ResultCard("BUFFER THROUGHPUT", state.buffer) }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, r: NativeGpuResult?) {
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(SvSpacing.Xs))
            if (r == null) {
                Text("无结果", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (!r.supported) {
                Text("Vulkan 不可用", color = MaterialTheme.colorScheme.error)
                r.invalidReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@Column
            }
            r.deviceName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            Text(
                "driver ${r.driverVersion ?: "?"}  •  Vulkan ${r.vulkanVersion ?: "?"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!r.arithType.isNullOrEmpty()) {
                Text(
                    "arith ${r.arithType} / ${r.arithContract ?: "?"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(SvSpacing.Sm))
            val mv = r.metricValue
            val unit = r.metricUnit ?: ""
            Text(
                if (mv != null) "%.2f %s".format(mv, unit) else "-",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "CV %.4f  •  GPU exec %,d ns".format(r.coefficientOfVariation ?: 0.0, r.gpuExecNs ?: 0L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "submit diag  rec %,d / submit %,d / wait %,d ns".format(
                    r.commandRecordingNs ?: 0L, r.queueSubmitNs ?: 0L, r.completionWaitNs ?: 0L,
                ),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val checksumOk = r.checksumValid
            Text(
                if (checksumOk) "checksum OK" else "checksum FAIL",
                style = MaterialTheme.typography.labelSmall,
                color = if (checksumOk) SvColors.Accent else MaterialTheme.colorScheme.error,
            )
            r.spirvHash?.let {
                Text("spirv $it", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            r.invalidReason?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
