package com.raulshma.jellyplay.core.data.worker

import android.util.Log
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
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
 * Watches the network status and resumes interrupted (`PAUSED`/`FAILED`)
 * downloads when the device gains validated internet — the `Offline`/`Local` →
 * `Online` transition. (`Local` denotes a captive-portal / unvalidated LAN,
 * which cannot reliably reach the server, so it is treated the same as Offline
 * here.)
 *
 * Why this is needed: when a network drop interrupts an in-flight download,
 * [DownloadWorker] catches the `IOException` and sets the row `PAUSED`.
 * `PAUSED` is a terminal state until something flips it back to `PENDING` —
 * [DownloadWorker.doWork] bails immediately on `PAUSED` — so without this
 * listener a download paused by connectivity loss stays paused forever (or
 * until the user manually resumes it), which manifests as "downloads never
 * finished after I lost signal."
 *
 * Watches both [NetworkMonitor.networkStatus] and
 * [OfflineModeManager.offlineMode] (not network status alone) so that toggling
 * app-level Offline Mode back online — which emits no network transition — also
 * triggers the resume. This mirrors [PlaybackSyncReconnectListener], which has
 * the same requirement for the playback outbox drain.
 *
 * Constructed as a singleton so [start] is idempotent across callers.
 */
@Singleton
class DownloadReconnectListener @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    private val downloadRepository: DownloadRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        // Seed wasReady from the current flow values: NetworkMonitor's initial
        // value is optimistically Online, so without seeding the first collect
        // would fire a spurious resume on a fresh process even though no
        // Offline → Online transition occurred.
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
                            downloadRepository.resumeInterruptedDownloads()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to resume interrupted downloads on reconnect", e)
                        }
                    }
                    wasReady = ready
                }
        }
    }

    /**
     * Cancels the network collector. The collection runs indefinitely on the
     * injected [scope], so tests that drive a [kotlinx.coroutines.test.TestScope]
     * must call this (or cancel the scope) so `runTest` does not report
     * uncompleted child jobs.
     */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun isReady(networkStatus: NetworkStatus, offlineMode: OfflineMode): Boolean =
        networkStatus == NetworkStatus.Online && offlineMode == OfflineMode.ONLINE

    private companion object {
        private const val TAG = "DownloadReconnect"
    }
}
