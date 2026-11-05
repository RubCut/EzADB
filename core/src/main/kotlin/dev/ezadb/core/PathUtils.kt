package dev.ezadb.core

object PathUtils {
    fun normalize(path: String): String {
        if (path.isBlank()) return "/"
        val absolute = if (path.startsWith("/")) path else "/$path"
        val parts = mutableListOf<String>()
        absolute.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun join(parent: String, child: String): String {
        if (child.startsWith("/")) return normalize(child)
        val base = normalize(parent).trimEnd('/')
        return normalize("$base/$child")
    }

    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == "/") return "/"
        val idx = normalized.lastIndexOf('/')
        return if (idx <= 0) "/" else normalized.substring(0, idx)
    }

    fun basename(path: String): String = normalize(path).substringAfterLast('/')

    fun extension(path: String): String = basename(path).substringAfterLast('.', "")

    fun isTextFile(path: String): Boolean {
        val ext = extension(path).lowercase()
        return ext in setOf(
            "txt", "log", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java",
            "py", "sh", "bash", "zsh", "conf", "cfg", "ini", "prop", "properties", "md",
            "csv", "tsv", "yaml", "yml", "toml", "gradle", "kts", "c", "cpp", "h", "hpp",
            "rs", "go", "rb", "php", "sql", "gitignore", "editorconfig", "rc", "list",
        ) || basename(path).startsWith(".")
    }

    fun sanitizeLocalName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").ifBlank { "file" }.take(180)

    fun validateRemote(path: String): Boolean =
        path.isNotBlank() && !path.contains('\u0000') && !path.contains('\n') && path.startsWith("/")
}
