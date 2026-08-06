package com.siliconverity.core.hardware

import android.content.Context
import android.os.StatFs
import com.siliconverity.core.model.Evidence
import com.siliconverity.core.model.SourceType

class StorageCollector : HardwareCollector {
    override val key: String = "storage"

    override fun collect(context: Context): List<CollectedFact> {
        val facts = mutableListOf<CollectedFact>()
        val result = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            facts += CollectedFact(
                key = "storage.fs.total",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "StatFs(filesDir).getTotalBytes",
                        rawValue = stat.totalBytes.toString(),
                        note = "file system total, not raw flash chip capacity",
                    ),
                ),
            )
            facts += CollectedFact(
                key = "storage.fs.available",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "StatFs(filesDir).getAvailableBytes",
                        rawValue = stat.availableBytes.toString(),
                        note = "app available space",
                    ),
                ),
            )
            facts += CollectedFact(
                key = "storage.fs.block_size",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "StatFs(filesDir).getBlockSizeLong",
                        rawValue = stat.blockSizeLong.toString(),
                    ),
                ),
            )
            facts += CollectedFact(
                key = "storage.fs.free",
                evidence = listOf(
                    Evidence(
                        sourceType = SourceType.PUBLIC_API,
                        sourceId = "StatFs(filesDir).getFreeBytes",
                        rawValue = stat.freeBytes.toString(),
                        note = "free bytes incl. reserved",
                    ),
                ),
            )
        }
        result.onFailure {
            facts += CollectedFact(
                key = "storage.fs.total",
                evidence = emptyList(),
                warnings = listOf("StatFs failed: ${it.message}"),
            )
        }
        return facts
    }
}
