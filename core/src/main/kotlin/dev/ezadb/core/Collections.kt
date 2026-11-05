package dev.ezadb.core

object Collections {
    fun filterApps(apps: List<AppInfo>, query: String, filter: AppFilter, sort: AppSort): List<AppInfo> {
        val q = query.trim().lowercase()
        val filtered = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.system
                AppFilter.SYSTEM -> app.system
                AppFilter.DISABLED -> !app.enabled
            }
            val matchesQuery = q.isBlank() ||
                app.packageName.lowercase().contains(q) ||
                app.label.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
        return when (sort) {
            AppSort.NAME -> filtered.sortedBy { it.label.lowercase() }
            AppSort.PACKAGE -> filtered.sortedBy { it.packageName.lowercase() }
            AppSort.SIZE -> filtered.sortedByDescending { it.sizeBytes ?: 0 }
            AppSort.UID -> filtered.sortedBy { it.uid ?: Int.MAX_VALUE }
        }
    }

    fun filterFiles(files: List<RemoteFile>, query: String, sort: FileSort): List<RemoteFile> {
        val q = query.trim().lowercase()
        val filtered = files.filter { q.isBlank() || it.name.lowercase().contains(q) }
        val comparator: Comparator<RemoteFile> = when (sort) {
            FileSort.NAME -> compareBy { it.name.lowercase() }
            FileSort.SIZE -> compareByDescending { it.sizeBytes }
            FileSort.MTIME -> compareByDescending { it.mtimeSec }
            FileSort.TYPE -> compareBy({ it.extension().lowercase() }, { it.name.lowercase() })
        }
        return filtered.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.then(comparator))
    }

    fun filterSettings(entries: List<SettingEntry>, query: String): List<SettingEntry> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return entries
        return entries.filter {
            it.key.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                (it.value?.lowercase()?.contains(q) == true)
        }
    }

    private fun RemoteFile.extension(): String = PathUtils.extension(path)
}
