package com.raulshma.jellyplay.core.data.cast.dlna

import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

class SsdpDiscovery(
    private val wifiManagerProvider: () -> WifiManager?,
) {

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_TIMEOUT_MS = 5000
        private const val DISCOVERY_INTERVAL_MS = 30_000L
        private const val MAX_AGE_SECONDS = 180
        private const val MULTICAST_LOCK_TAG = "JellyPlayDlnaDiscovery"

        private val SEARCH_TARGET_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private val SEARCH_TARGET_ALL = "ssdp:all"

        private val MSEARCH_MEDIA_RENDERER = buildString {
            appendLine("M-SEARCH * HTTP/1.1")
            appendLine("HOST: $SSDP_ADDRESS:$SSDP_PORT")
            appendLine("MAN: \"ssdp:discover\"")
            appendLine("MX: 3")
            appendLine("ST: $SEARCH_TARGET_MEDIA_RENDERER")
            appendLine()
        }

        private val MSEARCH_ALL = buildString {
            appendLine("M-SEARCH * HTTP/1.1")
            appendLine("HOST: $SSDP_ADDRESS:$SSDP_PORT")
            appendLine("MAN: \"ssdp:discover\"")
            appendLine("MX: 3")
            appendLine("ST: $SEARCH_TARGET_ALL")
            appendLine()
        }
    }

    private val _discoveredLocations = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val discoveredLocations: StateFlow<Map<String, DiscoveredDevice>> = _discoveredLocations.asStateFlow()

    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    data class DiscoveredDevice(
        val locationUrl: String,
        val usn: String,
        val searchTarget: String,
        val discoveredAtMs: Long = System.currentTimeMillis(),
        val maxAgeSeconds: Int = MAX_AGE_SECONDS,
    ) {
        fun isExpired(): Boolean {
            val elapsed = (System.currentTimeMillis() - discoveredAtMs) / 1000
            return elapsed > maxAgeSeconds
        }
    }

    fun startPeriodicDiscovery(scope: CoroutineScope) {
        stopDiscovery()
        discoveryJob = launchDiscoveryLoop(scope)
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        releaseMulticastLock()
    }

    private fun launchDiscoveryLoop(scope: CoroutineScope): Job {
        return scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    try {
                        // MulticastLock scoped to the search cycle only — the
                        // 30 s idle gap between bursts previously held the lock
                        // (and the wifi radio out of power save) for the whole
                        // discovery session.
                        acquireMulticastLock()
                        try {
                            performSearch()
                        } finally {
                            releaseMulticastLock()
                        }
                        purgeExpiredDevices()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "SSDP search cycle failed", e)
                    }
                    delay(DISCOVERY_INTERVAL_MS)
                }
            } finally {
                releaseMulticastLock()
            }
        }
    }

    private suspend fun performSearch() {
        val responses = mutableMapOf<String, DiscoveredDevice>()

        sendAndCollect(MSEARCH_MEDIA_RENDERER) { location, usn, st ->
            responses[location] = DiscoveredDevice(location, usn, st)
        }

        sendAndCollect(MSEARCH_ALL) { location, usn, st ->
            if (st.contains("MediaRenderer", ignoreCase = true)) {
                responses.getOrPut(location) {
                    DiscoveredDevice(location, usn, st)
                }
            }
        }

        val current = _discoveredLocations.value.toMutableMap()
        for ((location, device) in responses) {
            val existing = current[location]
            if (existing == null || existing.usn != device.usn) {
                current[location] = device
            } else {
                current[location] = existing.copy(discoveredAtMs = System.currentTimeMillis())
            }
        }
        _discoveredLocations.value = current
    }

    private fun sendAndCollect(
        message: String,
        onDeviceFound: (location: String, usn: String, st: String) -> Unit,
    ) {
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.soTimeout = SSDP_TIMEOUT_MS
            socket.bind(InetSocketAddress(0))

            val data = message.toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(SSDP_ADDRESS)
            val packet = DatagramPacket(data, data.size, address, SSDP_PORT)
            socket.send(packet)

            val buffer = ByteArray(4096)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            val deadline = System.currentTimeMillis() + SSDP_TIMEOUT_MS

            while (System.currentTimeMillis() < deadline) {
                try {
                    socket.receive(receivePacket)
                    val response = String(buffer, 0, receivePacket.length, Charsets.UTF_8)
                    parseSsdpResponse(response)?.let { (location, usn, st) ->
                        if (location.isNotBlank()) {
                            onDeviceFound(location, usn, st)
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SSDP socket error", e)
        } finally {
            socket.close()
        }
    }

    private fun parseSsdpResponse(response: String): Triple<String, String, String>? {
        var location = ""
        var usn = ""
        var st = ""

        for (line in response.lines()) {
            val colonIndex = line.indexOf(':')
            if (colonIndex < 0) continue
            val key = line.substring(0, colonIndex).trim().lowercase()
            val value = line.substring(colonIndex + 1).trim()
            when (key) {
                "location" -> location = value
                "usn" -> usn = value
                "st" -> st = value
            }
        }
        if (location.isBlank()) return null
        return Triple(location, usn, st)
    }

    fun removeDevice(locationUrl: String) {
        val current = _discoveredLocations.value.toMutableMap()
        current.remove(locationUrl)
        _discoveredLocations.value = current
    }

    private fun purgeExpiredDevices() {
        val current = _discoveredLocations.value
        val purged = current.filter { (_, device) -> !device.isExpired() }
        if (purged.size != current.size) {
            _discoveredLocations.value = purged
        }
    }

    private fun acquireMulticastLock() {
        releaseMulticastLock()
        try {
            val wm = wifiManagerProvider() ?: return
            val lock = wm.createMulticastLock(MULTICAST_LOCK_TAG)
            lock.setReferenceCounted(true)
            lock.acquire()
            multicastLock = lock
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
        }
        multicastLock = null
    }
}
