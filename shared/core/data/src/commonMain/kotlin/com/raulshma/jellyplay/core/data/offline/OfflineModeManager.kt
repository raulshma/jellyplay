package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.flow.StateFlow

/**
 * The single owner of the app's offline-mode state: combines the manual
 * offline preference and the auto-offline preference with the observed
 * network status into one [OfflineMode] flow — and of the going-online busy
 * flag that decorates the user-initiated offline→online transition.
 */
interface OfflineModeManager {
    val offlineMode: StateFlow<OfflineMode>

    /**
     * True while a user-initiated offline→online transition is in flight —
     * the spinner state behind every "Go Online" affordance (Home's Go
     * Online button, the nav-overflow toggles on both shells).
     *
     * [toggleManualOffline] arms it when the toggle's direction is going
     * online, BEFORE the async preference write so feedback precedes the
     * mode flip; external/auto flips never raise it. It clears when
     * [offlineMode] emits ONLINE — whichever way the transition resolves
     * (the toggle's write landing, or an external/auto reconnect overtaking
     * it) — with a watchdog fallback that force-clears the flag once its
     * 30 s window expires without an ONLINE emission (the lost-write case;
     * the deadline is unconditional, so an arm can never strand the flag).
     * A hung post-toggle fetch can NOT
     * park this flag: the ONLINE emission clears it before any fetch
     * starts. Set/clear choreography lives in [GoingOnlineFlag].
     */
    val goingOnline: StateFlow<Boolean>

    val isOffline: Boolean

    val networkStatus: StateFlow<NetworkStatus>

    fun toggleManualOffline()

    fun checkNetworkAndAutoDetect()
}
