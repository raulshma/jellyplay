package com.raulshma.jellyplay.core.data.testutil

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Controllable [NetworkMonitor] fake for the core:data jvmTest suites —
 * replaces the hand copies in the worker/playback tests. Tests flip state by
 * writing into the flows.
 */
class FakeNetworkMonitor(
    initial: NetworkStatus = NetworkStatus.Online,
    initialIsMetered: Boolean = false,
) : NetworkMonitor {
    override val networkStatus = MutableStateFlow(initial)
    override val isMetered = MutableStateFlow(initialIsMetered)
}
