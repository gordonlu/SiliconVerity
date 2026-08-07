package com.siliconverity.core.hardware

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * SoC 型号码 -> 商用名解析。
 * 数据源: assets/cpu_soc_table.json (94 条, 按名称+数字自然排序)。
 * 同码多型号 (如 MT6991 = Dimensity 9400/9400+) 用设备最大 CPU 频率自动区分;
 * 频率不可读或未收录时回退首个命中/原始值。
 */
object SocNameResolver {

    data class SocEntry(
        val vendor: String,
        val name: String,
        val code: String,
        val gpu: String,
        val maxFreqGhz: Double? = null,
    )

    private const val ASSET = "cpu_soc_table.json"

    @Volatile
    private var table: List<SocEntry>? = null

    fun resolve(context: Context, code: String?): SocEntry? {
        if (code.isNullOrBlank()) return null
        val t = table ?: synchronized(this) {
            table ?: load(context).also { table = it }
        }
        val key = normalize(code)
        val candidates = t.filter { normalize(it.code) == key }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // 同码多型号: 用最大 CPU 频率选最接近的 (如 9400 3.626GHz vs 9400+ 3.73GHz)
        val maxMhz = readMaxFreqMhz()
        val freqKnown = candidates.filter { it.maxFreqGhz != null }
        if (maxMhz != null && freqKnown.isNotEmpty()) {
            return freqKnown.minBy { kotlin.math.abs(it.maxFreqGhz!! * 1000.0 - maxMhz) }
        }
        return candidates.first()
    }

    /** 商用名; 未收录返回原码。 */
    fun displayName(context: Context, code: String?): String? =
        resolve(context, code)?.name ?: code

    /** 商用名 + 原始码 (用于详情)。 */
    fun displayWithCode(context: Context, code: String?): String? {
        if (code.isNullOrBlank()) return null
        val entry = resolve(context, code)
        return if (entry != null && entry.code != entry.name) "${entry.name} (${code})" else code
    }

    private fun load(context: Context): List<SocEntry> = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                add(
                    SocEntry(
                        vendor = o.optString("vendor"),
                        name = o.optString("name"),
                        code = o.optString("code"),
                        gpu = o.optString("gpu"),
                        maxFreqGhz = o.optString("max_freq_ghz").toDoubleOrNull(),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    /** 最大 CPU 频率 (kHz), 多路径回退; 不可读返回 null。 */
    private fun readMaxFreqMhz(): Int? {
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
        )
        for (path in paths) {
            val mhz = runCatching { File(path).readText().trim().toInt() / 1000 }.getOrNull()
            if (mhz != null && mhz > 0) return mhz
        }
        return null
    }

    private fun normalize(s: String): String = s.lowercase().replace("-", "").replace(" ", "").replace("_", "")
}
