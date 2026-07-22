package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the network status and enqueues an immediate
 * [PlaybackSyncWorker] drain when the device gains validated internet — the
 * `Offline`/`Local` → `Online` transition. (`Local` denotes a captive-portal /
 * unvalidated LAN, which cannot reliably reach the server, so it is treated
 * the same as Offline here.) This is the primary, low-latency trigger for the
 * offline playback outbox; the periodic schedule is only a backstop.
 *
 * Also triggers once on [start] so progress captured while the app process
 * was killed still flushes shortly after launch.
 *
 * Constructed as a singleton so [start] is idempotent across callers.
 */
@Singleton
class PlaybackSyncReconnectListener @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    private val scheduler: PlaybackSyncScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        // A drain is viable only when the network is validated *and* app-level
        // Offline Mode is disabled. Watching both conditions matters for a
        // manual-offline user: flipping that setting back online does not emit a
        // network transition, so a network-only listener would leave progress
        // queued until the periodic backstop.
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
                    scheduler.enqueueNow()
                }
                wasReady = ready
            }
        }
        // Flush anything captured while the process was dead. Even if we are
        // currently online, the worker constraints gate the actual run.
        scheduler.enqueueNow()
    }

    /**
     * Cancels the network collector. The collection runs indefinitely on the
     * injected [scope], so tests that drive a [TestScope] must call this (or
     * cancel the scope) so `runTest` does not report uncompleted child jobs.
     */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun isReady(networkStatus: NetworkStatus, offlineMode: OfflineMode): Boolean =
        networkStatus == NetworkStatus.Online && offlineMode == OfflineMode.ONLINE
}
