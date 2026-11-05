package dev.ezadb.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.flyfishxu.kadb.install
import dev.ezadb.R
import dev.ezadb.core.AppInfo
import dev.ezadb.core.BatchShell
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
import dev.ezadb.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException

/**
 * All device I/O.
 *
 * Reads are batched into single shell invocations (every ADB stream is a full TLS
 * round trip) and are replayed transparently when the transport drops.
 * Transfers report byte progress through [TransferHandle] and can be cancelled.
 */
class DeviceRepository(
    private val context: Context,
    private val connection: ConnectionManager,
) {
    private val helper = DeviceHelper(context, connection)
    private val labelCache = AppMetaCache(context)

    fun onConnectionChanged() {
        helper.reset()
    }

    suspend fun shell(command: String): ShellResult = connection.use { kadb ->
        val response = kadb.shell(command)
        ShellResult(response.output, response.errorOutput, response.exitCode)
    }

    /** Idempotent read: safe to replay after a reconnect. */
    private suspend fun read(command: String): ShellResult = connection.use(retryable = true) { kadb ->
        val response = kadb.shell(command)
        ShellResult(response.output, response.errorOutput, response.exitCode)
    }

    private suspend fun readBatch(commands: List<String>): List<String> =
        BatchShell.split(read(BatchShell.build(commands)).allOutput, commands.size)

    suspend fun exec(vararg args: String): ShellResult = shell(ShellQuote.command(args.toList()))

    suspend fun execChecked(vararg args: String): String {
        val result = exec(*args)
        if (!result.success) throw IOException(result.allOutput.ifBlank { context.getString(R.string.command_failed, result.exitCode) })
        return result.output
    }

    suspend fun deviceInfo(): DeviceInfo {
        val sections = readBatch(
            listOf(
                "getprop",
                "dumpsys battery",
                "df /data",
                "cat /proc/uptime",
                "wm size",
                "wm density",
                "ip route; ip -4 addr show",
                "which su",
            )
        )
        val props = Parsers.getprop(sections[0])
        val size = Parsers.wmSize(sections[4])
        val state = connection.state.value
        val kind = (state as? dev.ezadb.core.ConnectionState.Connected)?.device?.connectionKind ?: dev.ezadb.core.ConnectionKind.TCP
        val label = (state as? dev.ezadb.core.ConnectionState.Connected)?.device?.endpointLabel ?: ""
        return DeviceInfo(
            model = props["ro.product.model"] ?: "Android",
            manufacturer = props["ro.product.manufacturer"] ?: "",
            androidVersion = props["ro.build.version.release"] ?: "",
            sdkInt = props["ro.build.version.sdk"]?.toIntOrNull() ?: 0,
            buildId = props["ro.build.display.id"] ?: props["ro.build.id"] ?: "",
            securityPatch = props["ro.build.version.security_patch"] ?: "",
            serial = props["ro.serialno"] ?: "",
            abi = props["ro.product.cpu.abi"] ?: "",
            battery = Parsers.battery(sections[1]),
            storage = Parsers.storage(sections[2]),
            uptimeSeconds = Parsers.uptime(sections[3]),
            ipAddresses = Parsers.ipAddresses(sections[6]),
            screenWidth = size?.first,
            screenHeight = size?.second,
            densityDpi = Parsers.wmDensity(sections[5]),
            suAvailable = sections[7].isNotBlank(),
            connectionKind = kind,
            endpointLabel = label,
        )
    }

    // region apps
    suspend fun listApps(): List<AppInfo> {
        helper.apps()?.let { apps ->
            labelCache.remember(apps)
            return apps
        }
        val sections = readBatch(
            listOf(
                "pm list packages -f -U --show-versioncode || pm list packages -f -U",
                "pm list packages -s",
                "pm list packages -d",
            )
        )
        val apps = Parsers.packageList(
            sections[0],
            Parsers.packageNames(sections[1]),
            Parsers.packageNames(sections[2]),
        )
        return labelCache.apply(apps)
    }

    suspend fun appIcon(packageName: String): ByteArray? = helper.icon(packageName)

    suspend fun appIcons(packages: List<String>): Map<String, ByteArray> = helper.icons(packages)

    suspend fun appDetails(packageName: String): AppInfo {
        val quoted = ShellQuote.quote(packageName)
        val sections = readBatch(
            listOf(
                "dumpsys package $quoted | grep -E 'versionName|versionCode|userId=|flags=' | head -n 12",
                "pm path $packageName",
                "pm list packages -d",
                "pm list packages -s",
            )
        )
        val (versionName, versionCode) = Parsers.version(sections[0])
        val paths = sections[1].lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .toList()
        val apk = paths.firstOrNull { it.endsWith("base.apk") } ?: paths.firstOrNull()
        val size = apk?.let { runCatching { stat(it).sizeBytes }.getOrNull() }
        val disabled = Parsers.packageNames(sections[2])
        val system = Parsers.packageNames(sections[3])
        val uid = Regex("""userId=(\d+)""").find(sections[0])?.groupValues?.get(1)?.toIntOrNull()
        return AppInfo(
            packageName = packageName,
            label = labelCache.label(packageName) ?: packageName,
            apkPath = apk,
            uid = uid,
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

    suspend fun installApk(uri: Uri, handle: TransferHandle? = null) = withContext(Dispatchers.IO) {
        val document = DocumentFile.fromSingleUri(context, uri)
        var size = SafUtils.size(context, uri)
        if (size <= 0) size = document?.length() ?: -1
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException(context.getString(R.string.apk_open_failed))
        input.use { stream ->
            if (size <= 0) {
                // Streaming install needs the exact size; buffer unknown-length documents first.
                val buffer = Buffer()
                buffer.writeAll(stream.source().counted(handle))
                handle?.finish()
                connection.use { kadb -> kadb.install(buffer, buffer.size) }
            } else {
                connection.use { kadb ->
                    kadb.install(stream.source().counted(handle), size)
                }
                handle?.finish()
            }
        }
    }

    suspend fun extractApk(packageName: String, target: Uri, handle: TransferHandle? = null) {
        val paths = execChecked("pm", "path", packageName).lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .toList()
        val apk = paths.firstOrNull { it.endsWith("base.apk") } ?: paths.firstOrNull()
            ?: throw IOException(context.getString(R.string.apk_not_found))
        pullToUri(apk, target, handle)
    }
    // endregion

    // region files
    suspend fun listFiles(path: String): List<RemoteFile> = connection.use(retryable = true) { kadb ->
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

    suspend fun stat(path: String): RemoteFile = connection.use(retryable = true) { kadb ->
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

    suspend fun readFile(path: String, maxBytes: Long = 4L * 1024 * 1024, handle: TransferHandle? = null): ByteArray {
        val info = stat(path)
        if (info.sizeBytes > maxBytes) {
            throw IOException(context.getString(R.string.file_too_large, Formatters.bytes(info.sizeBytes), Formatters.bytes(maxBytes)))
        }
        return connection.use(retryable = true) { kadb ->
            val buffer = Buffer()
            kadb.pull(buffer.countedSink(handle), path)
            handle?.finish()
            buffer.readByteArray()
        }
    }

    suspend fun writeFile(path: String, bytes: ByteArray, mode: Int = 0b110100100, handle: TransferHandle? = null) =
        connection.use { kadb ->
            val buffer = Buffer().write(bytes)
            kadb.push(buffer.counted(handle), path, mode, System.currentTimeMillis())
            handle?.finish()
        }

    suspend fun mkdir(path: String): String = execChecked("mkdir", "-p", path)
    suspend fun rename(oldPath: String, newPath: String): String = execChecked("mv", oldPath, newPath)
    suspend fun delete(path: String, recursive: Boolean): String =
        if (recursive) execChecked("rm", "-rf", path) else execChecked("rm", "-f", path)
    suspend fun chmod(path: String, mode: String): String = execChecked("chmod", mode, path)

    suspend fun pullToUri(remotePath: String, target: Uri, handle: TransferHandle? = null) = connection.use { kadb ->
        val output = context.contentResolver.openOutputStream(target)
            ?: throw IOException(context.getString(R.string.file_write_open_failed))
        output.sink().buffer().use { sink ->
            kadb.pull(sink.countedSink(handle), remotePath)
            handle?.finish()
        }
    }

    suspend fun pushFromUri(source: Uri, remotePath: String, mode: Int = 0b110100100, handle: TransferHandle? = null) =
        connection.use { kadb ->
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException(context.getString(R.string.local_file_open_failed))
            input.source().buffer().use { buffered ->
                kadb.push(buffered.counted(handle), remotePath, mode, System.currentTimeMillis())
                handle?.finish()
            }
        }
    // endregion

    // region settings
    suspend fun listSettings(namespace: SettingNamespace): List<SettingEntry> {
        val actual = Parsers.settingsList(read("settings list ${namespace.adbName}").allOutput).toMap()
        val entries = linkedMapOf<String, SettingEntry>()
        SettingsCatalog.forNamespace(namespace).forEach { def -> entries[def.key] = def.toEntry(actual[def.key]) }
        actual.forEach { (key, value) ->
            if (key !in entries) {
                entries[key] = SettingEntry(namespace, key, value, SettingType.STRING, context.getString(R.string.custom_setting))
            }
        }
        return entries.values.sortedBy { it.key }
    }

    suspend fun getSetting(namespace: SettingNamespace, key: String): String? =
        Parsers.settingValue(read("settings get ${namespace.adbName} ${ShellQuote.quote(key)}").allOutput)

    /** Everything the quick toggles need, in a single shell round trip. */
    suspend fun toggleSnapshot(): ToggleSnapshot {
        val sections = readBatch(
            listOf(
                "settings list global",
                "settings list secure",
                "settings list system",
                "dumpsys power | grep -E 'mWakefulness=|Display Power' | head -n 4",
                "cmd uimode night",
            )
        )
        return ToggleSnapshot(
            settings = mapOf(
                SettingNamespace.GLOBAL to Parsers.settingsList(sections[0]).toMap(),
                SettingNamespace.SECURE to Parsers.settingsList(sections[1]).toMap(),
                SettingNamespace.SYSTEM to Parsers.settingsList(sections[2]).toMap(),
            ),
            power = sections[3],
            night = sections[4],
        )
    }

    suspend fun putSetting(namespace: SettingNamespace, key: String, value: String): String =
        execChecked("settings", "put", namespace.adbName, key, value)

    suspend fun deleteSetting(namespace: SettingNamespace, key: String): String =
        execChecked("settings", "delete", namespace.adbName, key)
    // endregion

    suspend fun reboot(mode: String = ""): String =
        if (mode.isBlank()) execChecked("reboot") else execChecked("reboot", mode)

    /** Captures the screen and returns the PNG bytes instead of writing them somewhere blind. */
    suspend fun screenshotBytes(handle: TransferHandle? = null): ByteArray {
        val remote = "/data/local/tmp/ezadb-screen-${System.currentTimeMillis()}.png"
        try {
            execChecked("screencap", "-p", remote)
            return connection.use(retryable = true) { kadb ->
                val buffer = Buffer()
                kadb.pull(buffer.countedSink(handle), remote)
                handle?.finish()
                buffer.readByteArray()
            }
        } finally {
            runCatching { delete(remote, recursive = false) }
        }
    }

    suspend fun screenshot(remotePath: String): String = execChecked("screencap", "-p", remotePath)

    suspend fun setScreenOn(on: Boolean): String =
        execChecked("input", "keyevent", if (on) "KEYCODE_WAKEUP" else "KEYCODE_SLEEP")

    suspend fun setNightMode(on: Boolean): String = execChecked("cmd", "uimode", "night", if (on) "yes" else "no")

    suspend fun nightModeOutput(): String = read("cmd uimode night").allOutput
    suspend fun nightMode(): Boolean? {
        val output = nightModeOutput().lowercase()
        return when {
            output.contains("yes") -> true
            output.contains("no") -> false
            else -> null
        }
    }

    suspend fun setAnimationScales(scale: String): String {
        shell(
            BatchShell.build(
                listOf(
                    "settings put global window_animation_scale $scale",
                    "settings put global transition_animation_scale $scale",
                    "settings put global animator_duration_scale $scale",
                )
            )
        )
        return context.getString(R.string.animation_scale, scale)
    }

    suspend fun enableWifiAdb(port: Int = 5555): String =
        shell("setprop service.adb.tcp.port $port; stop adbd; start adbd").allOutput

    private fun SettingDefinition.toEntry(value: String?): SettingEntry =
        SettingEntry(namespace, key, value, type, description, risk, presets)
}

/** Cheap device snapshot used by the quick toggle grid. */
data class ToggleSnapshot(
    val settings: Map<SettingNamespace, Map<String, String>> = emptyMap(),
    val power: String = "",
    val night: String = "",
)
