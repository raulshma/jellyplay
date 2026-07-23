package com.raulshma.jellyplay.core.data.worker

import android.util.Log
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Shared edge-detection scaffolding for "fire on the `Offline`/`Local` →
 * `Online` transition" listeners. (`Local` denotes a captive-portal /
 * unvalidated LAN, which cannot reliably reach the server, so it is treated
 * the same as Offline here.)
 *
 * Watches both [NetworkMonitor.networkStatus] and
 * [OfflineModeManager.offlineMode] (not network status alone) so that toggling
 * app-level Offline Mode back online — which emits no network transition —
 * also fires [onReady]. `wasReady` is seeded from the current flow values:
 * [NetworkMonitor]'s initial value is optimistically Online, so without
 * seeding the first collect would fire a spurious action on a fresh process
 * even though no transition occurred.
 *
 * Extracted because [PlaybackSyncReconnectListener] and
 * [DownloadReconnectListener] shared the `job`/`start`/`stop`/`isReady` and
 * the `combine(...).collect { if (ready && !wasReady) ... ; wasReady = ready }`
 * shape verbatim; only the ready-edge action differs.
 *
 * Not a `@Singleton` itself — each listener owns its own instance and is
 * `@Singleton`-scoped by its own DI binding. [start] is idempotent across
 * callers within one instance.
 */
class ReconnectTrigger(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    private val scope: CoroutineScope,
    private val tag: String,
    private val onReady: suspend () -> Unit,
) {
    private var job: Job? = null

    /**
     * Starts watching the network/offline signals. Idempotent: returns false
     * (without re-launching) if a collector is already active, so callers that
     * also run a one-shot startup action on [start] can gate it on the result
     * and avoid repeating that action on a redundant [start].
     */
    fun start(): Boolean {
        if (job?.isActive == true) return false
        var wasReady = isReady(
            networkMonitor.networkStatus.value,
            offlineModeManager.offlineMode.value,
        )
        job = scope.launch {
            combine(
                networkMonitor.networkStatus,
                offlineModeManager.offlineMode,
            ) { networkStatus, offlineMode -> isReady(networkStatus, offlineMode) }
                .collect { ready ->
                    if (ready && !wasReady) {
                        try {
                            onReady()
                        } catch (e: Exception) {
                            Log.w(tag, "Reconnect onReady action failed", e)
                        }
                    }
                    wasReady = ready
                }
        }
        return true
    }

    /**
     * Cancels the network collector. The collection runs indefinitely on the
     * injected [scope], so tests that drive a
     * `kotlinx.coroutines.test.TestScope` must call this (or cancel the scope)
     * so `runTest` does not report uncompleted child jobs.
     */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun isReady(networkStatus: NetworkStatus, offlineMode: OfflineMode): Boolean =
        networkStatus == NetworkStatus.Online && offlineMode == OfflineMode.ONLINE
}
