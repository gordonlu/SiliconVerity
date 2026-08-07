package com.siliconverity.feature.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.siliconverity.core.benchmark.RunManifest
import java.io.File

object RunExport {

    private const val AUTHORITY = "com.siliconverity.fileprovider"

    fun manifestToCsv(m: RunManifest): String {
        val sb = StringBuilder()
        sb.append("field,value\n")
        sb.append("runId,").append(m.runId).append('\n')
        sb.append("sessionId,").append(m.sessionId).append('\n')
        sb.append("benchmarkProtocolVersion,").append(m.benchmarkProtocolVersion).append('\n')
        sb.append("workloadId,").append(m.workloadId).append('\n')
        sb.append("workloadVersion,").append(m.workloadVersion).append('\n')
        sb.append("shaderSourceVersion,").append(m.shaderSourceVersion ?: "").append('\n')
        sb.append("spirvHash,").append(m.spirvHash ?: "").append('\n')
        sb.append("arithmeticType,").append(m.arithmeticType ?: "").append('\n')
        sb.append("arithmeticContract,").append(m.arithmeticContract ?: "").append('\n')
        sb.append("startedAt,").append(m.startedAt).append('\n')
        sb.append("deviceModel,").append(m.deviceModel).append('\n')
        sb.append("socReported,").append(m.socReported).append('\n')
        sb.append("androidVersion,").append(m.androidVersion).append('\n')
        sb.append("securityPatch,").append(m.securityPatch).append('\n')
        sb.append("abi,").append(m.abi).append('\n')
        sb.append("batteryLevel,").append(m.batteryLevel).append('\n')
        sb.append("chargingState,").append(m.chargingState).append('\n')
        sb.append("powerSaveMode,").append(m.powerSaveMode).append('\n')
        sb.append("thermalStatusStart,").append(m.thermalStatusStart).append('\n')
        sb.append("thermalStatusEnd,").append(m.thermalStatusEnd).append('\n')
        sb.append("median,").append(m.median).append('\n')
        sb.append("mad,").append(m.mad).append('\n')
        sb.append("cv,").append(m.cv).append('\n')
        sb.append("minimum,").append(m.minimum).append('\n')
        sb.append("maximum,").append(m.maximum).append('\n')
        sb.append("trendSlope,").append(m.trendSlope).append('\n')
        sb.append("outlierCount,").append(m.outlierCount).append('\n')
        sb.append("correctnessStatus,").append(m.correctnessStatus).append('\n')
        sb.append("validityLevel,").append(m.validityLevel.name).append('\n')
        sb.append("checksumKind,").append(m.checksumKind?.name ?: "").append('\n')
        m.warnings.forEach { sb.append("warning,").append(it).append('\n') }
        sb.append('\n')
        sb.append("index,workUnits,durationNanos,throughput\n")
        m.measurementSamples.forEach {
            sb.append(it.index).append(',').append(it.workUnits).append(',').append(it.durationNanos).append(',').append(it.throughput).append('\n')
        }
        return sb.toString()
    }

    fun shareCsv(context: Context, m: RunManifest) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${m.runId}.csv")
        file.writeText(manifestToCsv(m))
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "SiliconVerity ${m.workloadId} ${m.runId}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(send, "导出 CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun shareJsonFile(context: Context, runsDir: File, runId: String) {
        val file = File(runsDir, "$runId.json")
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "SiliconVerity Run Manifest $runId")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(send, "导出 JSON").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
