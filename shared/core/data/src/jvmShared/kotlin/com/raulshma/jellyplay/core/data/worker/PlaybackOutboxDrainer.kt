package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry

/**
 * The playback-outbox drain choreography: replays START/PROGRESS/STOP events
 * captured while the device was offline, reconciles the local offline store
 * against the server so resume positions stay consistent, and refreshes the
 * online UI caches afterwards.
 *
 * Previously this lived entirely in the legacy Android
 * `core.data.worker.PlaybackSyncWorker`; the move is a body move (same
 * decisions, same order, same budgets) so Android and desktop drain through
 * ONE code path — desktop previously staged outbox rows with no drain
 * machinery at all. The platform adapters own only the platform-shaped bits:
 * the Android worker maps [DrainResult.retriesPending] to WorkManager's
 * retry()/success() and serves the foreground notification through
 * [Notifier]; the desktop scheduler runs one-shot drains on startup and on
 * the offline→online network transition.
 *
 * The drain replays outbox entries through
 * `PlaybackRepository.replayOutboxEntry`, a pure dispatch (no enqueue) so a
 * retry does not recurse back into the outbox; the drainer owns the drain
 * loop (delete on success, retry/dead-letter on failure, reconcile), the
 * repository owns the entry-type → API-call mapping.
 *
 * Latest-wins reconciliation: for each item that has a downloaded offline
 * row, the server's `MediaItem` is fetched and compared. If the server's
 * `lastPlayedDate` is newer than the local `recordedAt`, or the server
 * reports `isPlayed`, the offline row is overwritten from the server — fixing
 * the "watched half offline → finished online → back offline shows stale 50%"
 * case.
 */
interface PlaybackOutboxDrainer {

    /**
     * Runs one full drain pass: offline/empty gates, outbox replay with the
     * per-type retry budgets, derived watched flips (#153), the bounded
     * reconcile batch, and the post-drain cache-invalidate + user-data
     * refresh tail. [attempt] is the retry-budget input (the Android adapter
     * passes WorkManager's runAttemptCount; desktop passes 0 — its drains are
     * fire-and-forget and re-trigger on the next network transition).
     *
     * Never throws except cancellation: every collaborator call whose failure
     * must not abort the drain is wrapped in `runCatchingRethrowingCancellation`,
     * exactly as the worker body it moved from.
     */
    suspend fun drainOnce(attempt: Int): DrainResult

    /**
     * Drain-progress surface, invoked at the exact points the legacy
     * worker called its notification plumbing:
     *  - [onDrainStarted] — a non-empty outbox snapshot is about to drain
     *    (the Android adapter promotes to foreground here);
     *  - [onDrainProgress] — count ticks down mid-drain, only for
     *    multi-entry drains with entries still remaining;
     *  - [onDrainFinished] — the snapshot drained (the adapter dismisses the
     *    notification; on retry WorkManager re-runs the worker, which
     *    re-posts).
     *
     * Implementations must be best-effort (a failing notification path must
     * never fail the drain); [NONE] is the honest no-op for platforms and
     * tests without a notification surface.
     */
    interface Notifier {
        suspend fun onDrainStarted(pendingCount: Int)
        suspend fun onDrainProgress(remaining: Int)
        suspend fun onDrainFinished()

        companion object {
            val NONE: Notifier = object : Notifier {
                override suspend fun onDrainStarted(pendingCount: Int) {}
                override suspend fun onDrainProgress(remaining: Int) {}
                override suspend fun onDrainFinished() {}
            }
        }
    }

    /**
     * The post-drain warm-refetch trigger — the narrow view of the platform
     * scheduler the drain tail needs (Android: UserDataSyncScheduler's
     * KEEP-collapsed one-shot user-data refresh; desktop: no user-data
     * worker — the synchronous cache-invalidate + notifyUserDataChanged
     * fan-out above it already refreshes the open UI).
     */
    fun interface UserDataSyncTrigger {
        fun enqueueNow()
    }

    /**
     * Outcome of one [drainOnce] pass — what the platform adapter decides on
     * plus the counters it reports. All defaults describe the early-exit
     * paths (offline skip, empty outbox, nothing to reconcile).
     *
     * @property pendingCount outbox rows in this drain's snapshot (the count
     *   the Android notification reports).
     * @property retriesPending at least one entry, derived flip, or
     *   re-staged reconcile intent did not land while still under its retry
     *   budget — the caller must come back for another attempt. False once
     *   every failure is resolved or dead-lettered, guaranteeing the outbox
     *   count converges to 0.
     * @property deadLetteredCount entries flagged dead this run (retry
     *   budget exhausted; rows retained, not deleted).
     * @property reconciledItemIds items whose server-facing state moved this
     *   run (delivered pushes, superseded telemetry drops) — the same names
     *   the tail's synthetic user-data push carries.
     * @property reconcileChanged whether the reconcile pass actually changed
     *   a local offline row, even when no outbox push named the item.
     */
    data class DrainResult(
        val pendingCount: Int = 0,
        val retriesPending: Boolean = false,
        val deadLetteredCount: Int = 0,
        val reconciledItemIds: List<String> = emptyList(),
        val reconcileChanged: Boolean = false,
    )
}
