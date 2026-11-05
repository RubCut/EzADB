package dev.ezadb.data

import dev.ezadb.core.AppInfo
import dev.ezadb.core.BatteryInfo
import dev.ezadb.core.ConnectionKind
import dev.ezadb.core.DeviceInfo
import dev.ezadb.core.Parsers
import dev.ezadb.core.PathUtils
import dev.ezadb.core.QuickToggleState
import dev.ezadb.core.RemoteFile
import dev.ezadb.core.SettingEntry
import dev.ezadb.core.SettingNamespace
import dev.ezadb.core.SettingType
import dev.ezadb.core.SettingsCatalog
import dev.ezadb.core.ShellResult
import dev.ezadb.core.StorageInfo

/** In-memory device used only when the user explicitly enables demo mode. */
class DemoBackend {
    private data class Node(
        val name: String,
        val path: String,
        val directory: Boolean,
        var content: ByteArray = ByteArray(0),
        var mode: Int = if (directory) 0x4000 or 0b111101101 else 0x8000 or 0b110100100,
        var mtime: Long = System.currentTimeMillis() / 1000,
    )

    private val nodes = linkedMapOf<String, Node>()
    private val apps = mutableListOf<AppInfo>()
    private val settings = mutableMapOf<SettingNamespace, MutableMap<String, String>>()
    private val toggles = linkedMapOf<String, Boolean>()

    init {
        fun dir(path: String) { nodes[path] = Node(PathUtils.basename(path), path, true) }
        fun file(path: String, text: String) {
            nodes[path] = Node(PathUtils.basename(path), path, false, text.toByteArray(), mtime = System.currentTimeMillis() / 1000 - 3600)
        }
        dir("/")
        dir("/sdcard")
        dir("/sdcard/Download")
        dir("/sdcard/DCIM")
        dir("/sdcard/Documents")
        dir("/sdcard/Android")
        dir("/sdcard/Android/data")
        dir("/system")
        dir("/data")
        dir("/data/local")
        dir("/data/local/tmp")
        file("/sdcard/Download/welcome.txt", "Добро пожаловать в EzADB!\nЭто демонстрационный файл.\nПодключите реальное устройство, чтобы работать с его файлами.\n")
        file("/sdcard/Documents/notes.md", "# Заметки\n\n- Проверить ADB\n- Обновить настройки\n- Сделать скриншот\n")
        file("/sdcard/Download/script.sh", "#!/system/bin/sh\necho \"Hello from EzADB\"\ngetprop ro.product.model\n")
        file("/system/build.prop", "ro.product.model=Pixel 8\nro.build.version.release=15\nro.build.version.sdk=35\nro.product.manufacturer=Google\n")
        file("/data/local/tmp/readme.txt", "Временные файлы ADB обычно живут здесь.\n")
        nodes["/sdcard/DCIM/photo.jpg"] = Node("photo.jpg", "/sdcard/DCIM/photo.jpg", false, ByteArray(2048) { it.toByte() })

        apps.addAll(listOf(
            AppInfo("com.android.settings", "Настройки", "/system/priv-app/Settings/Settings.apk", 1000, "15", 35, true, true, 18_000_000),
            AppInfo("com.android.chrome", "Chrome", "/data/app/com.android.chrome-1/base.apk", 10123, "130.0.6723.58", 672305801, false, true, 210_000_000),
            AppInfo("com.google.android.gms", "Сервисы Google Play", "/data/app/com.google.android.gms-1/base.apk", 10080, "24.40.13", 244013000, true, true, 320_000_000),
            AppInfo("org.telegram.messenger", "Telegram", "/data/app/org.telegram.messenger-1/base.apk", 10201, "11.2.3", 112030, false, true, 95_000_000),
            AppInfo("com.whatsapp", "WhatsApp", "/data/app/com.whatsapp-1/base.apk", 10202, "2.24.21.78", 242178000, false, true, 120_000_000),
            AppInfo("com.spotify.music", "Spotify", "/data/app/com.spotify.music-1/base.apk", 10203, "8.9.84", 89840000, false, true, 140_000_000),
            AppInfo("com.example.gallery", "Галерея", "/data/app/com.example.gallery-1/base.apk", 10204, "3.1.0", 31000, false, true, 45_000_000),
            AppInfo("com.example.music", "Музыка", "/data/app/com.example.music-1/base.apk", 10205, "2.0.4", 20400, false, true, 32_000_000),
            AppInfo("com.example.notes", "Заметки", "/data/app/com.example.notes-1/base.apk", 10206, "1.8.2", 18200, false, true, 18_000_000),
            AppInfo("com.example.filemanager", "Файлы", "/data/app/com.example.filemanager-1/base.apk", 10207, "4.2.1", 42100, false, true, 24_000_000),
            AppInfo("com.android.camera2", "Камера", "/system/app/Camera2/Camera2.apk", 10090, "9.0", 90000, true, true, 60_000_000),
            AppInfo("com.disabled.bloat", "Bloatware", "/system/app/Bloat/Bloat.apk", 10099, "1.0", 10000, true, false, 12_000_000),
        ))

        settings[SettingNamespace.GLOBAL] = mutableMapOf(
            "wifi_on" to "1", "bluetooth_on" to "0", "airplane_mode_on" to "0", "mobile_data" to "1",
            "adb_enabled" to "1", "development_settings_enabled" to "1", "stay_on_while_plugged_in" to "0",
            "window_animation_scale" to "1", "transition_animation_scale" to "1", "animator_duration_scale" to "1",
            "auto_time" to "1", "auto_time_zone" to "1", "low_power" to "0",
        )
        settings[SettingNamespace.SECURE] = mutableMapOf("location_mode" to "3", "backup_enabled" to "1", "screensaver_enabled" to "0")
        settings[SettingNamespace.SYSTEM] = mutableMapOf(
            "accelerometer_rotation" to "1", "screen_brightness" to "128", "screen_brightness_mode" to "1",
            "screen_off_timeout" to "60000", "haptic_feedback_enabled" to "1", "sound_effects_enabled" to "1",
            "vibrate_when_ringing" to "1", "font_scale" to "1", "show_touches" to "0", "pointer_location" to "0",
        )
        toggles.putAll(
            mapOf(
                "wifi" to true, "bluetooth" to false, "airplane" to false, "mobile_data" to true,
                "location" to true, "auto_rotate" to true, "stay_awake" to false, "dark_mode" to false,
                "animations" to true, "screen" to true, "show_touches" to false, "pointer_location" to false,
                "haptic" to true, "auto_brightness" to true,
            )
        )
    }

    fun deviceInfo(): DeviceInfo = DeviceInfo(
        model = "Pixel 8",
        manufacturer = "Google",
        androidVersion = "15",
        sdkInt = 35,
        buildId = "AP4A.250105.002",
        securityPatch = "2025-10-05",
        serial = "DEMO0123456789",
        abi = "arm64-v8a",
        battery = BatteryInfo(87, "Заряжается", 31.0, "Хорошее", "USB"),
        storage = StorageInfo(128_000_000_000L, 54_000_000_000L),
        uptimeSeconds = 2 * 86400 + 3 * 3600 + 12 * 60,
        ipAddresses = listOf("192.168.1.42"),
        screenWidth = 1080,
        screenHeight = 2400,
        densityDpi = 420,
        suAvailable = false,
        connectionKind = ConnectionKind.DEMO,
        endpointLabel = "Демо-устройство",
    )

    fun apps(): List<AppInfo> = apps.toList()

    fun setAppEnabled(packageName: String, enabled: Boolean): String {
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index >= 0) apps[index] = apps[index].copy(enabled = enabled)
        return "Package $packageName new state: ${if (enabled) "enabled" else "disabled-user"}"
    }

    fun uninstall(packageName: String): String {
        apps.removeAll { it.packageName == packageName }
        return "Success"
    }

    fun clearData(packageName: String): String = "Success"
    fun forceStop(packageName: String): String = ""

    fun listFiles(path: String): List<RemoteFile> {
        val parent = PathUtils.normalize(path)
        return nodes.values.filter { PathUtils.parent(it.path) == parent && it.path != parent }.map { it.toRemote() }
    }

    fun stat(path: String): RemoteFile = nodes[PathUtils.normalize(path)]?.toRemote() ?: throw NoSuchElementException("Нет такого файла: $path")

    fun readFile(path: String): ByteArray = nodes[PathUtils.normalize(path)]?.content ?: throw NoSuchElementException("Нет такого файла: $path")

    fun writeFile(path: String, bytes: ByteArray) {
        val normalized = PathUtils.normalize(path)
        val existing = nodes[normalized]
        if (existing != null) {
            existing.content = bytes
            existing.mtime = System.currentTimeMillis() / 1000
        } else {
            nodes[normalized] = Node(PathUtils.basename(normalized), normalized, false, bytes)
        }
    }

    fun mkdir(path: String) {
        val normalized = PathUtils.normalize(path)
        nodes[normalized] = Node(PathUtils.basename(normalized), normalized, true)
    }

    fun delete(path: String) {
        val normalized = PathUtils.normalize(path)
        nodes.keys.filter { it == normalized || it.startsWith("$normalized/") }.forEach { nodes.remove(it) }
    }

    fun rename(oldPath: String, newPath: String) {
        val old = PathUtils.normalize(oldPath)
        val new = PathUtils.normalize(newPath)
        val node = nodes.remove(old) ?: throw NoSuchElementException(oldPath)
        nodes[new] = node.copy(name = PathUtils.basename(new), path = new)
    }

    fun listSettings(namespace: SettingNamespace): List<SettingEntry> {
        val actual = settings[namespace].orEmpty()
        val entries = linkedMapOf<String, SettingEntry>()
        SettingsCatalog.forNamespace(namespace).forEach { def ->
            entries[def.key] = SettingEntry(namespace, def.key, actual[def.key], def.type, def.description, def.risk, def.presets)
        }
        actual.forEach { (key, value) ->
            if (key !in entries) entries[key] = SettingEntry(namespace, key, value, SettingType.STRING, "Пользовательский параметр")
        }
        return entries.values.sortedBy { it.key }
    }

    fun putSetting(namespace: SettingNamespace, key: String, value: String) {
        settings.getOrPut(namespace) { mutableMapOf() }[key] = value
        when (key) {
            "wifi_on" -> toggles["wifi"] = value == "1"
            "bluetooth_on" -> toggles["bluetooth"] = value == "1"
            "airplane_mode_on" -> toggles["airplane"] = value == "1"
            "mobile_data" -> toggles["mobile_data"] = value == "1"
            "location_mode" -> toggles["location"] = value != "0"
            "accelerometer_rotation" -> toggles["auto_rotate"] = value == "1"
            "stay_on_while_plugged_in" -> toggles["stay_awake"] = value != "0"
            "window_animation_scale" -> toggles["animations"] = value.toFloatOrNull()?.let { it > 0 } ?: true
            "show_touches" -> toggles["show_touches"] = value == "1"
            "pointer_location" -> toggles["pointer_location"] = value == "1"
            "haptic_feedback_enabled" -> toggles["haptic"] = value == "1"
            "screen_brightness_mode" -> toggles["auto_brightness"] = value == "1"
        }
    }

    fun deleteSetting(namespace: SettingNamespace, key: String) {
        settings[namespace]?.remove(key)
    }

    fun toggles(): List<QuickToggleState> = toggles.map { (id, value) ->
        QuickToggleState(id, toggleTitle(id), toggleSubtitle(id), value)
    }

    fun setToggle(id: String, enabled: Boolean): String {
        toggles[id] = enabled
        when (id) {
            "wifi" -> putSetting(SettingNamespace.GLOBAL, "wifi_on", if (enabled) "1" else "0")
            "bluetooth" -> putSetting(SettingNamespace.GLOBAL, "bluetooth_on", if (enabled) "1" else "0")
            "airplane" -> putSetting(SettingNamespace.GLOBAL, "airplane_mode_on", if (enabled) "1" else "0")
            "mobile_data" -> putSetting(SettingNamespace.GLOBAL, "mobile_data", if (enabled) "1" else "0")
            "location" -> putSetting(SettingNamespace.SECURE, "location_mode", if (enabled) "3" else "0")
            "auto_rotate" -> putSetting(SettingNamespace.SYSTEM, "accelerometer_rotation", if (enabled) "1" else "0")
            "stay_awake" -> putSetting(SettingNamespace.GLOBAL, "stay_on_while_plugged_in", if (enabled) "7" else "0")
            "dark_mode" -> Unit
            "animations" -> listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach {
                putSetting(SettingNamespace.GLOBAL, it, if (enabled) "1" else "0")
            }
            "screen" -> Unit
            "show_touches" -> putSetting(SettingNamespace.SYSTEM, "show_touches", if (enabled) "1" else "0")
            "pointer_location" -> putSetting(SettingNamespace.SYSTEM, "pointer_location", if (enabled) "1" else "0")
            "haptic" -> putSetting(SettingNamespace.SYSTEM, "haptic_feedback_enabled", if (enabled) "1" else "0")
            "auto_brightness" -> putSetting(SettingNamespace.SYSTEM, "screen_brightness_mode", if (enabled) "1" else "0")
        }
        return "OK"
    }

    fun shell(command: String): ShellResult {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return ShellResult()
        val parts = trimmed.split(Regex("\\s+"))
        return when (parts[0]) {
            "echo" -> ShellResult(trimmed.removePrefix("echo").trim().trim('"', '\'') + "\n")
            "pwd" -> ShellResult("/sdcard\n")
            "whoami" -> ShellResult("shell\n")
            "id" -> ShellResult("uid=2000(shell) gid=2000(shell) groups=2000(shell)\n")
            "uname" -> ShellResult("Linux localhost 6.1.99-android14-11 #1 SMP PREEMPT aarch64 Toybox\n")
            "getprop" -> {
                val props = demoProps()
                if (parts.size > 1) ShellResult((props[parts[1]] ?: "") + "\n")
                else ShellResult(props.entries.joinToString("\n") { "[${it.key}]: [${it.value}]" } + "\n")
            }
            "pm" -> when {
                trimmed.startsWith("pm list packages") -> ShellResult(apps.joinToString("\n") { "package:${it.packageName}" } + "\n")
                trimmed.startsWith("pm path") -> ShellResult(apps.firstOrNull { trimmed.contains(it.packageName) }?.let { "package:${it.apkPath}\n" } ?: "")
                trimmed.startsWith("pm enable") || trimmed.startsWith("pm disable") -> ShellResult("Success\n")
                trimmed.startsWith("pm uninstall") || trimmed.startsWith("pm clear") -> ShellResult("Success\n")
                else -> ShellResult("Success\n")
            }
            "settings" -> demoSettings(parts)
            "ls" -> {
                val path = parts.lastOrNull()?.takeIf { it.startsWith("/") } ?: "/sdcard"
                runCatching { ShellResult(listFiles(path).joinToString("\n") { "${it.permissions} shell shell ${it.sizeBytes} ${it.name}" } + "\n") }
                    .getOrElse { ShellResult("ls: $path: No such file or directory\n", exitCode = 1) }
            }
            "cat" -> {
                val path = parts.getOrNull(1) ?: return ShellResult("cat: missing operand\n", exitCode = 1)
                if (path == "/proc/uptime") return ShellResult("183120.45 98765.43\n")
                runCatching { ShellResult(String(readFile(path))) }.getOrElse { ShellResult("cat: $path: No such file or directory\n", exitCode = 1) }
            }
            "df" -> ShellResult("Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-0 125000000 71000000 54000000 57% /data\n")
            "dumpsys" -> if (parts.getOrNull(1) == "battery") ShellResult("Current Battery Service state:\n  level: 87\n  status: 2\n  health: 2\n  plugged: 2\n  temperature: 310\n") else ShellResult("Демо: dumpsys ${parts.getOrNull(1) ?: ""}\n")
            "wm" -> when (parts.getOrNull(1)) {
                "size" -> ShellResult("Physical size: 1080x2400\n")
                "density" -> ShellResult("Physical density: 420\n")
                else -> ShellResult("")
            }
            "ip" -> ShellResult("default via 192.168.1.1 dev wlan0 src 192.168.1.42\n")
            "uptime" -> ShellResult("183120.45 98765.43\n")
            "svc" -> ShellResult("")
            "cmd" -> if (trimmed.contains("uimode night")) ShellResult("Night mode: ${if (toggles["dark_mode"] == true) "yes" else "no"}\n") else ShellResult("")
            "input" -> ShellResult("")
            "reboot" -> ShellResult("Демо: устройство было бы перезагружено\n")
            "screencap" -> ShellResult("Демо: скриншот сохранён\n")
            "help" -> ShellResult(
                """
                EzADB demo shell. Попробуйте:
                  getprop ro.product.model
                  pm list packages
                  settings list global
                  ls /sdcard
                  cat /sdcard/Download/welcome.txt
                  dumpsys battery
                  wm size
                """.trimIndent() + "\n"
            )
            else -> ShellResult("sh: ${parts[0]}: not found\n", exitCode = 127)
        }
    }

    private fun demoSettings(parts: List<String>): ShellResult {
        val ns = parts.getOrNull(2)?.let { SettingNamespace.fromAdb(it) } ?: return ShellResult("Invalid namespace\n", exitCode = 1)
        val map = settings[ns].orEmpty()
        return when (parts.getOrNull(1)) {
            "list" -> ShellResult(map.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
            "get" -> ShellResult((map[parts.getOrNull(3)] ?: "null") + "\n")
            "put" -> {
                val key = parts.getOrNull(3) ?: return ShellResult("Missing key\n", exitCode = 1)
                val value = parts.getOrNull(4) ?: return ShellResult("Missing value\n", exitCode = 1)
                putSetting(ns, key, value)
                ShellResult()
            }
            "delete" -> {
                parts.getOrNull(3)?.let { deleteSetting(ns, it) }
                ShellResult()
            }
            else -> ShellResult("Unknown settings operation\n", exitCode = 1)
        }
    }

    private fun demoProps(): Map<String, String> = mapOf(
        "ro.product.model" to "Pixel 8",
        "ro.product.manufacturer" to "Google",
        "ro.build.version.release" to "15",
        "ro.build.version.sdk" to "35",
        "ro.build.version.security_patch" to "2025-10-05",
        "ro.build.display.id" to "AP4A.250105.002",
        "ro.serialno" to "DEMO0123456789",
        "ro.product.cpu.abi" to "arm64-v8a",
    )

    private fun Node.toRemote(): RemoteFile = RemoteFile(
        name = name,
        path = path,
        isDirectory = directory,
        isSymlink = false,
        isRegular = !directory,
        sizeBytes = content.size.toLong(),
        mode = mode,
        mtimeSec = mtime,
        permissions = Parsers.modeToPermissions(mode),
    )

    private fun toggleTitle(id: String): String = when (id) {
        "wifi" -> "Wi-Fi"; "bluetooth" -> "Bluetooth"; "airplane" -> "Режим полёта"
        "mobile_data" -> "Мобильные данные"; "location" -> "Геолокация"; "auto_rotate" -> "Автоповорот"
        "stay_awake" -> "Не спать"; "dark_mode" -> "Тёмная тема"; "animations" -> "Анимации"
        "screen" -> "Экран"; "show_touches" -> "Касания"; "pointer_location" -> "Координаты"
        "haptic" -> "Виброотклик"; "auto_brightness" -> "Автояркость"; else -> id
    }

    private fun toggleSubtitle(id: String): String = when (id) {
        "wifi" -> "Беспроводная сеть"; "bluetooth" -> "Bluetooth-адаптер"; "airplane" -> "Отключает радиомодули"
        "mobile_data" -> "Передача данных SIM"; "location" -> "Службы геолокации"; "auto_rotate" -> "Поворот экрана"
        "stay_awake" -> "Экран при зарядке"; "dark_mode" -> "Ночной режим"; "animations" -> "Системные анимации"
        "screen" -> "Вкл/выкл дисплей"; "show_touches" -> "Точки касаний"; "pointer_location" -> "Оверлей координат"
        "haptic" -> "Тактильная отдача"; "auto_brightness" -> "Адаптивная яркость"; else -> ""
    }
}
