package dev.ezadb.core

class TerminalHistory(private val maxSize: Int = 200) {
    private val items = ArrayDeque<String>()
    private var cursor: Int = -1
    private var draft: String = ""

    val entries: List<String> get() = items.toList()

    fun add(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        if (items.lastOrNull() == trimmed) {
            cursor = -1
            return
        }
        items.remove(trimmed)
        items.addLast(trimmed)
        while (items.size > maxSize) items.removeFirst()
        cursor = -1
    }

    fun begin(input: String) {
        draft = input
        cursor = items.size
    }

    fun previous(): String? {
        if (items.isEmpty()) return null
        cursor = (cursor - 1).coerceAtLeast(0)
        return items.elementAtOrNull(cursor)
    }

    fun next(): String? {
        if (items.isEmpty()) return draft
        cursor = (cursor + 1).coerceAtMost(items.size)
        return if (cursor >= items.size) draft else items.elementAtOrNull(cursor)
    }

    fun clear() {
        items.clear()
        cursor = -1
        draft = ""
    }
}
