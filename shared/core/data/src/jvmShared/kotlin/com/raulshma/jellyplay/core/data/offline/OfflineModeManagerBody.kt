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
 * The one JVM body of the [OfflineModeManager] seam (I2 fold) over
 * [GoingOnlineFlag]: the mode flow, the going-online choreography, the
 * store×network collector (folded into [OfflineModeDerivation.fromCollector]),
 * `toggleManualOffline` and `checkNetworkAndAutoDetect` exist exactly once
 * here. Before the fold, AndroidOfflineModeManager and
 * DesktopOfflineModeManager restated all of it line-for-line except the
 * auto-offline arms — which the desktop twins silently dropped (their
 * collector never read `autoOfflineEnabled`); that delta is now the explicit
 * [allowAuto] policy parameter ([OfflineModeDerivation] documents both
 * readings).
 *
 * Platform actuals stay thin constructors:
 *  - Android (`AndroidOfflineModeManager`) passes `allowAuto = true` and a
 *    ConnectivityManager reachability probe, and adds the
 *    ProcessLifecycleOwner foreground re-derivation;
 *  - desktop (`DesktopOfflineModeManager`) passes `allowAuto = false` (its
 *    probe lambda is then never consulted — no-auto mode ignores the
 *    network) and nothing else.
 *
 * Constructor names/packages/signatures of the actuals are unchanged — Koin
 * binds both (androidDataModule / desktopDataModule), so this fold changes
 * no public surface.
 */
open class OfflineModeManagerBody(
    private val networkMonitor: NetworkMonitor,
    protected val networkOfflineStore: NetworkOfflineStore,
    /**
     * The auto-offline policy: `true` lets the auto pref + a lost network
     * engage [OfflineMode.OFFLINE_AUTO] (Android); `false` (desktop) makes
     * the manual toggle the only offline path and renders the reachability
     * probe irrelevant.
     */
    private val allowAuto: Boolean,
    /**
     * The platform reachability probe behind `checkNetworkAndAutoDetect`.
     * Only consulted when [allowAuto] — desktop passes a never-called stub.
     */
    private val probeReachable: () -> Boolean,
) : OfflineModeManager {

    // The managers' own scope: the collector and the GoingOnlineFlag watchdog
    // die with it (neither manager was ever cancellable externally).
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _offlineMode = MutableStateFlow(OfflineMode.ONLINE)
    override val offlineMode: StateFlow<OfflineMode> = _offlineMode.asStateFlow()

    // The going-online busy flag, built over this manager's own mode flow so
    // its clears ride the same emissions this class derives.
    private val goingOnlineFlag = GoingOnlineFlag(scope, _offlineMode)
    override val goingOnline: StateFlow<Boolean> = goingOnlineFlag.goingOnline

    override val isOffline: Boolean get() = _offlineMode.value != OfflineMode.ONLINE

    override val networkStatus: StateFlow<NetworkStatus> = networkMonitor.networkStatus

    init {
        scope.launch {
            // Combine the manual/auto offline slice fields instead of the
            // full ~150-field `preferences` StateFlow. A settings-screen round
            // trip elsewhere in the app previously re-awakened this collector
            // and re-derived offline mode even though neither offline flag
            // changed.
            combine(
                networkOfflineStore.networkOffline.map { it.manualOfflineEnabled },
                networkOfflineStore.networkOffline.map { it.autoOfflineEnabled },
                networkMonitor.networkStatus,
            ) { manualOffline, autoOffline, status ->
                Triple(manualOffline, autoOffline, status)
            }.collect { (manualOffline, autoOffline, status) ->
                _offlineMode.value = OfflineModeDerivation.fromCollector(
                    manual = manualOffline,
                    auto = autoOffline,
                    offline = status == NetworkStatus.Offline,
                    current = _offlineMode.value,
                    allowAuto = allowAuto,
                )
            }
        }
    }

    override fun toggleManualOffline() {
        val currentManual = networkOfflineStore.networkOffline.value.manualOfflineEnabled
        // The snapshot (not a mode guess) decides the arm — see
        // GoingOnlineFlag.armIfGoingOnline.
        goingOnlineFlag.armIfGoingOnline(currentManual)
        scope.launch {
            networkOfflineStore.setManualOffline(!currentManual)
        }
    }

    override fun checkNetworkAndAutoDetect() {
        val prefs = networkOfflineStore.networkOffline.value
        // The probe runs only where auto mode is a policy: allowAuto=false
        // ignores the network entirely, so desktop never pays for it.
        val reachable = if (allowAuto) probeReachable() else false
        _offlineMode.value = OfflineModeDerivation.fromProbe(
            manual = prefs.manualOfflineEnabled,
            reachable = reachable,
            auto = prefs.autoOfflineEnabled,
            current = _offlineMode.value,
            allowAuto = allowAuto,
        )
    }
}
