package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.isFinishedOffline
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
class PlaybackSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val outbox: PlaybackOutboxRepository,
    private val playbackRepository: PlaybackRepository,
    private val offlineModeManager: OfflineModeManager,
    private val playedStateSync: PlayedStateSync,
    private val offlineRepository: OfflineRepository,
    private val userDataSyncScheduler: UserDataSyncScheduler,
    /**
     * The derived watched flips route through [MediaRepository.markPlayed]
     * (not raw PlayedStateSync.flip) so each flip also drops the detail /
     * home-sections / catalogue caches the repository owns — a drain that
     * changes server state must not leave in-memory caches serving the
     * pre-drain view to an open detail screen (#153 home/detail coherence).
     */
    // Concrete type (not the interface): the module-internal wholesale
    // invalidateCaches is deliberately off the interface (plan 08).
    private val mediaRepository: MediaRepositoryImpl,
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

        val playedIntentItemIds = stagedPlayedIntentItemIds(pending)
        val derivedWatchedItemIds = deriveWatchedItemIds(pending, playedIntentItemIds)

        val reconciledItems = mutableSetOf<String>()
        var anyFailure = false
        if (pending.isNotEmpty()) {
            anyFailure = drainPendingEntries(pending, playedIntentItemIds, reconciledItems)
            anyFailure = pushDerivedWatchedFlips(derivedWatchedItemIds, reconciledItems) || anyFailure
        }

        val itemsToReconcile = (reconciledItems + downloadedIds)
            .distinct()
            .take(MAX_RECONCILE_BATCH)
        var reconcileChanged = false
        if (itemsToReconcile.isNotEmpty()) {
            val (undeliveredIntent, changed) = reconcileBatch(itemsToReconcile)
            anyFailure = undeliveredIntent || anyFailure
            reconcileChanged = changed
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
            // Synchronous wholesale cache drop — the drain changed server
            // state, and waiting on the async UserDataSyncWorker lets the
            // 2-minute detail cache / 60-second home cache serve the
            // pre-drain view in the meantime (the "home shows it, detail
            // doesn't" report). The worker below is still enqueued for its
            // own warm-refetch behavior.
            runCatching { mediaRepository.invalidateCaches() }
            // Synthetic user-data push: open detail sessions and the home
            // refresher listen on the same flow as WS pushes and refresh —
            // the drain's markPlayedItem calls may never arrive as a
            // UserDataChanged echo on this socket. Only delivered flips are
            // named: an undelivered derived flip changed nothing server-side.
            mediaRepository.notifyUserDataChanged(reconciledItems.toList())
            runCatching { userDataSyncScheduler.enqueueNow() }
        }

        // On a non-exhausted attempt a failure → retry (it may succeed next
        // time). On the exhausted attempt failures are already dead-lettered
        // above, so anyFailure stays false and the drain returns success —
        // guaranteeing the outbox count reaches 0.
        return if (anyFailure) Result.retry() else Result.success()
    }

    /**
     * Items in this drain's snapshot with an undelivered PLAYED intent.
     *
     * Latest-intent-wins ordering: once a PLAYED flip is staged for an item,
     * its START/PROGRESS/STOP telemetry is redundant and harmful — a trailing
     * STOP replayed after markPlayedItem can leave the server with a near-end
     * position and Played=false (the #153 "watched offline, online home shows
     * mostly completed" bug). markPlayedItem records a full-runtime position,
     * so the telemetry carries nothing the flip needs.
     *
     * The check reads the outbox, not just this drain's snapshot: a
     * dead-lettered PLAYED row is as authoritative as a pending one.
     */
    private suspend fun stagedPlayedIntentItemIds(pending: List<PlaybackOutboxEntry>): Set<String> =
        pending
            .map { it.itemId }
            .distinct()
            .filter { itemId ->
                runCatching { outbox.hasUnsyncedPlayedIntent(itemId) }.getOrDefault(false)
            }
            .toSet()

    /**
     * Second net for #153: a watched offline session whose PLAYED outbox row
     * never landed (process death at the threshold) or whose row was lost in
     * an older build. If the local mirror row already reads as watched
     * (isPlayed, or ≥ the watched threshold), a markPlayed is derived at
     * drain time for any item a surviving telemetry row still surfaces.
     * Items with an explicit undelivered played-state intent (pending or
     * dead-lettered) are excluded: the intent row is the authority for those,
     * and a derived flip must not race it.
     */
    private suspend fun deriveWatchedItemIds(
        pending: List<PlaybackOutboxEntry>,
        playedIntentItemIds: Set<String>,
    ): Set<String> =
        pending
            .filter { it.eventType in TELEMETRY_EVENT_TYPES }
            .map { it.itemId }
            .distinct()
            .filter { it !in playedIntentItemIds }
            .filter { itemId ->
                runCatching { !outbox.hasUnsyncedUnplayedIntent(itemId) }.getOrDefault(false)
            }
            .filter { itemId ->
                runCatching { offlineRepository.getOfflineItem(itemId) }.getOrNull()
                    ?.let { row -> row.isPlayed || row.isFinishedOffline }
                    ?: false
            }
            .toSet()

    /**
     * Replays the pending entries oldest-first, returning whether any entry
     * under its retry budget failed (→ the drain must retry).
     *
     * On the final attempt the drain must converge: a persistently
     * undeliverable entry is dead-lettered (flagged, not deleted) so it is
     * skipped by future drains and the sync indicator's countFlow() reaches
     * 0, but the row is retained for audit and a future manual "retry sync"
     * affordance. Hard-deleting was unsafe: the failure could have been a
     * network blip after a 200, so the server may already have the event —
     * discarding the row lost both the audit trail and any chance of repair.
     *
     * USER_INTENT_EVENT_TYPES get a much larger retry budget and are
     * dead-lettered only past it (#153): a dead-lettered watched flip is
     * silently lost forever, which is precisely the reported bug. Telemetry
     * keeps the tight budget — a stale position is harmless.
     *
     * Note on atomicity: each entry's "replay + delete" cannot be wrapped
     * in a single Room transaction because replayOutboxEntry() is a network call and
     * holding the SQLite lock across network I/O is an anti-pattern (and
     * blocks every other DB client). The residual risk is re-delivery if
     * the process is killed between a successful replayOutboxEntry() and the delete():
     * the Jellyfin playback-report endpoints are keyed by sessionId/itemId
     * and treat a later report as latest-wins, so a duplicate is idempotent
     * in effect. The dead-letter flag closes the data-loss half of the bug.
     */
    private suspend fun drainPendingEntries(
        pending: List<PlaybackOutboxEntry>,
        playedIntentItemIds: Set<String>,
        reconciledItems: MutableSet<String>,
    ): Boolean {
        var anyFailure = false
        var remaining = pending.size
        for (entry in pending) {
            // Superseded telemetry: skip replay, drop the row (see
            // [stagedPlayedIntentItemIds]).
            if (entry.itemId in playedIntentItemIds &&
                entry.eventType in TELEMETRY_EVENT_TYPES
            ) {
                outbox.delete(entry.id)
                reconciledItems.add(entry.itemId)
                remaining--
                continue
            }
            val ok = runCatching { playbackRepository.replayOutboxEntry(entry) }.getOrElse { false }
            if (ok) {
                outbox.delete(entry.id)
                reconciledItems.add(entry.itemId)
            } else {
                val budget = if (entry.eventType in USER_INTENT_EVENT_TYPES) MAX_INTENT_RETRIES else MAX_RETRIES
                if (runAttemptCount >= budget) {
                    Log.w(
                        TAG,
                        "Dead-lettering outbox entry ${entry.id} " +
                            "(item=${entry.itemId}, type=${entry.eventType}) " +
                            "after $budget attempts",
                    )
                    outbox.markDeadLetter(entry.id)
                } else {
                    anyFailure = true
                }
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
        return anyFailure
    }

    /**
     * Pushes the derived watched flips last, after telemetry has settled, so
     * the server's final state for these items is played (#153). markPlayed
     * wraps PlayedStateSync.flip with the repository's cache invalidation; on
     * failure the flip stages a PLAYED outbox row itself, so the intent
     * survives for the next drain. Delivery is detected via the outbox probe
     * ([PlaybackOutboxRepository.isPlayedStateIntentDelivered] — candidates
     * have no pre-existing intent rows to confuse the check). Returns whether
     * any flip did not land.
     */
    private suspend fun pushDerivedWatchedFlips(
        derivedWatchedItemIds: Set<String>,
        reconciledItems: MutableSet<String>,
    ): Boolean {
        var anyFailure = false
        for (itemId in derivedWatchedItemIds) {
            val push = runCatching { mediaRepository.markPlayed(itemId) }
            val delivered = push.isSuccess &&
                runCatching { outbox.isPlayedStateIntentDelivered(itemId, played = true) }.getOrDefault(false)
            if (delivered) {
                reconciledItems.add(itemId)
            } else {
                anyFailure = true
            }
        }
        return anyFailure
    }

    /**
     * Reconciles the batch against the server, returning
     * `(undeliveredIntent, anyChanged)`:
     *  - `undeliveredIntent` — some [PlayedStateSync.ReconcileOutcome.UndeliveredIntent]
     *    result: a re-staged intent row is waiting and returning success
     *    would strand it until the 4h periodic backstop (#153), so the drain
     *    must retry. A thrown exception stays silent — reconcile is
     *    best-effort.
     *  - `anyChanged` — some row actually changed, so the caller refreshes
     *    the online UI caches.
     *
     * The reconciliation does a network fetch per item (a forced
     * getMediaDetail read), so a large outbox would otherwise fire N serial
     * detail fetches in this foreground worker. Bound the batch size and run
     * the fetches with bounded concurrency: anything beyond the cap is
     * deferred to the periodic backstop. Each reconciliation is independent
     * and wrapped in runCatching so a single failure cannot abort the batch.
     */
    private suspend fun reconcileBatch(itemsToReconcile: List<String>): Pair<Boolean, Boolean> {
        val results = coroutineScope {
            val gate = Semaphore(MAX_CONCURRENT_RECONCILES)
            itemsToReconcile.map { itemId ->
                async {
                    gate.withPermit {
                        runCatching { playedStateSync.reconcileOfflineRow(itemId) }
                    }
                }
            }.awaitAll()
        }
        val undeliveredIntent = results.any {
            it.getOrNull() == PlayedStateSync.ReconcileOutcome.UndeliveredIntent
        }
        val anyChanged = results.any { it.getOrNull() is PlayedStateSync.ReconcileOutcome.Changed }
        return undeliveredIntent to anyChanged
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.playback_sync_periodic"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.playback_sync_now"
        const val WORK_TAG = "playback_sync"

        private const val TAG = "PlaybackSyncWorker"
        private const val MAX_RETRIES = 3

        /**
         * Retry budget for user-intent events (PLAYED / UNPLAYED / FAVORITE /
         * UNFAVORITE) before dead-lettering (#153). Deliberately much larger
         * than [MAX_RETRIES]: a dead-lettered watched flip is a silently lost
         * user action, while a stale telemetry position is harmless. The worker
         * returns retry() while any intent entry remains under budget, so a
         * flaky first reconnect (expired auth, DNS warm-up) no longer burns
         * the flip forever.
         */
        private const val MAX_INTENT_RETRIES = 10

        /** START/PROGRESS/STOP — position telemetry, superseded by a played flip. */
        private val TELEMETRY_EVENT_TYPES = setOf(
            PlaybackOutboxEventType.START,
            PlaybackOutboxEventType.PROGRESS,
            PlaybackOutboxEventType.STOP,
        )

        /** User-driven state intents — carry their own large retry budget. */
        private val USER_INTENT_EVENT_TYPES = setOf(
            PlaybackOutboxEventType.PLAYED,
            PlaybackOutboxEventType.UNPLAYED,
            PlaybackOutboxEventType.FAVORITE,
            PlaybackOutboxEventType.UNFAVORITE,
        )
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
