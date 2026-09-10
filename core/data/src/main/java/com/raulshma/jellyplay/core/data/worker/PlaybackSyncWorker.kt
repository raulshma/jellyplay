package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation

/**
 * Thin Android adapter between WorkManager and the shared
 * [PlaybackOutboxDrainer] — the drain choreography itself (retry budgets,
 * dead-letter policy, superseded-telemetry skip, derived watched flips,
 * bounded reconcile batch, post-drain cache coherence) moved verbatim to
 * :shared:core:data so Android and desktop drain through one code path
 * (desktop previously staged outbox rows with no drain machinery at all).
 *
 * This class owns only what is WorkManager-shaped:
 *  - the attempt counter ([runAttemptCount], the drainer's retry-budget
 *    input) and the retry()/success() mapping of the drain's
 *    [PlaybackOutboxDrainer.DrainResult] — on a non-exhausted attempt a
 *    failure maps to retry (it may succeed next time); on the exhausted
 *    attempt failures are already dead-lettered, so retriesPending stays
 *    false and the drain returns success, guaranteeing the outbox count
 *    reaches 0;
 *  - the foreground promotion + sync-progress notification, served through
 *    this worker's [PlaybackOutboxDrainer.Notifier] implementation: the
 *    drainer invokes [onDrainStarted]/[onDrainProgress]/[onDrainFinished] at
 *    the points the old drain loop called
 *    [PlaybackSyncNotificationHelper]. Each is best-effort — some device OEMs
 *    restrict foreground promotion / the notification service can be dead —
 *    and must never fail the drain itself.
 *
 * Triggered:
 *   - On the Offline→Online network transition (immediate, via
 *     [PlaybackSyncReconnectListener]).
 *   - Periodically (backstop) via [PlaybackSyncScheduler].
 *   (Desktop drains through [com.raulshma.jellyplay.core.data.worker.DesktopPlaybackSyncScheduler]
 *   instead — same drainer, no WorkManager.)
 */
class PlaybackSyncWorker(
    context: Context,
    params: WorkerParameters,
    /**
     * Factory rather than a graph-resolvable drainer: the notifier is
     * worker-instance-scoped (`setForeground` exists only on the running
     * CoroutineWorker), so [CoreDataWorkerFactory] binds each worker to its
     * own drainer over the singleton repositories.
     */
    createDrainer: (PlaybackOutboxDrainer.Notifier) -> PlaybackOutboxDrainer,
) : CoroutineWorker(context, params), PlaybackOutboxDrainer.Notifier {

    private val drainer = createDrainer(this)

    override suspend fun doWork(): Result {
        val result = drainer.drainOnce(attempt = runAttemptCount)
        return if (result.retriesPending) Result.retry() else Result.success()
    }

    override suspend fun onDrainStarted(pendingCount: Int) {
        runCatchingRethrowingCancellation {
            setForeground(PlaybackSyncNotificationHelper.createForegroundInfo(applicationContext, pendingCount))
        }
    }

    override suspend fun onDrainProgress(remaining: Int) {
        runCatchingRethrowingCancellation {
            PlaybackSyncNotificationHelper.updateNotification(applicationContext, remaining)
        }
    }

    override suspend fun onDrainFinished() {
        runCatchingRethrowingCancellation {
            PlaybackSyncNotificationHelper.dismissNotification(applicationContext)
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.playback_sync_periodic"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.playback_sync_now"
        const val WORK_TAG = "playback_sync"
    }
}
