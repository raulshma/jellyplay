package com.raulshma.jellyplay.core.data.testutil

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Controllable [OfflineModeManager] fake for the core:data jvmTest suites —
 * replaces the hand copies in the worker tests. Tests flip state by writing
 * into [offlineMode] / [goingOnline].
 */
class FakeOfflineModeManager(
    initial: OfflineMode = OfflineMode.ONLINE,
) : OfflineModeManager {
    override val offlineMode = MutableStateFlow(initial)
    // Consumers key off [offlineMode]; [goingOnline] is the manager impls'
    // transition choreography and stays parked here for interface parity.
    override val goingOnline = MutableStateFlow(false)
    override val isOffline: Boolean get() = offlineMode.value != OfflineMode.ONLINE
    override val networkStatus = MutableStateFlow(NetworkStatus.Online)
    override fun toggleManualOffline() {
        offlineMode.value = if (offlineMode.value == OfflineMode.ONLINE) {
            OfflineMode.OFFLINE_MANUAL
        } else {
            OfflineMode.ONLINE
        }
    }
    override fun checkNetworkAndAutoDetect() = Unit
}
