package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaCacheInvalidator
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.isWatchedOffline
import kotlinx.coroutines.sync.Semaphore

/**
 * The moved PlaybackSyncWorker drain body (verbatim, one mechanical fold:
 * `runAttemptCount` became the [PlaybackOutboxDrainer.drainOnce] attempt
 * parameter, and the hand-rolled semaphore + async/awaitAll reconcile ladder
 * folded onto the shared [mapConcurrent] primitive — same permits, same
 * launch shape, same any-folds). `android.util.Log` became the module's Log
 * facade; the notification call sites became [PlaybackOutboxDrainer.Notifier]
 * callbacks (the runCatching-best-effort wrappers moved WITH the calls into
 * the Android adapter, which owns the notification plumbing).
 */
class PlaybackOutboxDrainerImpl(
    private val outbox: PlaybackOutboxRepository,
    private val playbackRepository: PlaybackRepository,
    private val offlineModeManager: OfflineModeManager,
    private val playedStateSync: PlayedStateSync,
    private val offlineRepository: OfflineRepository,
    /**
     * The derived watched flips route through [MediaRepository.markPlayed]
     * (not raw PlayedStateSync.flip) so each flip also drops the detail /
     * catalogue caches the repository owns — a drain that changes server
     * state must not leave in-memory caches serving the pre-drain view to an
     * open detail screen (#153 home/detail coherence).
     */
    private val mediaRepository: MediaRepository,
    /**
     * The post-drain wholesale cache drop. Deliberately off the public
     * [MediaRepository] interface (plan 08) — it arrives through the narrow
     * [MediaCacheInvalidator] port, Koin-bound to the same
     * MediaRepositoryImpl single as [mediaRepository].
     */
    private val cacheInvalidator: MediaCacheInvalidator,
    private val userDataSyncTrigger: PlaybackOutboxDrainer.UserDataSyncTrigger,
    private val notifier: PlaybackOutboxDrainer.Notifier = PlaybackOutboxDrainer.Notifier.NONE,
) : PlaybackOutboxDrainer {

    override suspend fun drainOnce(attempt: Int): PlaybackOutboxDrainer.DrainResult {
        if (offlineModeManager.isOffline) {
            // A manual offline setting is not a transient delivery failure.
            // Finish this run so it cannot occupy the unique one-shot slot with
            // backoff; the platform reconnect triggers will enqueue a fresh,
            // immediate drain when Offline Mode is disabled again.
            return PlaybackOutboxDrainer.DrainResult()
        }
        val pending = outbox.drain()

        // Downloaded items whose played/resume state may have drifted
        // server-side (watched or resumed on the web / another device). These
        // are reconciled even when the outbox is empty so a server-side change
        // propagates to the offline store without this device recording any
        // playback of its own — closes the "fixed online, not reflected
        // offline" gap. Best-effort lookup: a DB read failure degrades to an
        // outbox-only run.
        val downloadedIds = runCatchingRethrowingCancellation { offlineRepository.getDownloadedItemIds() }
            .getOrElse { emptyList() }

        if (pending.isEmpty() && downloadedIds.isEmpty()) return PlaybackOutboxDrainer.DrainResult()

        // Promote to a foreground service while the drain runs (adapter
        // concern) so the user sees a "Syncing watch progress" notification
        // and the OS does not throttle a burst of reconnect-driven drains.
        // Only posted when there is outbox work to push (the notification
        // reports the outbox count); a reconcile-only run with an empty
        // outbox stays a silent background freshness check.
        if (pending.isNotEmpty()) {
            notifier.onDrainStarted(pending.size)
        }

        val playedIntentItemIds = stagedPlayedIntentItemIds(pending)
        val derivedWatchedItemIds = deriveWatchedItemIds(pending, playedIntentItemIds)

        val reconciledItems = mutableSetOf<String>()
        // Derived flips that DELIVERED: their markPlayed already announced on
        // the user-data flow from inside PlayedStateSync.flip — the drain-tail
        // announce must not name them again (exactly-once contract).
        val selfAnnouncedItemIds = mutableSetOf<String>()
        var anyFailure = false
        var deadLetteredCount = 0
        if (pending.isNotEmpty()) {
            val entries = drainPendingEntries(pending, playedIntentItemIds, reconciledItems, attempt)
            anyFailure = entries.anyFailure
            deadLetteredCount = entries.deadLetteredCount
            anyFailure = pushDerivedWatchedFlips(derivedWatchedItemIds, reconciledItems, selfAnnouncedItemIds) || anyFailure
        }

        val itemsToReconcile = (reconciledItems + downloadedIds)
            .distinct()
            .take(MAX_RECONCILE_BATCH)
        var reconcileChanged = false
        var adoptedItemIds: List<String> = emptyList()
        if (itemsToReconcile.isNotEmpty()) {
            val reconcile = reconcileBatch(itemsToReconcile)
            anyFailure = reconcile.undeliveredIntent || anyFailure
            reconcileChanged = reconcile.changedItemIds.isNotEmpty()
            adoptedItemIds = reconcile.changedItemIds
        }

        // Drain done — dismiss the progress notification regardless of outcome
        // (adapter concern). On retry the platform re-runs the drain, which
        // will re-post.
        if (pending.isNotEmpty()) {
            notifier.onDrainFinished()
        }

        // If anything was pushed up OR a downloaded row actually changed during
        // reconcile, the online UI caches (Continue Watching, Next Up, detail)
        // are now stale. Trigger an immediate user-data refresh so the user
        // sees fresh played/progress state on the online home + detail screens
        // instead of waiting for the 60s/2min cache TTLs or the
        // UserDataSyncScheduler periodic tick (12h). KEEP policy collapses rapid
        // reconnects. (The outbox-drain backstop cadence is 4h with a 30m flex
        // — distinct from the user-data cadence referenced here.)
        if (reconciledItems.isNotEmpty() || reconcileChanged) {
            // Synchronous wholesale cache drop — the drain changed server
            // state, and waiting on the async UserDataSyncWorker lets the
            // 2-minute detail cache / 60-second home cache serve the
            // pre-drain view in the meantime (the "home shows it, detail
            // doesn't" report). The worker below is still enqueued for its
            // own warm-refetch behavior.
            runCatchingRethrowingCancellation { cacheInvalidator.invalidateCaches() }
            // Synthetic user-data push: open detail sessions and the home
            // refresher listen on the same flow as WS pushes and refresh —
            // the drain's raw API pushes (replayed entries) may never arrive
            // as a UserDataChanged echo on this socket. Exactly-once: every
            // DELIVERED flip or adopted row is named once — replayed entries
            // and adoptions only here, derived flips only from their own
            // inner flip (excluded via [selfAnnouncedItemIds]), and heal
            // flips (pushUnsyncedIntent) stay silent inside reconcile so
            // this tail is their single announcement.
            mediaRepository.notifyUserDataChanged(
                ((reconciledItems + adoptedItemIds) - selfAnnouncedItemIds).distinct(),
            )
            runCatchingRethrowingCancellation { userDataSyncTrigger.enqueueNow() }
        }

        return PlaybackOutboxDrainer.DrainResult(
            pendingCount = pending.size,
            retriesPending = anyFailure,
            deadLetteredCount = deadLetteredCount,
            reconciledItemIds = reconciledItems.toList(),
            reconcileChanged = reconcileChanged,
        )
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
                runCatchingRethrowingCancellation { outbox.hasUnsyncedPlayedIntent(itemId) }.getOrDefault(false)
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
                runCatchingRethrowingCancellation { !outbox.hasUnsyncedUnplayedIntent(itemId) }.getOrDefault(false)
            }
            .filter { itemId ->
                runCatchingRethrowingCancellation { offlineRepository.getOfflineItem(itemId) }.getOrNull()
                    ?.let { row -> row.isWatchedOffline }
                    ?: false
            }
            .toSet()

    /**
     * Replays the pending entries oldest-first, returning an
     * [EntryDrainOutcome]:
     *  - [EntryDrainOutcome.anyFailure] — some entry failed while still under
     *    its retry budget (→ the drain must retry);
     *  - [EntryDrainOutcome.deadLetteredCount] — entries dead-lettered this
     *    run.
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
        attempt: Int,
    ): EntryDrainOutcome {
        var anyFailure = false
        var deadLetteredCount = 0
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
            val ok = runCatchingRethrowingCancellation { playbackRepository.replayOutboxEntry(entry) }.getOrElse { false }
            if (ok) {
                outbox.delete(entry.id)
                reconciledItems.add(entry.itemId)
            } else {
                val budget = if (entry.eventType in USER_INTENT_EVENT_TYPES) MAX_INTENT_RETRIES else MAX_RETRIES
                if (attempt >= budget) {
                    Log.w(
                        TAG,
                        "Dead-lettering outbox entry ${entry.id} " +
                            "(item=${entry.itemId}, type=${entry.eventType}) " +
                            "after $budget attempts",
                    )
                    outbox.markDeadLetter(entry.id)
                    deadLetteredCount++
                } else {
                    anyFailure = true
                }
            }
            remaining--
            // Update the notification mid-drain so the count ticks down. Only
            // worth a notify() call on meaningful batches to avoid spam.
            if (pending.size > 1 && remaining > 0) {
                notifier.onDrainProgress(remaining)
            }
        }
        return EntryDrainOutcome(anyFailure = anyFailure, deadLetteredCount = deadLetteredCount)
    }

    /**
     * Pushes the derived watched flips last, after telemetry has settled, so
     * the server's final state for these items is played (#153). markPlayed
     * wraps PlayedStateSync.flip with the repository's cache invalidation; on
     * failure the flip stages a PLAYED outbox row itself, so the intent
     * survives for the next drain. Delivery is detected via the outbox probe
     * ([PlaybackOutboxRepository.isPlayedStateIntentDelivered] — candidates
     * have no pre-existing intent rows to confuse the check). Returns whether
     * any flip did not land. A DELIVERED flip's inner PlayedStateSync.flip
     * announces the id itself, so it is recorded in [selfAnnouncedItemIds] —
     * the drain-tail announce skips those (exactly-once).
     */
    private suspend fun pushDerivedWatchedFlips(
        derivedWatchedItemIds: Set<String>,
        reconciledItems: MutableSet<String>,
        selfAnnouncedItemIds: MutableSet<String>,
    ): Boolean {
        var anyFailure = false
        for (itemId in derivedWatchedItemIds) {
            val push = runCatchingRethrowingCancellation { mediaRepository.markPlayed(itemId) }
            val delivered = push.isSuccess &&
                runCatchingRethrowingCancellation { outbox.isPlayedStateIntentDelivered(itemId, played = true) }.getOrDefault(false)
            if (delivered) {
                reconciledItems.add(itemId)
                selfAnnouncedItemIds.add(itemId)
            } else {
                anyFailure = true
            }
        }
        return anyFailure
    }

    /**
     * Reconciles the batch against the server, returning a
     * [ReconcileBatchOutcome]:
     *  - [ReconcileBatchOutcome.undeliveredIntent] — some
     *    [PlayedStateSync.ReconcileOutcome.UndeliveredIntent]
     *    result: a re-staged intent row is waiting and returning success
     *    would strand it until the 4h periodic backstop (#153), so the drain
     *    must retry. A thrown exception stays silent — reconcile is
     *    best-effort.
     *  - [ReconcileBatchOutcome.anyChanged] — some row actually changed, so
     *    the caller refreshes the online UI caches.
     *
     * The reconciliation does a network fetch per item (a forced
     * getMediaDetail read), so a large outbox would otherwise fire N serial
     * detail fetches in this foreground drain. Bound the batch size and run
     * the fetches with bounded concurrency: anything beyond the cap is
     * deferred to the periodic backstop. Each reconciliation is independent
     * and wrapped in runCatchingRethrowingCancellation so a single failure
     * cannot abort the batch (cancellation still propagates).
     */
    private suspend fun reconcileBatch(itemsToReconcile: List<String>): ReconcileBatchOutcome {
        val results = Semaphore(MAX_CONCURRENT_RECONCILES).mapConcurrent(itemsToReconcile) { itemId ->
            runCatchingRethrowingCancellation { playedStateSync.reconcileOfflineRow(itemId) }
        }
        val undeliveredIntent = results.any {
            it.getOrNull() == PlayedStateSync.ReconcileOutcome.UndeliveredIntent
        }
        // mapConcurrent preserves input order, so results zip 1:1 with the ids.
        val changedItemIds = results.zip(itemsToReconcile)
            .filter { (result, _) -> result.getOrNull() is PlayedStateSync.ReconcileOutcome.Changed }
            .map { (_, itemId) -> itemId }
        return ReconcileBatchOutcome(
            undeliveredIntent = undeliveredIntent,
            changedItemIds = changedItemIds,
        )
    }

    /** Outcome of one entry-replay pass: whether the drain must retry, and how many entries died. */
    private data class EntryDrainOutcome(val anyFailure: Boolean, val deadLetteredCount: Int)

    /**
     * Outcome of the bounded reconcile batch: an undelivered intent forces a
     * retry; changedItemIds feeds both the result (via isNotEmpty) and the
     * drain-tail announce — it names every adopted row (delivered push or
     * server-state adoption) so the announce can heal id-matching consumers,
     * not just id-agnostic ones.
     */
    private data class ReconcileBatchOutcome(
        val undeliveredIntent: Boolean,
        val changedItemIds: List<String>,
    )

    companion object {
        private const val TAG = "PlaybackOutboxDrainer"
        private const val MAX_RETRIES = 3

        /**
         * Retry budget for user-intent events (PLAYED / UNPLAYED / FAVORITE /
         * UNFAVORITE) before dead-lettering (#153). Deliberately much larger
         * than [MAX_RETRIES]: a dead-lettered watched flip is a silently lost
         * user action, while a stale telemetry position is harmless. The
         * drain reports retriesPending while any intent entry remains under
         * budget, so a flaky first reconnect (expired auth, DNS warm-up) no
         * longer burns the flip forever.
         */
        private const val MAX_INTENT_RETRIES = 10

        /**
         * Position telemetry, superseded by a played flip. BOOK_PROGRESS rides
         * with the session trio: a stale page position is as harmless as a
         * stale tick, and — since Jellyfin never auto-marks a book played — a
         * staged book position is the drain's only surfacing for the derived
         * watched-flip pass.
         */
        private val TELEMETRY_EVENT_TYPES = setOf(
            PlaybackOutboxEventType.START,
            PlaybackOutboxEventType.PROGRESS,
            PlaybackOutboxEventType.STOP,
            PlaybackOutboxEventType.BOOK_PROGRESS,
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
         * large outbox cannot monopolise the foreground drain with a burst of
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
