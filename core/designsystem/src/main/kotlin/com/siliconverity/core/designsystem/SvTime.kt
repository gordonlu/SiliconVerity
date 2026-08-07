package com.siliconverity.core.designsystem

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** ISO-8601 时间戳 -> UI 友好时间 (今天/昨天/MM-dd HH:mm)。 */
object SvTime {

    private val clockFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun formatIso(iso: String, todayLabel: String = "今天", yesterdayLabel: String = "昨天"): String = runCatching {
        val local = Instant.parse(iso).atZone(ZoneId.systemDefault())
        val today = LocalDate.now()
        val day = local.toLocalDate()
        when {
            day == today -> "$todayLabel " + local.format(clockFormatter)
            day == today.minusDays(1) -> "$yesterdayLabel " + local.format(clockFormatter)
            day.year == today.year -> local.format(clockFormatter)
            else -> local.format(fullFormatter)
        }
    }.getOrDefault(iso)
}
