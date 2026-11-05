package dev.ezadb.core

/** POSIX shell quoting compatible with AOSP adb_utils.cpp::escape_arg(). */
object ShellQuote {
    fun quote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    fun command(vararg args: String): String = command(args.toList())

    fun command(args: List<String>): String = args.joinToString(" ") { quote(it) }

    /** Builds `settings <op> <namespace> <key> [value]` without injection. */
    fun settings(op: String, namespace: SettingNamespace, key: String, value: String? = null): String {
        val args = mutableListOf("settings", op, namespace.adbName, key)
        if (value != null) args += value
        return command(args)
    }

    fun pm(vararg args: String): String = command(listOf("pm") + args)
    fun am(vararg args: String): String = command(listOf("am") + args)
    fun svc(vararg args: String): String = command(listOf("svc") + args)
}
