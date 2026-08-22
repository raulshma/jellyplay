package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Desktop implementation of the [OfflineModeManager] seam: offline mode
 * never auto-engages (the desktop [NetworkMonitor] is always `Online`), so
 * the only path to [OfflineMode.OFFLINE_MANUAL] is the manual toggle. The
 * collector mirrors the Android derivation verbatim minus the
 * ProcessLifecycleOwner foreground check, which has no desktop equivalent —
 * `checkNetworkAndAutoDetect` is therefore a plain re-derivation over the
 * store snapshot.
 */
class DesktopOfflineModeManager(
    private val networkMonitor: NetworkMonitor,
    private val networkOfflineStore: NetworkOfflineStore,
) : OfflineModeManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _offlineMode = MutableStateFlow(OfflineMode.ONLINE)
    override val offlineMode: StateFlow<OfflineMode> = _offlineMode.asStateFlow()

    override val isOffline: Boolean get() = _offlineMode.value != OfflineMode.ONLINE

    override val networkStatus: StateFlow<NetworkStatus> = networkMonitor.networkStatus

    init {
        scope.launch {
            combine(
                networkOfflineStore.networkOffline.map { it.manualOfflineEnabled },
                networkOfflineStore.networkOffline.map { it.autoOfflineEnabled },
                networkMonitor.networkStatus,
            ) { manualOffline, _, _ -> manualOffline }
                .collect { manualOffline ->
                    if (manualOffline) {
                        _offlineMode.value = OfflineMode.OFFLINE_MANUAL
                    } else if (_offlineMode.value == OfflineMode.OFFLINE_MANUAL) {
                        _offlineMode.value = OfflineMode.ONLINE
                    }
                }
        }
    }

    override fun toggleManualOffline() {
        val currentManual = networkOfflineStore.networkOffline.value.manualOfflineEnabled
        scope.launch {
            networkOfflineStore.setManualOffline(!currentManual)
        }
    }

    override fun checkNetworkAndAutoDetect() {
        // Desktop never auto-engages offline mode: the connectivity seam
        // reports Online until Phase V1 refines desktop detection.
        if (networkOfflineStore.networkOffline.value.manualOfflineEnabled) {
            _offlineMode.value = OfflineMode.OFFLINE_MANUAL
        } else if (_offlineMode.value == OfflineMode.OFFLINE_MANUAL) {
            _offlineMode.value = OfflineMode.ONLINE
        }
    }
}
