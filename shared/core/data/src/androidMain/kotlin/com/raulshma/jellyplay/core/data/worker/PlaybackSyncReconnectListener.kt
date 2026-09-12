package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Watches the network status and enqueues an immediate
 * [PlaybackSyncWorker] drain when the device gains validated internet — the
 * `Offline`/`Local` → `Online` transition. This is the primary, low-latency
 * trigger for the offline playback outbox; the periodic schedule is only a
 * backstop. The shared edge-detection scaffolding lives in [ReconnectTrigger].
 *
 * Also triggers once on [start] so progress captured while the app process
 * was killed still flushes shortly after launch — but only when the outbox
 * has pending entries, so a warm start with nothing to drain does not
 * schedule a no-op worker run.
 *
 * Constructed as a singleton so [start] is idempotent across callers.
 */
class PlaybackSyncReconnectListener(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    private val scheduler: PlaybackSyncScheduler,
    private val outbox: PlaybackOutboxRepository,
    private val scope: CoroutineScope,
) {
    private val trigger = ReconnectTrigger(
        networkMonitor = networkMonitor,
        offlineModeManager = offlineModeManager,
        scope = scope,
        tag = TAG,
        onReady = { scheduler.enqueueNow() },
    )

    fun start() {
        val started = trigger.start()
        // Flush anything captured while the process was dead — but only on the
        // first start, and only if the outbox actually has pending entries.
        // enqueueNow uses KEEP, so without this gate every warm start
        // (including ones where the outbox is known empty) schedules a worker
        // run that just wakes the DB and returns success on an empty drain. A
        // cheap single-row count avoids that no-op WorkManager run.
        if (started) {
            scope.launch {
                if (outbox.count() > 0) {
                    scheduler.enqueueNow()
                }
            }
        }
    }

    fun stop() = trigger.stop()

    private companion object {
        private const val TAG = "PlaybackSyncReconnect"
    }
}
