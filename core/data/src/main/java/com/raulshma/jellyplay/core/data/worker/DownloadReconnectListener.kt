package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Watches the network status and resumes interrupted downloads when the device
 * gains validated internet — the `Offline`/`Local` → `Online` transition. The
 * shared edge-detection scaffolding lives in [ReconnectTrigger].
 *
 * Why this is needed: when a network drop interrupts an in-flight download, the
 * worker catches the `IOException` in its read loop and sets the row `PAUSED`
 * (reason `NETWORK`). `PAUSED` is a terminal state until something flips it back
 * to `PENDING` — [DownloadWorker.doWork] bails immediately on `PAUSED` — so
 * without this listener a download paused by connectivity loss stays paused
 * forever (or until the user manually resumes it), which manifests as
 * "downloads never finished after I lost signal."
 *
 * [DownloadRepository.resumeInterruptedDownloads] resumes only `PAUSED` rows
 * with reason `NETWORK` (not user-paused) plus `FAILED` rows, and skips rows
 * past the auto-retry budget so a persistently failing download dead-letters
 * instead of spinning on every reconnect.
 *
 * Unlike [PlaybackSyncReconnectListener] there is no startup flush: cold-start
 * recovery ([com.raulshma.jellyplay.startup.DownloadRecoveryInitializer]) only
 * re-enqueues `PENDING`/`DOWNLOADING`/`QUEUED` rows, so an interrupted row that
 * is already `PAUSED`/`FAILED` on a process that cold-starts already-online is
 * not recovered until the next genuine `Offline → Online` transition (or a
 * manual resume). That is by design — the listener reacts to connectivity, not
 * process starts.
 *
 * Constructed as a singleton so [start] is idempotent across callers.
 */
class DownloadReconnectListener(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    private val downloadRepository: DownloadRepository,
    private val scope: CoroutineScope,
) {
    private val trigger = ReconnectTrigger(
        networkMonitor = networkMonitor,
        offlineModeManager = offlineModeManager,
        scope = scope,
        tag = TAG,
        onReady = { downloadRepository.resumeInterruptedDownloads() },
    )

    fun start() {
        trigger.start()
    }

    /** Cancels the network collector. See [ReconnectTrigger.stop]. */
    fun stop() = trigger.stop()

    private companion object {
        private const val TAG = "DownloadReconnect"
    }
}
