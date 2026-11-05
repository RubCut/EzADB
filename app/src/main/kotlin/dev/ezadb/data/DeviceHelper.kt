package dev.ezadb.data

import android.content.Context
import android.util.Base64
import android.util.LruCache
import dev.ezadb.core.AppInfo
import dev.ezadb.core.Parsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.File

/**
 * Runs the bundled app_process agent on the device.
 *
 * The agent answers in one round trip what would otherwise need hundreds of
 * `dumpsys package` calls: real application labels, versions, sizes and icons.
 * When a device blocks app_process the caller falls back to plain `pm` output.
 */
class DeviceHelper(
    private val context: Context,
    private val connection: ConnectionManager,
) {
    private val mutex = Mutex()

    @Volatile private var installed = false

    @Volatile private var broken = false

    private val iconMemory = LruCache<String, ByteArray>(64)
    private val iconDir: File get() = File(context.cacheDir, "app-icons").apply { mkdirs() }

    fun reset() {
        installed = false
        broken = false
        iconMemory.evictAll()
    }

    suspend fun apps(): List<AppInfo>? = mutex.withLock {
        if (broken || !ensureInstalled()) return@withLock null
        val output = run("apps") ?: return@withLock null
        if (!output.contains(HELPER_BANNER)) {
            broken = true
            return@withLock null
        }
        Parsers.helperApps(output).takeIf { it.isNotEmpty() }
    }

    suspend fun icon(packageName: String, size: Int = 96): ByteArray? {
        iconMemory.get(packageName)?.let { return it }
        val cached = File(iconDir, "$packageName.png")
        if (cached.exists() && cached.length() > 0) {
            val bytes = runCatching { cached.readBytes() }.getOrNull()
            if (bytes != null) {
                iconMemory.put(packageName, bytes)
                return bytes
            }
        }
        if (broken || !ensureInstalled()) return null
        val output = run("icon", packageName, size.toString()) ?: return null
        val line = output.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return null
        val bytes = runCatching { Base64.decode(line.trim(), Base64.DEFAULT) }.getOrNull() ?: return null
        if (bytes.size < 32) return null
        iconMemory.put(packageName, bytes)
        runCatching { cached.writeBytes(bytes) }
        return bytes
    }

    /** Fetches a screenful of icons in one round trip. */
    suspend fun icons(packages: List<String>): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        val missing = mutableListOf<String>()
        packages.distinct().forEach { pkg ->
            val memory = iconMemory.get(pkg)
            if (memory != null) {
                result[pkg] = memory
                return@forEach
            }
            val disk = File(iconDir, "$pkg.png")
            if (disk.exists() && disk.length() > 0) {
                runCatching { disk.readBytes() }.getOrNull()?.let {
                    iconMemory.put(pkg, it)
                    result[pkg] = it
                    return@forEach
                }
            }
            missing += pkg
        }
        if (missing.isEmpty() || broken || !ensureInstalled()) return result
        val output = run("icons", *missing.toTypedArray()) ?: return result
        output.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val parts = line.split('\t')
            if (parts.size < 2 || parts[1].isBlank()) return@forEach
            val bytes = runCatching { Base64.decode(parts[1].trim(), Base64.DEFAULT) }.getOrNull() ?: return@forEach
            if (bytes.size < 32) return@forEach
            val pkg = parts[0].trim()
            iconMemory.put(pkg, bytes)
            runCatching { File(iconDir, "$pkg.png").writeBytes(bytes) }
            result[pkg] = bytes
        }
        return result
    }

    private suspend fun ensureInstalled(): Boolean {
        if (installed) return true
        if (broken) return false
        return withContext(Dispatchers.IO) {
            val bytes = runCatching { context.assets.open(ASSET).use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                broken = true
                return@withContext false
            }
            val pushed = runCatching {
                connection.use(retryable = true) { kadb ->
                    kadb.push(Buffer().write(bytes), REMOTE, 0b110100100, System.currentTimeMillis())
                }
            }.isSuccess
            if (!pushed) {
                broken = true
                return@withContext false
            }
            val pong = run("ping")
            installed = pong?.trim()?.contains("pong") == true
            if (!installed) broken = true
            installed
        }
    }

    private suspend fun run(vararg args: String): String? = runCatching {
        connection.use(retryable = true) { kadb ->
            val command = "CLASSPATH=$REMOTE app_process /system/bin $CLASS ${args.joinToString(" ")}"
            kadb.shell(command).output
        }
    }.getOrNull()

    companion object {
        private const val ASSET = "ezadb-helper.jar"
        private const val REMOTE = "/data/local/tmp/ezadb-helper.jar"
        private const val CLASS = "dev.ezadb.helper.Helper"
        private const val HELPER_BANNER = "#ezadb-helper-v1"
    }
}
