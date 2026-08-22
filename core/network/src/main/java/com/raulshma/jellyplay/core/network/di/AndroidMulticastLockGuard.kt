package com.raulshma.jellyplay.core.network.di

import android.content.Context
import android.net.wifi.WifiManager
import com.raulshma.jellyplay.core.network.DiscoveryMulticastGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [DiscoveryMulticastGuard]: holds a `WifiManager.MulticastLock` for
 * the duration of an SSDP discovery scan. Without it the Wi-Fi stack filters
 * the UDP multicast responses out and `discoverLocalServers` sees nothing on
 * most devices.
 *
 * Lock handling is verbatim what ServerDiscoveryService did inline before the
 * C3 split: a fresh reference-counted lock per acquire, released (guarded
 * against double-release) on the matching release. Acquire/release pairs come
 * from the discovery flow's try/finally, so they are balanced; the guard is
 * synchronized anyway because it is a singleton shared across flows.
 */
@Singleton
class AndroidMulticastLockGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) : DiscoveryMulticastGuard {

    private val lock = Any()
    private val held = ArrayDeque<WifiManager.MulticastLock>()

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun acquire() {
        synchronized(lock) {
            val newLock = wifiManager.createMulticastLock("JellyPlayDiscovery")
            newLock.setReferenceCounted(true)
            newLock.acquire()
            held.addLast(newLock)
        }
    }

    override fun release() {
        synchronized(lock) {
            val current = held.removeLastOrNull() ?: return
            try {
                if (current.isHeld) {
                    current.release()
                }
            } catch (_: Exception) {
                // Lock may already be released
            }
        }
    }
}
