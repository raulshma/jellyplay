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
import kotlinx.coroutines.flow.Flow
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
 *
 * The two callbackFlow registrations are one parameterized builder
 * ([capabilityFlow]) over per-stream derive/current readers, and the
 * capability→value mappings are the internal pure functions
 * [networkStatusFromCapabilityFacts] / [isMeteredFromCapabilityFacts]
 * (androidHostTest pins them — the Android-only "validated ⇒ Online /
 * has-internet-but-unvalidated ⇒ Local" ladder is exactly the behavior the
 * desktop monitor never produces).
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
    override val networkStatus: StateFlow<NetworkStatus> = capabilityFlow(
        derive = ::deriveStatusFromCapabilities,
        current = ::currentStatus,
    )
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
    override val isMetered: StateFlow<Boolean> = capabilityFlow(
        derive = ::deriveMeteredFromCapabilities,
        current = ::currentMetered,
    )
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

    /**
     * The one ConnectivityManager callback registration both monitored streams
     * ride: emit the current value, register a
     * [NetworkCapabilities.NET_CAPABILITY_INTERNET] callback that re-emits on
     * available/capabilities-changed and re-probes on loss, unregister on
     * close. Distinct-until-changed + conflated, matching the pre-extraction
     * chains byte-for-byte.
     */
    private fun <T> capabilityFlow(
        derive: (Network) -> T,
        current: () -> T,
    ): Flow<T> = callbackFlow {
        val sendCurrent: () -> Unit = { trySend(current()) }

        val callback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(derive(network))
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(derive(network))
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

    private fun currentStatus(): NetworkStatus = activeNetworkStatus(connectivityManager)

    private fun deriveStatusFromCapabilities(network: Network): NetworkStatus {
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkStatus.Offline
        return networkStatusFromCapabilityFacts(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    private fun currentMetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return true
        return deriveMeteredFromCapabilities(activeNetwork)
    }

    private fun deriveMeteredFromCapabilities(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return true
        return isMeteredFromCapabilityFacts(
            hasNotMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }
}

/**
 * Pure capability-facts → [NetworkStatus] derivation (the decision ladder
 * [AndroidNetworkMonitor.deriveStatusFromCapabilities] feeds): a VALIDATED
 * network is [NetworkStatus.Online]; internet-capable but unvalidated is
 * [NetworkStatus.Local] (captive portal / blocked uplink — LAN still works);
 * no internet capability is [NetworkStatus.Offline]. Android can report
 * [NetworkStatus.Local]; the desktop monitor never does — do not "fix" that
 * asymmetry here. Missing capabilities are handled by the caller (they map to
 * Offline before this runs).
 */
internal fun networkStatusFromCapabilityFacts(
    hasInternet: Boolean,
    validated: Boolean,
): NetworkStatus = when {
    validated -> NetworkStatus.Online
    hasInternet -> NetworkStatus.Local
    else -> NetworkStatus.Offline
}

/**
 * The synchronous active-network read, shared by [AndroidNetworkMonitor]'s
 * current-value seeding and the offline manager's foreground probe: the
 * active network's capability facts through the
 * [networkStatusFromCapabilityFacts] ladder — [NetworkStatus.Offline] when
 * there is no active network or no readable capabilities. One function, not
 * two hand-mirrored ladders: the probe's "reachable" verdict and the
 * published [NetworkStatus] cannot drift.
 */
internal fun activeNetworkStatus(connectivityManager: ConnectivityManager): NetworkStatus {
    val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus.Offline
    val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        ?: return NetworkStatus.Offline
    return networkStatusFromCapabilityFacts(
        hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )
}

/**
 * Pure capability-facts → metered derivation: a network is metered unless it
 * carries NET_CAPABILITY_NOT_METERED. Missing capabilities are handled by the
 * caller (they map to metered before this runs).
 */
internal fun isMeteredFromCapabilityFacts(hasNotMetered: Boolean): Boolean = !hasNotMetered
