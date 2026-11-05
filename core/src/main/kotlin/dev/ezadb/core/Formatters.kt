package dev.ezadb.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object Formatters {
    fun bytes(value: Long?): String {
        if (value == null) return "—"
        val abs = abs(value)
        return when {
            abs < 1024 -> "$value Б"
            abs < 1024 * 1024 -> String.format("%.1f КиБ", value / 1024.0)
            abs < 1024L * 1024 * 1024 -> String.format("%.1f МиБ", value / (1024.0 * 1024))
            abs < 1024L * 1024 * 1024 * 1024 -> String.format("%.2f ГиБ", value / (1024.0 * 1024 * 1024))
            else -> String.format("%.2f ТиБ", value / (1024.0 * 1024 * 1024 * 1024))
        }
    }

    fun duration(seconds: Long?): String {
        if (seconds == null) return "—"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "${d}д ${h}ч"
            h > 0 -> "${h}ч ${m}м"
            m > 0 -> "${m}м"
            else -> "${seconds}с"
        }
    }

    fun epoch(seconds: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (seconds <= 0) return "—"
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(zone)
            .format(Instant.ofEpochSecond(seconds))
    }

    fun percent(fraction: Float?): String = if (fraction == null) "—" else "${(fraction * 100).toInt()}%"

    fun battery(level: Int?): String = if (level == null) "—" else "$level%"
}
