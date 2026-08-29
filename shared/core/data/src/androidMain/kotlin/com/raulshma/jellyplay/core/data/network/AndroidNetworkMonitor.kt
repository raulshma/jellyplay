package com.raulshma.jellyplay.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.raulshma.jellyplay.core.model.NetworkStatus
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

/**
 * Android implementation of the [NetworkMonitor] seam (C4 part 2 connectivity
 * split): body moved verbatim from the legacy `:core:data` `NetworkMonitor` —
 * the class was renamed and the Hilt annotations stripped (Koin's
 * [com.raulshma.jellyplay.core.data.di.androidDataModule] constructs it;
 * consumers resolve the same single straight from Koin).
 */
class AndroidNetworkMonitor(
    private val context: Context,
) : NetworkMonitor {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A [StateFlow] that always reflects the current [NetworkStatus].
     * Starts with [NetworkStatus.Online] as the optimistic default so the UI
     * doesn't flash "offline" on a cold start before the first callback fires.
     */
    override val networkStatus: StateFlow<NetworkStatus> = callbackFlow {
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

    /**
     * Whether the active network is metered (e.g. cellular, metered Wi-Fi).
     * True when the network lacks [NetworkCapabilities.NET_CAPABILITY_NOT_METERED].
     * Used by [com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard]
     * to gate proactive audio-cache prefetching, and by
     * [com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager]
     * (the former synchronous ConnectivityManager read — hence the seeded
     * initialValue below, so a cold `.value` read before the first
     * subscription reports real state, matching the legacy per-call probe).
     */
    override val isMetered: StateFlow<Boolean> = callbackFlow {
        val sendCurrent: () -> Unit = { trySend(currentMetered()) }

        val callback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(deriveMeteredFromCapabilities(network))
            }

            override fun onLost(network: Network) {
                trySend(currentMetered())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(deriveMeteredFromCapabilities(network))
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
            // Seeded with a one-shot synchronous probe (not a hardcoded
            // false): WhileSubscribed means the upstream callback flow only
            // runs while a collector is active, so an un-subscribed `.value`
            // read would otherwise always see the initial constant.
            initialValue = currentMetered(),
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

    private fun currentMetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return true
        return deriveMeteredFromCapabilities(activeNetwork)
    }

    private fun deriveMeteredFromCapabilities(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
