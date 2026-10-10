package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The slice of the activity-scoped MainViewModel the main shell renders
 * through — [MainContent]'s ONLY view of it. The navigation package names
 * only slice interfaces, never the concrete ViewModel type: this interface
 * is the layout subtree's view (the flows it collects and the commands it
 * invokes), [ShellGateModel] is the root session gate's view
 * ([com.raulshma.jellyplay.navigation.JellyPlayApp] — preferences,
 * onboarding, logout, shell-overlay navigation), and both are implemented by
 * the one activity-scoped ViewModel. Everything else the composition needs
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
}
