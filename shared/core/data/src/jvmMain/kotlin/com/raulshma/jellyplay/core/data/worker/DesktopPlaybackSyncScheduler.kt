package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Desktop playback-outbox drain trigger (the desktop actual of the
 * [PlaybackSyncScheduler] seam) — the in-process replacement for Android's
 * WorkManager [PlaybackSyncWorker] pair, running the SAME shared
 * [PlaybackOutboxDrainer] through fire-and-forget one-shot drains.
 *
 * Declared desktop behaviour delta (previously the seam was an honest no-op
 * and staged outbox rows sat forever): the drainer now runs
 *  - once at [start] (the app-start drain Android's `enqueueNow` covers),
 *  - on every Offline→Online network transition (the
 *    [PlaybackSyncReconnectListener] twin — the desktop probe is
 *    [NetworkMonitor.networkStatus], which never reports Local, so any
 *    transition out of Offline is the reconnect edge),
 *  - on [enqueueNow] (SyncStatusStateHolder's manual "sync now").
 *
 * [enqueuePeriodic] stays a deliberate no-op: there is no WorkManager-style
 * periodic backstop in process. A row staged by a transient HTTP failure
 * while the app stays continuously online waits for the next network
 * transition, manual sync, or app restart — the Android 4h backstop's
 * residual role, accepted on desktop because the reconnect edge (the trigger
 * the backstop exists to back up) is handled in-process here.
 *
 * Drains are serialized behind a mutex — the WorkManager
 * `ExistingWorkPolicy.KEEP` equivalent, so a manual sync racing a reconnect
 * transition collapses into one pass instead of double-replaying a snapshot.
 */
class DesktopPlaybackSyncScheduler(
    private val drainer: PlaybackOutboxDrainer,
    private val networkMonitor: NetworkMonitor,
    /** The process-wide application scope (DatastoreQualifiers.applicationScope in Koin). */
    private val scope: CoroutineScope,
) : PlaybackSyncScheduler {

    private var startJob: Job? = null
    private val drainMutex = Mutex()

    /**
     * Launches the startup drain and the network-transition observer.
     * Idempotent, mirroring [DesktopAutoDownloadScheduler.start]; the
     * composition root resolves + start()s it after startKoin (construction
     * is side-effect free).
     */
    fun start() {
        if (startJob?.isActive == true) return
        startJob = scope.launch {
            drain("startup")
            var previous: NetworkStatus? = null
            networkMonitor.networkStatus.collect { status ->
                val reconnected = previous == NetworkStatus.Offline && status.hasNetwork
                previous = status
                if (reconnected) drain("network-online")
            }
        }
    }

    override fun enqueueNow() {
        scope.launch { drain("manual") }
    }

    override fun enqueuePeriodic() {
        // See the class KDoc: no in-process periodic backstop on desktop.
    }

    /**
     * One fire-and-forget drain pass. attempt = 0: each pass gets a fresh
     * budget, the Android runAttemptCount ladder replaced by "the next
     * trigger re-runs the drain" (a not-yet-dead-lettered failure stays
     * pending in the outbox until the next transition / manual sync).
     */
    private suspend fun drain(trigger: String) {
        val result = drainMutex.withLock {
            runCatchingRethrowingCancellation { drainer.drainOnce(attempt = 0) }
        }
        result.fold(
            onSuccess = { drain ->
                Log.i(
                    TAG,
                    "Playback outbox drain ($trigger): pending=${drain.pendingCount} " +
                        "deadLettered=${drain.deadLetteredCount} " +
                        "reconciled=${drain.reconciledItemIds.size} " +
                        "retriesPending=${drain.retriesPending}",
                )
            },
            onFailure = { failure -> Log.w(TAG, "Playback outbox drain ($trigger) failed", failure) },
        )
    }

    private companion object {
        const val TAG = "DesktopPlaybackSync"
    }
}
