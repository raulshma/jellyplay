package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.navigation.playbackhost.ExternalPlayerLaunch
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The slice of the activity-scoped MainViewModel the main shell renders
 * through — [MainContent]'s ONLY view of it. The seam exists so the
 * navigation package never names the concrete ViewModel type: the shell
 * depends on just the flows it collects and the commands it invokes, and the
 * ViewModel (which also serves the intent/deep-link/shortcut surfaces
 * MainActivity owns) implements it. Everything else the composition needs
 * from the shell layer arrives through the [ShellInfra] coordinator bundle
 * and [com.raulshma.jellyplay.feature.shell.navigation.ShellHostHooks].
 */
internal interface MainShellModel {

    /** App-wide offline mode (OfflineModeManager owns the flag). */
    val offlineMode: StateFlow<OfflineMode>

    /** Count of downloads actively in flight, for the nav overflow's badge. */
    val activeDownloadCount: StateFlow<Int>

    /** True while a user-initiated offline→online transition is in flight. */
    val isGoingOnline: StateFlow<Boolean>

    /** One-shot deep link / shortcut / shared-text route request. */
    val pendingRoute: StateFlow<Route?>

    /** One-shot search prefill (shared-text targets). */
    val pendingSearchQuery: StateFlow<String?>

    /** One-shot "Surprise Me" signal for the Home hero controller. */
    val surpriseRequests: SharedFlow<Unit>

    /** Whether the current user is an admin (gates admin destinations). */
    val isAdmin: StateFlow<Boolean>

    /** True while an admin-status refresh is in flight. */
    val isRefreshingAdmin: StateFlow<Boolean>

    /** Consumes the state-loss-restore marker exactly once per fresh ViewModel. */
    fun consumeStateLossRestore(): Boolean

    /** Toggles manual offline mode. */
    fun toggleOfflineMode()

    /** Persists the Home mode (Video / Music) switch. */
    fun setHomeMode(mode: HomeMode)

    /** Fires the Surprise-Me signal (see [surpriseRequests]). */
    fun requestSurprise()

    /** Re-validates the current user's admin status (deduped by the shared AdminRefreshGate). */
    fun refreshAdminStatus()

    /** Acks the pending route after dispatch. */
    fun consumePendingRoute()

    /** Acks the pending search query after the Search entry consumed it. */
    fun consumePendingSearchQuery()

    /** Resolves the external-player launch for an item (completed download or server stream URL). */
    suspend fun buildExternalPlayerLaunch(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): ExternalPlayerLaunch?

    /** Reports an external-player playback start to the server. */
    fun reportExternalPlaybackStart(playerLaunch: ExternalPlayerLaunch)

    /** Reports an external-player playback stop, crediting watched progress. */
    fun reportExternalPlaybackStopped(playerLaunch: ExternalPlayerLaunch, finalPositionTicks: Long)
}
