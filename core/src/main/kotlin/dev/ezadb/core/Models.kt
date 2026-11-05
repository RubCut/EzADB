package dev.ezadb.core

enum class ConnectionKind { USB, TCP, WIRELESS, DEMO }

data class Endpoint(
    val host: String,
    val port: Int,
    val label: String = "$host:$port",
    val kind: ConnectionKind = ConnectionKind.TCP,
)

data class DeviceInfo(
    val model: String = "—",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val buildId: String = "",
    val securityPatch: String = "",
    val serial: String = "",
    val abi: String = "",
    val battery: BatteryInfo = BatteryInfo(),
    val storage: StorageInfo = StorageInfo(),
    val uptimeSeconds: Long? = null,
    val ipAddresses: List<String> = emptyList(),
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val densityDpi: Int? = null,
    val suAvailable: Boolean = false,
    val connectionKind: ConnectionKind = ConnectionKind.TCP,
    val endpointLabel: String = "",
)

data class BatteryInfo(
    val level: Int? = null,
    val status: String = "—",
    val temperatureC: Double? = null,
    val health: String = "—",
    val plugged: String = "—",
)

data class StorageInfo(
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
) {
    val usedBytes: Long? get() = if (totalBytes != null && freeBytes != null) totalBytes - freeBytes else null
    val usedFraction: Float?
        get() = if (totalBytes != null && totalBytes > 0 && freeBytes != null) {
            ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
        } else null
}

data class AppInfo(
    val packageName: String,
    val label: String = packageName,
    val apkPath: String? = null,
    val uid: Int? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val system: Boolean = false,
    val enabled: Boolean = true,
    val sizeBytes: Long? = null,
)

enum class AppFilter { ALL, USER, SYSTEM, DISABLED }
enum class AppSort { NAME, PACKAGE, SIZE, UID }

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val isRegular: Boolean = true,
    val sizeBytes: Long = 0,
    val mode: Int = 0,
    val mtimeSec: Long = 0,
    val permissions: String = "",
    val uid: Int? = null,
    val gid: Int? = null,
)

enum class FileSort { NAME, SIZE, MTIME, TYPE }

enum class SettingNamespace(val adbName: String) {
    GLOBAL("global"), SECURE("secure"), SYSTEM("system");
    companion object {
        fun fromAdb(value: String): SettingNamespace? = entries.firstOrNull { it.adbName == value.lowercase() }
    }
}

enum class SettingType { INT, FLOAT, STRING, BOOL }
enum class Risk { LOW, MEDIUM, HIGH }

data class SettingDefinition(
    val namespace: SettingNamespace,
    val key: String,
    val type: SettingType = SettingType.STRING,
    val description: String = "",
    val risk: Risk = Risk.LOW,
    val presets: List<String> = emptyList(),
)

data class SettingEntry(
    val namespace: SettingNamespace,
    val key: String,
    val value: String?,
    val type: SettingType = SettingType.STRING,
    val description: String = "",
    val risk: Risk = Risk.LOW,
    val presets: List<String> = emptyList(),
) {
    val definition: SettingDefinition get() = SettingDefinition(namespace, key, type, description, risk, presets)
}

data class QuickToggleState(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val enabled: Boolean? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val requiresRoot: Boolean = false,
)

data class ShellResult(
    val output: String = "",
    val error: String = "",
    val exitCode: Int = 0,
) {
    val success: Boolean get() = exitCode == 0
    val allOutput: String get() = if (error.isBlank()) output else if (output.isBlank()) error else "$output\n$error"
}

enum class TerminalKind { INPUT, STDOUT, STDERR, SYSTEM, EXIT }

data class TerminalLine(
    val text: String,
    val kind: TerminalKind = TerminalKind.STDOUT,
    val timestampMs: Long = 0,
)

enum class DiscoveryType { CONNECT, PAIR }

data class DiscoveredEndpoint(
    val name: String,
    val host: String,
    val port: Int,
    val type: DiscoveryType,
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val label: String) : ConnectionState
    data class Connected(val device: DeviceInfo) : ConnectionState
    data class Error(val message: String, val lastLabel: String = "") : ConnectionState
}
