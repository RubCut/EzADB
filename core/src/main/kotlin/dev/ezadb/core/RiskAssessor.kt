package dev.ezadb.core

data class CommandRisk(val level: Risk, val reason: String)

object RiskAssessor {
    private val high = listOf(
        Regex("""\brm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)+/(\s|$)""") to "Рекурсивное удаление корневого каталога",
        Regex("""\brm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)+/(system|vendor|product|data|boot|dev|proc|sys)(\s|/|$)""") to "Удаление системного раздела",
        Regex("""\bdd\b.*\bof=/dev/""") to "Запись на блочное устройство",
        Regex("""\b(mkfs|fastboot|flash|wipe|format)\b""") to "Прошивка/форматирование",
        Regex("""\breboot\s+(bootloader|recovery|download|edl)\b""") to "Перезагрузка в загрузчик/recovery",
        Regex("""\bpm\s+(uninstall|clear)\b""") to "Удаление приложения или очистка данных",
        Regex("""\bsettings\s+put\s+global\s+(adb_enabled|development_settings_enabled|airplane_mode_on|hidden_api_policy)\b""") to "Критический глобальный параметр",
        Regex("""\bsvc\s+(wifi|data|bluetooth)\s+disable\b""") to "Отключение сетевого интерфейса",
        Regex("""\bchmod\s+(-[a-zA-Z]*R[a-zA-Z]*\s+)+777\s+/(\s|$)""") to "Опасные права на корень",
        Regex(""">\s*/dev/block""") to "Перезапись блочного устройства",
    )

    private val medium = listOf(
        Regex("""\breboot\b""") to "Перезагрузка устройства",
        Regex("""\bsettings\s+(put|delete)\b""") to "Изменение настроек устройства",
        Regex("""\bpm\s+(disable|disable-user|enable|install|uninstall)\b""") to "Изменение состояния приложения",
        Regex("""\bam\s+force-stop\b""") to "Принудительная остановка приложения",
        Regex("""\b(rm|mv|chmod|chown|kill|pkill|stop|start|mount|umount)\b""") to "Изменение файловой системы или процессов",
        Regex("""\bsvc\b""") to "Управление системными сервисами",
        Regex("""\bcmd\s+(wifi|bluetooth|phone|netd|connectivity)\b""") to "Управление системными сервисами",
    )

    fun assess(command: String): CommandRisk {
        val normalized = command.trim()
        high.forEach { (regex, reason) -> if (regex.containsMatchIn(normalized)) return CommandRisk(Risk.HIGH, reason) }
        medium.forEach { (regex, reason) -> if (regex.containsMatchIn(normalized)) return CommandRisk(Risk.MEDIUM, reason) }
        return CommandRisk(Risk.LOW, "Команда без распознанных разрушительных шаблонов")
    }
}
