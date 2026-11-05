package dev.ezadb.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.ezadb.core.DiscoveredEndpoint
import dev.ezadb.core.DiscoveryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class WirelessDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val _endpoints = MutableStateFlow<List<DiscoveredEndpoint>>(emptyList())
    val endpoints: StateFlow<List<DiscoveredEndpoint>> = _endpoints.asStateFlow()

    private val resolved = ConcurrentHashMap<String, DiscoveredEndpoint>()
    private var connectListener: NsdManager.DiscoveryListener? = null
    private var pairListener: NsdManager.DiscoveryListener? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        resolved.clear()
        _endpoints.value = emptyList()
        connectListener = listener(DiscoveryType.CONNECT).also {
            runCatching { nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, it) }
        }
        pairListener = listener(DiscoveryType.PAIR).also {
            runCatching { nsdManager.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, it) }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        connectListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        pairListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        connectListener = null
        pairListener = null
        resolved.clear()
        _endpoints.value = emptyList()
    }

    private fun listener(type: DiscoveryType) = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            resolved.remove(serviceInfo.serviceName)
            publish()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            runCatching {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val port = info.port
                        if (port <= 0) return
                        resolved[info.serviceName] = DiscoveredEndpoint(info.serviceName, host, port, type)
                        publish()
                    }
                })
            }
        }
    }

    private fun publish() {
        _endpoints.value = resolved.values.sortedWith(compareBy({ it.type.name }, { it.name }))
    }
}
