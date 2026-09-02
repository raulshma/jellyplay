package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.flow.StateFlow

/**
 * The single owner of the app's offline-mode state: combines the manual
 * offline preference and the auto-offline preference with the observed
 * network status into one [OfflineMode] flow.
 */
interface OfflineModeManager {
    val offlineMode: StateFlow<OfflineMode>

    val isOffline: Boolean

    val networkStatus: StateFlow<NetworkStatus>

    fun toggleManualOffline()

    fun checkNetworkAndAutoDetect()
}
