package com.siliconverity.core.designsystem

/** 字节数的 UI 友好格式化 (B/KB/MB/GB 自适应)。 */
object SvFormat {
    fun bytes(b: Long): String = when {
        b >= 1_000_000_000 -> "%.2f GB".format(b / 1_000_000_000.0)
        b >= 1_000_000 -> "%.1f MB".format(b / 1_000_000.0)
        b >= 1_000 -> "%.1f KB".format(b / 1_000.0)
        else -> "$b B"
    }

    fun bytes(raw: String?): String? = raw?.toLongOrNull()?.let { bytes(it) }
}
