package com.raulshma.jellyplay.core.notification.scheduler

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.worker.ReconnectTrigger
import kotlinx.coroutines.CoroutineScope

/**
 * Watches the network status and enqueues an immediate
 * [NewMediaCheckWorker] run when the device gains validated internet — the
 * `Offline`/`Local` → `Online` transition. The periodic schedule is only a
 * backstop; this is the low-latency catch-up trigger. The shared
 * edge-detection scaffolding lives in [ReconnectTrigger].
 *
 * Constructed as a singleton so [start] is idempotent across callers.
 */
class NotificationReconnectListener(
    private val networkMonitor: NetworkMonitor,
    private val offlineModeManager: OfflineModeManager,
    private val notificationScheduler: NotificationScheduler,
    private val scope: CoroutineScope,
) {
    private val trigger = ReconnectTrigger(
        networkMonitor = networkMonitor,
        offlineModeManager = offlineModeManager,
        scope = scope,
        tag = TAG,
        onReady = { notificationScheduler.enqueueNow() },
    )

    fun start() {
        trigger.start()
    }

    /** Cancels the network collector. See [ReconnectTrigger.stop]. */
    fun stop() = trigger.stop()

    private companion object {
        private const val TAG = "NotificationReconnect"
    }
}
