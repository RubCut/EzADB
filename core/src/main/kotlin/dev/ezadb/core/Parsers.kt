package dev.ezadb.core

object Parsers {
    private val packageLine = Regex("""^package:(.+)=([\w.]+)(?:\s+uid:(\d+))?(?:\s+versionCode:(\d+))?.*$""")
    private val settingLine = Regex("""^([^=]+)=(.*)$""")
    private val wmSize = Regex("""(\d+)\s*x\s*(\d+)""")
    private val ipv4 = Regex("""\b(?:src|inet)\s+(\d{1,3}(?:\.\d{1,3}){3})\b""")

    fun getprop(output: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("[")) return@forEach
            val sep = line.indexOf("]: [")
            if (sep <= 1) return@forEach
            val key = line.substring(1, sep)
            val value = line.substring(sep + 4).removeSuffix("]")
            if (key.isNotBlank()) result[key] = value
        }
        return result
    }

    fun packageList(output: String, systemPackages: Set<String> = emptySet(), disabledPackages: Set<String> = emptySet()): List<AppInfo> {
        val apps = linkedMapOf<String, AppInfo>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("package:")) return@forEach
            val match = packageLine.find(line) ?: return@forEach
            val (path, pkg, uid, versionCode) = match.destructured
            apps[pkg] = AppInfo(
                packageName = pkg,
                apkPath = path.ifBlank { null },
                uid = uid.toIntOrNull(),
                versionCode = versionCode.toLongOrNull(),
                system = pkg in systemPackages,
                enabled = pkg !in disabledPackages,
            )
        }
        return apps.values.toList()
    }

    /**
     * Parses the tab separated output of the on-device helper:
     * package, label, versionName, versionCode, system, enabled, size, apkPath, uid.
     */
    fun helperApps(output: String): List<AppInfo> {
        val apps = linkedMapOf<String, AppInfo>()
        output.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\n', '\r')
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val fields = line.split('\t')
            if (fields.size < 4) return@forEach
            val pkg = fields[0].trim()
            if (pkg.isEmpty() || !pkg.contains('.')) return@forEach
            val label = fields[1].trim()
            apps[pkg] = AppInfo(
                packageName = pkg,
                label = label.ifBlank { pkg },
                versionName = fields[2].trim().ifBlank { null },
                versionCode = fields[3].trim().toLongOrNull(),
                system = fields.getOrNull(4)?.trim() == "1",
                enabled = fields.getOrNull(5)?.trim() != "0",
                sizeBytes = fields.getOrNull(6)?.trim()?.toLongOrNull()?.takeIf { it > 0 },
                apkPath = fields.getOrNull(7)?.trim()?.ifBlank { null },
                uid = fields.getOrNull(8)?.trim()?.toIntOrNull(),
            )
        }
        return apps.values.toList()
    }

    fun packageNames(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").substringBefore(' ').trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun settingsList(output: String): List<Pair<String, String>> =
        output.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank() || line == "null") return@mapNotNull null
            val match = settingLine.find(line) ?: return@mapNotNull null
            val (key, value) = match.destructured
            if (key.isBlank()) null else key to value
        }.toList()

    fun settingValue(output: String): String? {
        val value = output.trim()
        return if (value.isBlank() || value == "null") null else value
    }

    fun battery(output: String): BatteryInfo {
        val fields = mutableMapOf<String, String>()
        output.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) fields[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return BatteryInfo(
            level = fields["level"]?.toIntOrNull(),
            status = batteryStatus(fields["status"]),
            temperatureC = fields["temperature"]?.toIntOrNull()?.div(10.0),
            health = batteryHealth(fields["health"]),
            plugged = plugged(fields["plugged"]),
        )
    }

    private fun batteryStatus(code: String?): String = when (code) {
        "1" -> "Неизвестно"
        "2" -> "Заряжается"
        "3" -> "Разряжается"
        "4" -> "Не заряжается"
        "5" -> "Заряжен"
        else -> "—"
    }

    private fun batteryHealth(code: String?): String = when (code) {
        "1" -> "Неизвестно"
        "2" -> "Хорошее"
        "3" -> "Перегрев"
        "4" -> "Неисправен"
        "5" -> "Перенапряжение"
        "6" -> "Ошибка"
        "7" -> "Холодный"
        else -> "—"
    }

    private fun plugged(code: String?): String = when (code) {
        "0" -> "Не подключено"
        "1" -> "AC"
        "2" -> "USB"
        "4" -> "Беспроводная"
        else -> "—"
    }

    fun storage(output: String): StorageInfo {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val dataLine = lines.lastOrNull { it.contains("/data") } ?: lines.lastOrNull() ?: return StorageInfo()
        val parts = dataLine.split(Regex("\\s+"))
        if (parts.size < 4) return StorageInfo()
        val multiplier = if (parts[0].contains("1K", true) || dataLine.contains("1K-blocks")) 1024L else 1024L
        val total = parts.getOrNull(1)?.toLongOrNull()?.times(multiplier)
        val free = parts.getOrNull(3)?.toLongOrNull()?.times(multiplier)
        return StorageInfo(totalBytes = total, freeBytes = free)
    }

    fun uptime(output: String): Long? = output.trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()?.toLong()

    fun wmSize(output: String): Pair<Int, Int>? {
        val match = wmSize.find(output) ?: return null
        val (w, h) = match.destructured
        return w.toIntOrNull()?.let { width -> h.toIntOrNull()?.let { height -> width to height } }
    }

    fun wmDensity(output: String): Int? = Regex("""(\d+)""").find(output)?.value?.toIntOrNull()

    fun ipAddresses(output: String): List<String> =
        ipv4.findAll(output).map { it.groupValues[1] }
            .filter { it != "127.0.0.1" && it != "0.0.0.0" && !it.startsWith("169.254.") }
            .distinct().toList()

    fun version(output: String): Pair<String?, Long?> {
        val name = Regex("""versionName=(\S+)""").find(output)?.groupValues?.get(1)
        val code = Regex("""versionCode=(\d+)""").find(output)?.groupValues?.get(1)?.toLongOrNull()
        return name to code
    }

    fun enabledState(output: String): Boolean? {
        val state = Regex("""enabled=(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
        return when (state) {
            0 -> null // default
            1 -> true
            2, 3 -> false
            4 -> false
            else -> null
        }
    }

    fun lsLa(output: String, parentPath: String): List<RemoteFile> {
        val result = mutableListOf<RemoteFile>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("total ")) return@forEach
            val parts = line.split(Regex("\\s+"), limit = 9)
            if (parts.size < 9) return@forEach
            val perms = parts[0]
            val name = parts[8].substringBefore(" -> ")
            if (name == "." || name == "..") return@forEach
            val isDir = perms.startsWith("d")
            val isLink = perms.startsWith("l")
            result += RemoteFile(
                name = name,
                path = PathUtils.join(parentPath, name),
                isDirectory = isDir,
                isSymlink = isLink,
                isRegular = !isDir && !isLink,
                sizeBytes = parts[4].toLongOrNull() ?: 0,
                permissions = perms,
            )
        }
        return result
    }

    fun modeToPermissions(mode: Int): String {
        val type = when (mode and 0xF000) {
            0x4000 -> "d"
            0xA000 -> "l"
            0x8000 -> "-"
            0x1000 -> "p"
            0xC000 -> "s"
            0x6000 -> "b"
            0x2000 -> "c"
            else -> "-"
        }
        fun triplet(shift: Int): String {
            val bits = (mode shr shift) and 7
            return buildString {
                append(if (bits and 4 != 0) "r" else "-")
                append(if (bits and 2 != 0) "w" else "-")
                append(if (bits and 1 != 0) "x" else "-")
            }
        }
        return type + triplet(6) + triplet(3) + triplet(0)
    }
}
