package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val scheduler: PlaybackSyncScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        // Capture the initial connected-state synchronously so the launched
        // collector cannot miss the seed value (the coroutine may not run until
        // the scope's dispatcher pumps, by which time the value could have
        // changed).
        //
        // Use `== Online` (not the NetworkStatus.hasNetwork enum helper, which
        // treats an unvalidated Local network as connected — playback progress
        // sync needs a validated internet path to reach the server).
        var wasConnected = networkMonitor.networkStatus.value == NetworkStatus.Online
        job = scope.launch {
            // StateFlow already deduplicates equal emissions (operator fusion),
            // so `collect` only fires on actual transitions.
            networkMonitor.networkStatus.collect { status ->
                val connected = status == NetworkStatus.Online
                if (connected && !wasConnected) {
                    scheduler.enqueueNow()
                }
                wasConnected = connected
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
}
