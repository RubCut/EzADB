package dev.ezadb.data

import android.content.Context
import android.hardware.usb.UsbDevice
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.KadbOptions
import com.flyfishxu.kadb.TcpKeepAlive
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.exception.AdbPairAuthException
import com.flyfishxu.kadb.usb.KadbUsb
import dev.ezadb.R
import dev.ezadb.core.ConnectionKind
import dev.ezadb.core.ConnectionState
import dev.ezadb.core.DeviceInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.channels.InterruptedByTimeoutException
import javax.net.ssl.SSLException

/**
 * Owns the single ADB transport.
 *
 * Reliability rules:
 *  - every operation is serialized, so a burst of UI refreshes cannot flood adbd with streams;
 *  - read operations transparently reconnect and retry when the TLS transport dies
 *    (Wi-Fi power save, screen off, adbd restart);
 *  - a heartbeat notices dead links early instead of failing the next user action;
 *  - raw SSL/socket errors are translated into actionable messages.
 */
class ConnectionManager(private val context: Context) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val connectMutex = Mutex()
    private val opMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeat: Job? = null

    @Volatile private var client: Kadb? = null
    private var usbDeviceName: String? = null
    private var currentKind: ConnectionKind = ConnectionKind.TCP
    private var currentLabel: String = ""

    val isConnected: Boolean get() = client != null

    suspend fun connectTcp(host: String, port: Int, kind: ConnectionKind = ConnectionKind.TCP) {
        connectMutex.withLock {
            disconnectLocked()
            val label = "$host:$port"
            _state.value = ConnectionState.Connecting(label)
            currentKind = kind
            currentLabel = label
            try {
                prepareIdentity()
                val kadb = Kadb.create(
                    host = host,
                    port = port,
                    connectTimeout = 12_000,
                    socketTimeout = 60_000,
                    options = KadbOptions(tcpKeepAlive = TcpKeepAlive()),
                )
                verify(kadb, 25_000)
                client = kadb
                _state.value = ConnectionState.Connected(DeviceInfo(connectionKind = kind, endpointLabel = label))
                startHeartbeat(kadb)
            } catch (error: Throwable) {
                runCatching { client?.close() }
                client = null
                _state.value = ConnectionState.Error(AdbErrors.message(context, error), label)
                throw error
            }
        }
    }

    suspend fun connectUsb(device: UsbDevice) {
        connectMutex.withLock {
            disconnectLocked()
            val label = device.productName ?: device.deviceName
            _state.value = ConnectionState.Connecting(label)
            currentKind = ConnectionKind.USB
            currentLabel = label
            usbDeviceName = device.deviceName
            try {
                prepareIdentity()
                val kadb = KadbUsb.create(context = context, device = device, options = KadbOptions())
                verify(kadb, 45_000)
                client = kadb
                _state.value = ConnectionState.Connected(
                    DeviceInfo(model = label, connectionKind = ConnectionKind.USB, endpointLabel = "USB")
                )
                startHeartbeat(kadb)
            } catch (error: Throwable) {
                runCatching { client?.close() }
                client = null
                usbDeviceName = null
                _state.value = ConnectionState.Error(AdbErrors.message(context, error), label)
                throw error
            }
        }
    }

    suspend fun pair(host: String, port: Int, code: String) {
        withContext(Dispatchers.IO) {
            prepareIdentity()
            try {
                Kadb.pair(host, port, code)
            } catch (error: Throwable) {
                throw IOException(AdbErrors.message(context, error), error)
            }
        }
    }

    suspend fun disconnect() {
        connectMutex.withLock { disconnectLocked() }
    }

    fun onUsbDetached(device: UsbDevice) {
        if (usbDeviceName == device.deviceName) {
            heartbeat?.cancel()
            client?.close()
            client = null
            usbDeviceName = null
            _state.value = ConnectionState.Disconnected
        }
    }

    /**
     * Runs [block] on the live transport.
     * [retryable] must only be true for operations without side effects: they are
     * replayed once or twice after a transparent reconnect.
     */
    suspend fun <T> use(retryable: Boolean = false, block: suspend (Kadb) -> T): T {
        val kadb = client ?: throw IOException(context.getString(R.string.no_connection))
        return withContext(Dispatchers.IO) {
            opMutex.withLock {
                var lastError: Throwable? = null
                for (attempt in 0..2) {
                    try {
                        return@withLock block(kadb)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        lastError = error
                        val canRetry = retryable && attempt < 2 && client === kadb && isTransportError(error)
                        if (!canRetry) throw error
                        runCatching { kadb.resetConnection() }
                        delay(250L * (attempt + 1))
                    }
                }
                throw lastError ?: IOException(context.getString(R.string.err_link_lost))
            }
        }
    }

    fun current(): Kadb? = client

    /** Drops the transport so the next command reconnects with a fresh TLS session. */
    fun invalidate() {
        runCatching { client?.resetConnection() }
    }

    private fun startHeartbeat(kadb: Kadb) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive && client === kadb) {
                delay(20_000)
                if (!isActive || client !== kadb) break
                if (opMutex.isLocked) continue
                val alive = runCatching {
                    opMutex.withLock { kadb.shell("echo ezadb").output.trim() == "ezadb" }
                }.getOrDefault(false)
                if (alive) continue
                val recovered = runCatching {
                    opMutex.withLock {
                        kadb.resetConnection()
                        kadb.shell("echo ezadb").output.trim() == "ezadb"
                    }
                }.getOrDefault(false)
                if (!recovered && client === kadb) {
                    _state.value = ConnectionState.Error(context.getString(R.string.err_link_lost), currentLabel)
                    break
                }
            }
        }
    }

    private suspend fun prepareIdentity() {
        withContext(Dispatchers.IO) {
            // Generating/parsing the RSA identity is slow; do it before the handshake
            // so a half-initialized key store can never produce certificate errors.
            KadbCert.ensureReady()
        }
    }

    private suspend fun disconnectLocked() {
        heartbeat?.cancelAndJoin()
        heartbeat = null
        runCatching { client?.close() }
        client = null
        usbDeviceName = null
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun verify(kadb: Kadb, timeoutMs: Long) {
        withContext(Dispatchers.IO) {
            coroutineScope {
                val watchdog = launch {
                    delay(timeoutMs)
                    runCatching { kadb.close() }
                }
                try {
                    val response = kadb.shell("echo ezadb")
                    val text = (response.output + response.errorOutput).trim()
                    if (text != "ezadb") throw IOException(context.getString(R.string.unexpected_response, text))
                } finally {
                    watchdog.cancelAndJoin()
                }
            }
        }
    }

    companion object {
        fun isTransportError(error: Throwable): Boolean {
            generateSequence(error) { it.cause }.forEach { cause ->
                when (cause) {
                    is SSLException, is SocketException, is SocketTimeoutException, is ConnectException,
                    is EOFException, is InterruptedByTimeoutException, is AdbPairAuthException -> return true
                }
                val message = cause.message?.lowercase() ?: return@forEach
                if (
                    message.contains("socket is closed") ||
                    message.contains("connection reset") ||
                    message.contains("broken pipe") ||
                    message.contains("stream is closed") ||
                    message.contains("stream closed") ||
                    message.contains("connection closed") ||
                    message.contains("kadb is closed") ||
                    message.contains("tls handshake") ||
                    message.contains("software caused connection abort")
                ) return true
            }
            return false
        }
    }
}
