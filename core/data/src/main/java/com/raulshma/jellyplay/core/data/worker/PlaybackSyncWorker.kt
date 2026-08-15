package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Drains the [PlaybackOutboxRepository] — replaying START/PROGRESS/STOP events
 * captured while the device was offline — and reconciles the local offline
 * store against the server so resume positions stay consistent.
 *
 * Triggered:
 *   - On the Offline→Online network transition (immediate, via
 *     [PlaybackSyncReconnectListener]).
 *   - Periodically (backstop) via [PlaybackSyncScheduler].
 *
 * The drain replays outbox entries through [PlaybackRepository.replayOutboxEntry],
 * a pure dispatch (no enqueue) so a retry does not recurse back into the outbox;
 * the worker owns the drain loop (delete on success, retry/dead-letter on
 * failure, reconcile), the repository owns the entry-type → API-call mapping.
 *
 * Latest-wins reconciliation: for each item that has a downloaded offline row,
 * the server's `MediaItem` is fetched and compared. If the server's
 * `lastPlayedDate` is newer than the local `recordedAt`, or the server reports
 * `isPlayed`, the offline row is overwritten from the server — fixing the
 * "watched half offline → finished online → back offline shows stale 50%"
 * case.
 */
@HiltWorker
class PlaybackSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val outbox: PlaybackOutboxRepository,
    private val playbackRepository: PlaybackRepository,
    private val offlineModeManager: OfflineModeManager,
    private val playedStateSync: PlayedStateSync,
    private val offlineRepository: OfflineRepository,
    private val userDataSyncScheduler: UserDataSyncScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (offlineModeManager.isOffline) {
            // A manual offline setting is not a transient delivery failure.
            // Finish this run so it cannot occupy the unique one-shot slot with
            // WorkManager backoff; PlaybackSyncReconnectListener will enqueue a
            // fresh, immediate drain when Offline Mode is disabled again.
            return Result.success()
        }
        val pending = outbox.drain()

        // Downloaded items whose played/resume state may have drifted
        // server-side (watched or resumed on the web / another device). These
        // are reconciled even when the outbox is empty so a server-side change
        // propagates to the offline store without this device recording any
        // playback of its own — closes the "fixed online, not reflected
        // offline" gap. Best-effort lookup: a DB read failure degrades to an
        // outbox-only run.
        val downloadedIds = runCatching { offlineRepository.getDownloadedItemIds() }
            .getOrElse { emptyList() }

        if (pending.isEmpty() && downloadedIds.isEmpty()) return Result.success()

        // Promote to a foreground service while the drain runs so the user sees
        // a "Syncing watch progress" notification and the OS does not throttle
        // a burst of reconnect-driven drains. Only posted when there is outbox
        // work to push (the notification reports the outbox count); a
        // reconcile-only run with an empty outbox stays a silent background
        // freshness check. Best-effort: some device OEMs restrict foreground
        // promotion; fall back to plain background if it throws.
        if (pending.isNotEmpty()) {
            runCatching {
                setForeground(PlaybackSyncNotificationHelper.createForegroundInfo(applicationContext, pending.size))
            }
        }

        // On the final attempt the drain must converge: a persistently
        // undeliverable entry is dead-lettered (flagged, not deleted) so it is
        // skipped by future drains and the sync indicator's countFlow() reaches
        // 0, but the row is retained for audit and a future manual "retry sync"
        // affordance. Hard-deleting was unsafe: the failure could have been a
        // network blip after a 200, so the server may already have the event —
        // discarding the row lost both the audit trail and any chance of repair.
        //
        // Note on atomicity: each entry's "replay + delete" cannot be wrapped
        // in a single Room transaction because replayOutboxEntry() is a network call and
        // holding the SQLite lock across network I/O is an anti-pattern (and
        // blocks every other DB client). The residual risk is re-delivery if
        // the process is killed between a successful replayOutboxEntry() and the delete():
        // the Jellyfin playback-report endpoints are keyed by sessionId/itemId
        // and treat a later report as latest-wins, so a duplicate is idempotent
        // in effect. The dead-letter flag closes the data-loss half of the bug.
        val exhausted = runAttemptCount >= MAX_RETRIES
        val reconciledItems = mutableSetOf<String>()
        var anyFailure = false
        if (pending.isNotEmpty()) {
            var remaining = pending.size
            for (entry in pending) {
                val ok = runCatching { playbackRepository.replayOutboxEntry(entry) }.getOrElse { false }
                if (ok) {
                    outbox.delete(entry.id)
                    reconciledItems.add(entry.itemId)
                } else if (exhausted) {
                    Log.w(
                        TAG,
                        "Dead-lettering outbox entry ${entry.id} " +
                            "(item=${entry.itemId}, type=${entry.eventType}) " +
                            "after $MAX_RETRIES attempts",
                    )
                    outbox.markDeadLetter(entry.id)
                } else {
                    anyFailure = true
                }
                remaining--
                // Update the notification mid-drain so the count ticks down. Only
                // worth a notify() call on meaningful batches to avoid spam.
                if (pending.size > 1 && remaining > 0) {
                    runCatching {
                        PlaybackSyncNotificationHelper.updateNotification(applicationContext, remaining)
                    }
                }
            }
        }
        // Only reconcile after the push so the server's view reflects the
        // locally-recorded progress for these items. The reconciliation does a
        // network fetch per item (a forced getMediaDetail read), so a
        // large outbox (e.g. a long offline music session with many distinct
        // item ids) would otherwise fire N serial detail fetches in this
        // foreground worker. Bound the batch size and run the fetches with
        // bounded concurrency: anything beyond the cap is deferred to the
        // periodic backstop (the next drain reconciles them once they surface
        // again). Each reconciliation is independent and wrapped in runCatching
        // so a single failure cannot abort the batch.
        //
        // The batch is the union of just-pushed outbox items and a bounded set
        // of other downloaded items (server-side drift). Deduped so an item
        // that was both pushed and downloaded is fetched once.
        val itemsToReconcile = (reconciledItems + downloadedIds)
            .distinct()
            .take(MAX_RECONCILE_BATCH)
        val reconcileChanged = if (itemsToReconcile.isNotEmpty()) {
            val results = coroutineScope {
                val gate = Semaphore(MAX_CONCURRENT_RECONCILES)
                itemsToReconcile.map { itemId ->
                    async {
                        gate.withPermit {
                            runCatching { playedStateSync.reconcileOfflineRow(itemId) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            results.any { it != null && it != PlayedStateSync.ComputeResult.NOOP }
        } else {
            false
        }

        // Drain done — dismiss the progress notification regardless of outcome.
        // On retry/failure WorkManager re-runs the worker, which will repost.
        if (pending.isNotEmpty()) {
            runCatching { PlaybackSyncNotificationHelper.dismissNotification(applicationContext) }
        }

        // If anything was pushed up OR a downloaded row actually changed during
        // reconcile, the online UI caches (Continue Watching, Next Up, detail)
        // are now stale. Trigger an immediate user-data refresh so the user
        // sees fresh played/progress state on the online home + detail screens
        // instead of waiting for the 60s/2min cache TTLs or the
        // UserDataSyncScheduler periodic tick (12h). KEEP policy collapses rapid
        // reconnects. (This worker's own outbox-drain backstop in
        // PlaybackSyncScheduler is 4h with a 30m flex — distinct from the
        // user-data cadence referenced here.)
        if (reconciledItems.isNotEmpty() || reconcileChanged) {
            runCatching { userDataSyncScheduler.enqueueNow() }
        }

        // On a non-exhausted attempt a failure → retry (it may succeed next
        // time). On the exhausted attempt failures are already dead-lettered
        // above, so anyFailure stays false and the drain returns success —
        // guaranteeing the outbox count reaches 0.
        return if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.playback_sync_periodic"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.playback_sync_now"
        const val WORK_TAG = "playback_sync"

        private const val TAG = "PlaybackSyncWorker"
        private const val MAX_RETRIES = 3
        /**
         * Bounds the number of distinct items reconciled per drain so a very
         * large outbox cannot monopolise the foreground worker with a burst of
         * network fetches. Anything beyond the cap is deferred to the periodic
         * backstop (4h), which reconciles surviving offline rows on the next
         * run.
         */
        private const val MAX_RECONCILE_BATCH = 50
        /**
         * Bounds concurrency of the per-item detail fetches inside a batch so
         * the server is not hit with N simultaneous requests. Mirrors the
         * MAX_CONCURRENT_FOLDER_FETCHES gate in NewMediaCheckWorker.
         */
        private const val MAX_CONCURRENT_RECONCILES = 4
    }
}
