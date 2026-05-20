package com.raulshma.jellyplay.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import com.raulshma.jellyplay.core.model.NetworkStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the device's network connectivity and emits a [NetworkStatus] for each change.
 *
 * The three states are:
 * - **Online** — the active network has `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED`.
 * - **Local** — the device is connected to WiFi/Ethernet (has a network) but the network is not
 *   validated (e.g. no internet gateway, captive portal). The Jellyfin LAN server may still be
 *   reachable.
 * - **Offline** — no active network at all.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A [StateFlow] that always reflects the current [NetworkStatus].
     * Starts with [NetworkStatus.Online] as the optimistic default so the UI
     * doesn't flash "offline" on a cold start before the first callback fires.
     */
    val networkStatus: StateFlow<NetworkStatus> = callbackFlow {
        val sendCurrent: () -> Unit = { trySend(currentStatus()) }

        val callback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(deriveStatusFromCapabilities(network))
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(deriveStatusFromCapabilities(network))
            }
        }

        sendCurrent()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
        .distinctUntilChanged()
        .conflate()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkStatus.Online,
        )

    // ── helpers ──────────────────────────────────────────────

    private fun currentStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkStatus.Offline
        return deriveStatusFromCapabilities(activeNetwork)
    }

    private fun deriveStatusFromCapabilities(network: Network): NetworkStatus {
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkStatus.Offline

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return when {
            validated -> NetworkStatus.Online
            hasInternet -> NetworkStatus.Local
            else -> NetworkStatus.Offline
        }
    }
}
