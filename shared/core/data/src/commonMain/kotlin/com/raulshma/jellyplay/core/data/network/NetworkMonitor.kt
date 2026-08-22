package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

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
interface NetworkMonitor {
    /**
     * A [StateFlow] that always reflects the current [NetworkStatus].
     * Starts with [NetworkStatus.Online] as the optimistic default so the UI
     * doesn't flash "offline" on a cold start before the first callback fires.
     */
    val networkStatus: StateFlow<NetworkStatus>

    /**
     * Whether the active network is metered (e.g. cellular, metered Wi-Fi).
     * True when the network lacks `NET_CAPABILITY_NOT_METERED`.
     * Used by [com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard]
     * to gate proactive audio-cache prefetching.
     */
    val isMetered: StateFlow<Boolean>
}
