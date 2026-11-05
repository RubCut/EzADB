package dev.ezadb.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.flyfishxu.kadb.install
import com.flyfishxu.kadb.pull
import com.flyfishxu.kadb.push
import dev.ezadb.core.AppInfo
import dev.ezadb.core.BatteryInfo
import dev.ezadb.core.DeviceInfo
import dev.ezadb.core.Formatters
import dev.ezadb.core.Parsers
import dev.ezadb.core.PathUtils
import dev.ezadb.core.RemoteFile
import dev.ezadb.core.SettingDefinition
import dev.ezadb.core.SettingEntry
import dev.ezadb.core.SettingNamespace
import dev.ezadb.core.SettingType
import dev.ezadb.core.SettingsCatalog
import dev.ezadb.core.ShellQuote
import dev.ezadb.core.ShellResult
import dev.ezadb.core.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException

class DeviceRepository(
    private val context: Context,
    private val connection: ConnectionManager,
) {
    suspend fun shell(command: String): ShellResult = connection.use { kadb ->
        val response = kadb.shell(command)
        ShellResult(response.output, response.errorOutput, response.exitCode)
    }

    suspend fun exec(vararg args: String): ShellResult = shell(ShellQuote.command(args.toList()))

    suspend fun execChecked(vararg args: String): String {
        val result = exec(*args)
        if (!result.success) throw IOException(result.allOutput.ifBlank { "Команда завершилась с кодом ${result.exitCode}" })
        return result.output
    }

    suspend fun deviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        val props = Parsers.getprop(shell("getprop").allOutput)
        val battery = Parsers.battery(shell("dumpsys battery").allOutput)
        val storage = Parsers.storage(shell("df /data").allOutput)
        val uptime = Parsers.uptime(shell("cat /proc/uptime").allOutput)
        val size = Parsers.wmSize(shell("wm size").allOutput)
        val density = Parsers.wmDensity(shell("wm density").allOutput)
        val ips = Parsers.ipAddresses(shell("ip route; ip -4 addr show").allOutput)
        val su = shell("which su").let { it.success && it.output.isNotBlank() }
        val state = connection.state.value
        val kind = (state as? dev.ezadb.core.ConnectionState.Connected)?.device?.connectionKind ?: dev.ezadb.core.ConnectionKind.TCP
        val label = (state as? dev.ezadb.core.ConnectionState.Connected)?.device?.endpointLabel ?: ""
        DeviceInfo(
            model = props["ro.product.model"] ?: "Android",
            manufacturer = props["ro.product.manufacturer"] ?: "",
            androidVersion = props["ro.build.version.release"] ?: "",
            sdkInt = props["ro.build.version.sdk"]?.toIntOrNull() ?: 0,
            buildId = props["ro.build.display.id"] ?: props["ro.build.id"] ?: "",
            securityPatch = props["ro.build.version.security_patch"] ?: "",
            serial = props["ro.serialno"] ?: "",
            abi = props["ro.product.cpu.abi"] ?: "",
            battery = battery,
            storage = storage,
            uptimeSeconds = uptime,
            ipAddresses = ips,
            screenWidth = size?.first,
            screenHeight = size?.second,
            densityDpi = density,
            suAvailable = su,
            connectionKind = kind,
            endpointLabel = label,
        )
    }

    suspend fun listApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val all = exec("pm", "list", "packages", "-f", "-U", "--show-versioncode").let { result ->
            if (result.success) result.output else exec("pm", "list", "packages", "-f", "-U").output
        }
        val system = Parsers.packageNames(exec("pm", "list", "packages", "-s").allOutput)
        val disabled = Parsers.packageNames(exec("pm", "list", "packages", "-d").allOutput)
        Parsers.packageList(all, system, disabled)
    }

    suspend fun appDetails(packageName: String): AppInfo = withContext(Dispatchers.IO) {
        val dump = shell("dumpsys package ${ShellQuote.quote(packageName)}").allOutput
        val (versionName, versionCode) = Parsers.version(dump)
        val paths = execChecked("pm", "path", packageName).lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .toList()
        val apk = paths.firstOrNull { it.endsWith("base.apk") } ?: paths.firstOrNull()
        val size = apk?.let { runCatching { stat(it).sizeBytes }.getOrNull() }
        val disabled = Parsers.packageNames(exec("pm", "list", "packages", "-d").allOutput)
        val system = Parsers.packageNames(exec("pm", "list", "packages", "-s").allOutput)
        AppInfo(
            packageName = packageName,
            apkPath = apk,
            versionName = versionName,
            versionCode = versionCode,
            sizeBytes = size,
            system = packageName in system,
            enabled = packageName !in disabled,
        )
    }

    suspend fun setAppEnabled(packageName: String, enabled: Boolean): String =
        if (enabled) execChecked("pm", "enable", packageName)
        else execChecked("pm", "disable-user", "--user", "0", packageName)

    suspend fun forceStop(packageName: String): String = execChecked("am", "force-stop", packageName)
    suspend fun clearAppData(packageName: String): String = execChecked("pm", "clear", packageName)
    suspend fun uninstallApp(packageName: String): String = execChecked("pm", "uninstall", "--user", "0", packageName)
    suspend fun launchApp(packageName: String): String =
        execChecked("monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")

    suspend fun openAppSettings(packageName: String): String =
        execChecked("am", "start", "-a", "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:$packageName")

    suspend fun installApk(uri: Uri) = connection.use { kadb ->
        val document = DocumentFile.fromSingleUri(context, uri) ?: throw IOException("Не удалось открыть APK")
        kadb.install(document, context)
    }

    suspend fun extractApk(packageName: String, target: Uri) = connection.use { kadb ->
        val paths = execChecked("pm", "path", packageName).lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .toList()
        val apk = paths.firstOrNull { it.endsWith("base.apk") } ?: paths.firstOrNull() ?: throw IOException("APK не найден")
        context.contentResolver.openOutputStream(target)?.use { output ->
            output.sink().buffer().use { sink -> kadb.pull(sink, apk) }
        } ?: throw IOException("Не удалось открыть файл для записи")
    }

    suspend fun listFiles(path: String): List<RemoteFile> = connection.use { kadb ->
        val normalized = PathUtils.normalize(path)
        kadb.openSync().use { sync ->
            sync.list(normalized).mapNotNull { entry ->
                if (entry.name == "." || entry.name == "..") return@mapNotNull null
                val type = entry.mode.toInt() and 0xF000
                RemoteFile(
                    name = entry.name,
                    path = PathUtils.join(normalized, entry.name),
                    isDirectory = type == 0x4000,
                    isSymlink = type == 0xA000,
                    isRegular = type == 0x8000,
                    sizeBytes = entry.size,
                    mode = entry.mode,
                    mtimeSec = entry.mtimeSec,
                    permissions = Parsers.modeToPermissions(entry.mode),
                )
            }
        }
    }

    suspend fun stat(path: String): RemoteFile = connection.use { kadb ->
        kadb.openSync().use { sync ->
            val stat = sync.lstat(path)
            val type = stat.mode.toInt() and 0xF000
            RemoteFile(
                name = PathUtils.basename(path),
                path = PathUtils.normalize(path),
                isDirectory = type == 0x4000,
                isSymlink = type == 0xA000,
                isRegular = type == 0x8000,
                sizeBytes = stat.size,
                mode = stat.mode,
                mtimeSec = stat.mtimeSec,
                permissions = Parsers.modeToPermissions(stat.mode),
            )
        }
    }

    suspend fun readFile(path: String, maxBytes: Long = 1024 * 1024): ByteArray = connection.use { kadb ->
        val info = stat(path)
        if (info.sizeBytes > maxBytes) throw IOException("Файл слишком большой для редактора (${Formatters.bytes(info.sizeBytes)}). Лимит ${Formatters.bytes(maxBytes)}.")
        val buffer = Buffer()
        kadb.pull(buffer, path)
        buffer.readByteArray()
    }

    suspend fun writeFile(path: String, bytes: ByteArray, mode: Int = 0b110100100) = connection.use { kadb ->
        val buffer = Buffer().write(bytes)
        kadb.push(buffer, path, mode, System.currentTimeMillis())
    }

    suspend fun mkdir(path: String): String = execChecked("mkdir", "-p", path)
    suspend fun rename(oldPath: String, newPath: String): String = execChecked("mv", oldPath, newPath)
    suspend fun delete(path: String, recursive: Boolean): String =
        if (recursive) execChecked("rm", "-rf", path) else execChecked("rm", "-f", path)
    suspend fun chmod(path: String, mode: String): String = execChecked("chmod", mode, path)

    suspend fun pullToUri(remotePath: String, target: Uri) = connection.use { kadb ->
        context.contentResolver.openOutputStream(target)?.use { output ->
            output.sink().buffer().use { sink -> kadb.pull(sink, remotePath) }
        } ?: throw IOException("Не удалось открыть файл для записи")
    }

    suspend fun pushFromUri(source: Uri, remotePath: String, mode: Int = 0b110100100) = connection.use { kadb ->
        context.contentResolver.openInputStream(source)?.use { input ->
            input.source().buffer().use { buffered ->
                kadb.push(buffered, remotePath, mode, System.currentTimeMillis())
            }
        } ?: throw IOException("Не удалось открыть локальный файл")
    }

    suspend fun listSettings(namespace: SettingNamespace): List<SettingEntry> {
        val output = execChecked("settings", "list", namespace.adbName)
        val actual = Parsers.settingsList(output).toMap()
        val entries = linkedMapOf<String, SettingEntry>()
        SettingsCatalog.forNamespace(namespace).forEach { def ->
            entries[def.key] = def.toEntry(actual[def.key])
        }
        actual.forEach { (key, value) ->
            if (key !in entries) {
                entries[key] = SettingEntry(namespace, key, value, SettingType.STRING, "Пользовательский параметр")
            }
        }
        return entries.values.sortedBy { it.key }
    }

    suspend fun getSetting(namespace: SettingNamespace, key: String): String? =
        Parsers.settingValue(execChecked("settings", "get", namespace.adbName, key))

    suspend fun putSetting(namespace: SettingNamespace, key: String, value: String): String =
        execChecked("settings", "put", namespace.adbName, key, value)

    suspend fun deleteSetting(namespace: SettingNamespace, key: String): String =
        execChecked("settings", "delete", namespace.adbName, key)

    suspend fun reboot(mode: String = ""): String =
        if (mode.isBlank()) execChecked("reboot") else execChecked("reboot", mode)

    suspend fun screenshot(remotePath: String): String = execChecked("screencap", "-p", remotePath)

    suspend fun setScreenOn(on: Boolean): String =
        execChecked("input", "keyevent", if (on) "KEYCODE_WAKEUP" else "KEYCODE_SLEEP")

    suspend fun setNightMode(on: Boolean): String = execChecked("cmd", "uimode", "night", if (on) "yes" else "no")
    suspend fun nightMode(): Boolean? {
        val output = shell("cmd uimode night").allOutput.lowercase()
        return when {
            output.contains("yes") -> true
            output.contains("no") -> false
            else -> null
        }
    }

    suspend fun setAnimationScales(scale: String): String {
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach { key ->
            putSetting(SettingNamespace.GLOBAL, key, scale)
        }
        return "Масштаб анимаций: $scale"
    }

    suspend fun enableWifiAdb(port: Int = 5555): String =
        shell("setprop service.adb.tcp.port $port; stop adbd; start adbd").allOutput

    private fun SettingDefinition.toEntry(value: String?): SettingEntry =
        SettingEntry(namespace, key, value, type, description, risk, presets)
}
