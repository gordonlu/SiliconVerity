package com.siliconverity.feature.gpu

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.siliconverity.core.designsystem.SvColors
import com.siliconverity.core.designsystem.SvPanel
import com.siliconverity.core.designsystem.SvSpacing
import com.siliconverity.nativegpu.NativeGpuResult

@Composable
fun GpuScreen(
    state: GpuUiState,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onSurfaceChanged: (Surface?) -> Unit,
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
                Text(stringResource(R.string.gpu_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onBack) { Text(stringResource(R.string.gpu_back)) }
            }
            Text(
                stringResource(R.string.gpu_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            GraphicsSurface(onSurfaceChanged)
        }

        val gameMode = when (state) {
            is GpuUiState.Idle -> state.gameMode
            is GpuUiState.Running -> state.gameMode
            is GpuUiState.Done -> state.gameMode
        }
        if (gameMode.isNotEmpty()) {
            val gameModeEligible = gameMode == "STANDARD" || gameMode == "CUSTOM" || gameMode == "PERFORMANCE"
            item {
                Text(
                    stringResource(R.string.gpu_game_mode, gameMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gameModeEligible) SvColors.Accent else MaterialTheme.colorScheme.error,
                )
            }
        }

        val running = state is GpuUiState.Running
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SvSpacing.Sm)) {
                Button(
                    onClick = onRun,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (running) stringResource(R.string.gpu_run_measuring) else stringResource(R.string.gpu_run_start), fontWeight = FontWeight.SemiBold)
                }
                if (running) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    ) { Text(stringResource(R.string.gpu_stop)) }
                }
            }
        }

        when (state) {
            is GpuUiState.Idle -> item {
                Text(stringResource(R.string.gpu_not_run), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is GpuUiState.Running -> item {
                Text(stringResource(R.string.gpu_running_hint), style = MaterialTheme.typography.bodyMedium)
            }
            is GpuUiState.Done -> {
                state.error?.let { item { Text(stringResource(R.string.gpu_error, it), color = MaterialTheme.colorScheme.error) } }
                item { ResultCard(stringResource(R.string.gpu_card_graphics), state.graphics) }
                item { ResultCard(stringResource(R.string.gpu_card_independent), state.independent) }
                item { ResultCard(stringResource(R.string.gpu_card_dependency), state.dependency) }
                item { ResultCard(stringResource(R.string.gpu_card_buffer), state.buffer) }
            }
        }
    }
}

@Composable
private fun GraphicsSurface(onSurfaceChanged: (Surface?) -> Unit) {
    val currentCallback = rememberUpdatedState(onSurfaceChanged)
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { context ->
            SurfaceView(context).apply {
                keepScreenOn = true
                holder.setFixedSize(1920, 1080)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        currentCallback.value(holder.surface.takeIf { it.isValid })
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        currentCallback.value(holder.surface.takeIf { it.isValid })
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        currentCallback.value(null)
                    }
                })
            }
        },
    )
}

@Composable
private fun ResultCard(title: String, r: NativeGpuResult?) {
    SvPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(SvSpacing.Xs))
            if (r == null) {
                Text(stringResource(R.string.gpu_no_result), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (!r.supported) {
                Text(stringResource(R.string.gpu_vulkan_unavailable), color = MaterialTheme.colorScheme.error)
                r.invalidReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@Column
            }
            r.deviceName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            r.diag?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.gpu_driver_line, r.driverVersion ?: "?", r.vulkanVersion ?: "?"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val arithType = r.arithType
            if (!arithType.isNullOrEmpty()) {
                Text(
                    stringResource(R.string.gpu_arith_line, arithType, r.arithContract ?: "?"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(SvSpacing.Sm))
            val mv = r.metricValue
            val unit = r.metricUnit ?: ""
            Text(
                if (mv != null) stringResource(R.string.gpu_metric_value, mv, unit) else "-",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            val totalFrames = r.totalFrames
            if (totalFrames != null) {
                Text(
                    stringResource(
                        R.string.gpu_graphics_detail,
                        totalFrames,
                        (r.elapsedNanos ?: 0L) / 1_000_000_000.0,
                        r.presentedFps ?: 0.0,
                        r.surfaceWidth ?: 0,
                        r.surfaceHeight ?: 0,
                        r.presentMode ?: "?",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.gpu_graphics_frame_time,
                        (r.medianNs ?: 0L) / 1_000_000.0,
                        (r.p95FrameNanos ?: 0L) / 1_000_000.0,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.gpu_graphics_batch,
                        r.workloadIterations ?: 1,
                        r.measuredSceneIterations ?: totalFrames,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.gpu_cv_line, r.coefficientOfVariation ?: 0.0, r.gpuExecNs ?: 0L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.gpu_submit_line, r.commandRecordingNs ?: 0L, r.queueSubmitNs ?: 0L, r.completionWaitNs ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val checksumOk = r.checksumValid
            Text(
                stringResource(if (checksumOk) R.string.gpu_checksum_ok else R.string.gpu_checksum_fail),
                style = MaterialTheme.typography.labelSmall,
                color = if (checksumOk) SvColors.Accent else MaterialTheme.colorScheme.error,
            )
            r.spirvHash?.let {
                Text(stringResource(R.string.gpu_spirv, it), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            r.invalidReason?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
