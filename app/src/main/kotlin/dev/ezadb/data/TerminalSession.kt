package dev.ezadb.data

import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbPtyShellSession
import com.flyfishxu.kadb.shell.AdbShellPacket
import com.flyfishxu.kadb.stream.AdbStream
import dev.ezadb.core.TerminalKind
import dev.ezadb.core.TerminalLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.Buffer

class TerminalSession(
    private val kadb: Kadb,
    private val scope: CoroutineScope,
    private val onLine: (TerminalLine) -> Unit,
    private val onClosed: (String?) -> Unit,
) {
    private var pty: AdbPtyShellSession? = null
    private var raw: AdbStream? = null
    private var job: Job? = null
    var interactive: Boolean = false
        private set

    fun start() {
        if (kadb.supportsFeature("shell_v2")) {
            pty = kadb.openPtyShellSession()
            interactive = true
            job = scope.launch(Dispatchers.IO) { readPty() }
        } else {
            raw = kadb.open("shell:")
            interactive = false
            job = scope.launch(Dispatchers.IO) { readRaw() }
        }
    }

    fun send(text: String) {
        pty?.write(text) ?: raw?.sink?.apply {
            writeUtf8(text)
            flush()
        }
    }

    fun sendLine(command: String) {
        send(command + "\n")
    }

    fun interrupt() = send("\u0003")
    fun eof() = send("\u0004")

    fun resize(rows: Int, cols: Int) {
        runCatching { pty?.resize(rows, cols) }
    }

    fun close() {
        job?.cancel()
        runCatching { pty?.close() }
        runCatching { raw?.close() }
        pty = null
        raw = null
    }

    private suspend fun readPty() {
        val session = pty ?: return
        try {
            while (scope.isActive) {
                when (val packet = session.read()) {
                    is AdbShellPacket.StdOut -> emit(String(packet.payload, Charsets.UTF_8), TerminalKind.STDOUT)
                    is AdbShellPacket.StdError -> emit(String(packet.payload, Charsets.UTF_8), TerminalKind.STDERR)
                    is AdbShellPacket.Exit -> {
                        val code = packet.payload.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                        onLine(TerminalLine("[процесс завершился с кодом $code]", TerminalKind.EXIT))
                        break
                    }
                }
            }
            onClosed(null)
        } catch (error: Throwable) {
            onClosed(error.message ?: "Сессия терминала закрыта")
        } finally {
            runCatching { session.close() }
        }
    }

    private suspend fun readRaw() {
        val stream = raw ?: return
        val buffer = Buffer()
        try {
            while (scope.isActive) {
                val read = stream.source.read(buffer, 8192L)
                if (read == -1L) break
                emit(buffer.readString(), TerminalKind.STDOUT)
            }
            onClosed(null)
        } catch (error: Throwable) {
            onClosed(error.message ?: "Сессия терминала закрыта")
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun emit(text: String, kind: TerminalKind) {
        if (text.isEmpty()) return
        onLine(TerminalLine(text, kind))
    }
}
