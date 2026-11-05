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
            abs < 1024 -> "$value B"
            abs < 1024 * 1024 -> String.format("%.1f KB", value / 1024.0)
            abs < 1024L * 1024 * 1024 -> String.format("%.1f MB", value / (1024.0 * 1024))
            abs < 1024L * 1024 * 1024 * 1024 -> String.format("%.2f GB", value / (1024.0 * 1024 * 1024))
            else -> String.format("%.2f TB", value / (1024.0 * 1024 * 1024 * 1024))
        }
    }

    fun duration(seconds: Long?): String {
        if (seconds == null) return "—"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "${seconds}s"
        }
    }

    fun epoch(seconds: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (seconds <= 0) return "—"
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(zone)
            .format(Instant.ofEpochSecond(seconds))
    }

    /** Transfer speed, locale neutral (KB/s, MB/s). */
    fun rate(bytesPerSecond: Long?): String {
        if (bytesPerSecond == null || bytesPerSecond <= 0) return "—"
        return "${bytes(bytesPerSecond)}/s"
    }

    /** Short remaining-time estimate for transfers. */
    fun eta(seconds: Long?): String {
        if (seconds == null || seconds < 0) return "—"
        return when {
            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    fun percent(fraction: Float?): String = if (fraction == null) "—" else "${(fraction * 100).toInt()}%"

    fun battery(level: Int?): String = if (level == null) "—" else "$level%"
}
