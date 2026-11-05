package dev.ezadb.core

data class CommandRisk(val level: Risk, val reason: String)

object RiskAssessor {
    private val high = listOf(
        Regex("""\brm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)+/(\s|$)""") to "rm_root",
        Regex("""\brm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)+/(system|vendor|product|data|boot|dev|proc|sys)(\s|/|$)""") to "rm_system",
        Regex("""\bdd\b.*\bof=/dev/""") to "dd_block",
        Regex("""\b(mkfs|fastboot|flash|wipe|format)\b""") to "flash",
        Regex("""\breboot\s+(bootloader|recovery|download|edl)\b""") to "reboot_bootloader",
        Regex("""\bpm\s+(uninstall|clear)\b""") to "pm_uninstall_clear",
        Regex("""\bsettings\s+put\s+global\s+(adb_enabled|development_settings_enabled|airplane_mode_on|hidden_api_policy)\b""") to "critical_global",
        Regex("""\bsvc\s+(wifi|data|bluetooth)\s+disable\b""") to "disable_network",
        Regex("""\bchmod\s+(-[a-zA-Z]*R[a-zA-Z]*\s+)+777\s+/(\s|$)""") to "chmod_root",
        Regex(""">\s*/dev/block""") to "overwrite_block",
    )

    private val medium = listOf(
        Regex("""\breboot\b""") to "reboot",
        Regex("""\bsettings\s+(put|delete)\b""") to "settings_change",
        Regex("""\bpm\s+(disable|disable-user|enable|install|uninstall)\b""") to "app_state",
        Regex("""\bam\s+force-stop\b""") to "force_stop",
        Regex("""\b(rm|mv|chmod|chown|kill|pkill|stop|start|mount|umount)\b""") to "fs_process",
        Regex("""\bsvc\b""") to "svc",
        Regex("""\bcmd\s+(wifi|bluetooth|phone|netd|connectivity)\b""") to "svc",
    )

    fun assess(command: String): CommandRisk {
        val normalized = command.trim()
        high.forEach { (regex, reason) -> if (regex.containsMatchIn(normalized)) return CommandRisk(Risk.HIGH, reason) }
        medium.forEach { (regex, reason) -> if (regex.containsMatchIn(normalized)) return CommandRisk(Risk.MEDIUM, reason) }
        return CommandRisk(Risk.LOW, "low")
    }
}
