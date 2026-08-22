package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop implementation of the [NetworkMonitor] seam: an always-connected
 * equivalent (`Online`, unmetered). Desktop connectivity detection (captive
 * portals, NIC-level reachability) is deliberately deferred to Phase V1 —
 * until then the desktop app simply assumes the network is up, which matches
 * the optimistic `initialValue = NetworkStatus.Online` cold-start behaviour
 * of the Android implementation.
 */
class DesktopNetworkMonitor : NetworkMonitor {
    override val networkStatus: StateFlow<NetworkStatus> =
        MutableStateFlow(NetworkStatus.Online).asStateFlow()

    override val isMetered: StateFlow<Boolean> =
        MutableStateFlow(false).asStateFlow()
}
