package dev.ezadb.data

import android.content.Context
import android.hardware.usb.UsbDevice
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.KadbOptions
import com.flyfishxu.kadb.TcpKeepAlive
import com.flyfishxu.kadb.usb.KadbUsb
import dev.ezadb.core.ConnectionKind
import dev.ezadb.core.ConnectionState
import dev.ezadb.core.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class ConnectionManager(private val context: Context) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var client: Kadb? = null
    private var usbDeviceName: String? = null
    private var currentKind: ConnectionKind = ConnectionKind.TCP
    private var currentLabel: String = ""

    val isConnected: Boolean get() = client != null

    suspend fun connectTcp(host: String, port: Int, kind: ConnectionKind = ConnectionKind.TCP) {
        mutex.withLock {
            disconnectLocked()
            val label = "$host:$port"
            _state.value = ConnectionState.Connecting(label)
            currentKind = kind
            currentLabel = label
            try {
                val kadb = Kadb.create(
                    host = host,
                    port = port,
                    connectTimeout = 10_000,
                    socketTimeout = 30_000,
                    options = KadbOptions(tcpKeepAlive = TcpKeepAlive()),
                )
                verify(kadb, 20_000)
                client = kadb
                _state.value = ConnectionState.Connected(
                    DeviceInfo(connectionKind = kind, endpointLabel = label)
                )
            } catch (error: Throwable) {
                runCatching { client?.close() }
                client = null
                _state.value = ConnectionState.Error(error.message ?: "Не удалось подключиться", label)
                throw error
            }
        }
    }

    suspend fun connectUsb(device: UsbDevice) {
        mutex.withLock {
            disconnectLocked()
            val label = device.productName ?: device.deviceName
            _state.value = ConnectionState.Connecting(label)
            currentKind = ConnectionKind.USB
            currentLabel = label
            usbDeviceName = device.deviceName
            try {
                val kadb = KadbUsb.create(
                    context = context,
                    device = device,
                    options = KadbOptions(),
                )
                verify(kadb, 45_000)
                client = kadb
                _state.value = ConnectionState.Connected(
                    DeviceInfo(model = label, connectionKind = ConnectionKind.USB, endpointLabel = "USB")
                )
            } catch (error: Throwable) {
                runCatching { client?.close() }
                client = null
                usbDeviceName = null
                _state.value = ConnectionState.Error(error.message ?: "Не удалось подключиться по USB", label)
                throw error
            }
        }
    }

    suspend fun pair(host: String, port: Int, code: String) {
        withContext(Dispatchers.IO) {
            Kadb.pair(host, port, code)
        }
    }

    suspend fun disconnect() {
        mutex.withLock { disconnectLocked() }
    }

    fun onUsbDetached(device: UsbDevice) {
        if (usbDeviceName == device.deviceName) {
            client?.close()
            client = null
            usbDeviceName = null
            _state.value = ConnectionState.Disconnected
        }
    }

    suspend fun <T> use(block: suspend (Kadb) -> T): T {
        val kadb = client ?: throw IOException("Нет активного подключения")
        return withContext(Dispatchers.IO) { block(kadb) }
    }

    fun current(): Kadb? = client

    private fun disconnectLocked() {
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
                    if (text != "ezadb") throw IOException("Неожиданный ответ устройства: $text")
                } finally {
                    watchdog.cancelAndJoin()
                }
            }
        }
    }
}
