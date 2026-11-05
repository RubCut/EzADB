package dev.ezadb.core

/**
 * Builds a single shell invocation out of many commands.
 *
 * Every ADB stream costs a full round trip (OPEN/OKAY/WRTE/CLSE) which is very
 * expensive over TLS on Wi-Fi. Batching keeps the dashboard responsive.
 */
object BatchShell {
    private const val PREFIX = "__EZADB_SECTION_"

    fun marker(index: Int): String = "$PREFIX${index}__"

    /** Wraps every command with an echo marker so the output can be split again. */
    fun build(commands: List<String>): String =
        commands.mapIndexed { index, command ->
            "echo ${marker(index)}; ( $command ) 2>&1"
        }.joinToString("; ")

    /** Returns command output per index; missing sections become empty strings. */
    fun split(output: String, count: Int): List<String> {
        val buckets = Array(count) { StringBuilder() }
        var current = -1
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith(PREFIX) && trimmed.endsWith("__")) {
                val index = trimmed.removePrefix(PREFIX).removeSuffix("__").toIntOrNull()
                if (index != null && index in 0 until count) {
                    current = index
                    return@forEach
                }
            }
            if (current >= 0) buckets[current].append(line).append('\n')
        }
        return buckets.map { it.toString().trimEnd('\n') }
    }
}
