package dev.ezadb.data

import android.content.Context
import dev.ezadb.core.AppInfo
import java.io.File

/**
 * Keeps application labels between sessions so the list shows real names instantly,
 * even before the on-device helper answered (or when it is unavailable).
 */
class AppMetaCache(context: Context) {
    private val file = File(context.filesDir, "app-labels.tsv")
    private val labels = LinkedHashMap<String, String>()

    @Volatile private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            file.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size >= 2 && parts[0].isNotBlank()) labels[parts[0]] = parts[1]
            }
        }
    }

    @Synchronized
    fun label(packageName: String): String? {
        ensureLoaded()
        return labels[packageName]?.takeIf { it.isNotBlank() && it != packageName }
    }

    @Synchronized
    fun apply(apps: List<AppInfo>): List<AppInfo> {
        ensureLoaded()
        return apps.map { app ->
            if (app.label != app.packageName) app
            else labels[app.packageName]?.let { app.copy(label = it) } ?: app
        }
    }

    @Synchronized
    fun remember(apps: List<AppInfo>) {
        ensureLoaded()
        var changed = false
        apps.forEach { app ->
            if (app.label.isNotBlank() && app.label != app.packageName && labels[app.packageName] != app.label) {
                labels[app.packageName] = app.label
                changed = true
            }
        }
        if (!changed) return
        runCatching {
            file.bufferedWriter().use { writer ->
                labels.forEach { (pkg, label) ->
                    writer.write(pkg)
                    writer.write("\t")
                    writer.write(label.replace('\t', ' ').replace('\n', ' '))
                    writer.write("\n")
                }
            }
        }
    }
}
