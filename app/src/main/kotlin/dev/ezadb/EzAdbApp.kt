package dev.ezadb

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import dev.ezadb.data.ConnectionManager
import dev.ezadb.data.DemoBackend
import dev.ezadb.data.DeviceRepository
import dev.ezadb.data.PreferencesRepository
import dev.ezadb.data.QuickToggleRepository
import dev.ezadb.data.WirelessDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import java.io.File

class EzAdbApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var preferences: PreferencesRepository
    lateinit var connection: ConnectionManager
    lateinit var deviceRepository: DeviceRepository
    lateinit var quickToggles: QuickToggleRepository
    lateinit var discovery: WirelessDiscovery
    val demo = DemoBackend()

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
        connection = ConnectionManager(this)
        deviceRepository = DeviceRepository(this, connection)
        quickToggles = QuickToggleRepository(deviceRepository)
        discovery = WirelessDiscovery(this)

        val keyFile = File(filesDir, "adbkey.pem")
        KadbCert.configure(store = OkioFilePrivateKeyStore(keyFile.toOkioPath()))
        appScope.launch(Dispatchers.IO) {
            runCatching {
                KadbCert.ensureReady()
                keyFile.setReadable(true, true)
                keyFile.setWritable(true, true)
                keyFile.setExecutable(false, false)
            }
        }
    }
}
