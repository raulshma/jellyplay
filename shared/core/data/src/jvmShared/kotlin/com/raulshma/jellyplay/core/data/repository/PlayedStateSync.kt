package com.raulshma.jellyplay.core.data.repository

/**
 * Deep module that owns the played / resume-state fan-out and reconciliation.
 *
 * Previously the same domain fact — "item [itemId] is played at position X" —
 * was written by [MediaRepositoryImpl.markPlayed] / [markUnplayed] (fanning out
 * to the API, the offline store, and the outbox), stored in two places, and
 * reconciled by a separate merge in [PlaybackSyncWorker]. The flip recipe (3×
 * `runCatching` per call) was duplicated for played and unplayed, and the
 * `computePlayedPercentage` + `parseIsoToEpochMillis` helpers lived only inside
 * the worker.
 *
 * This module is the single home for both directions:
 *  - [flip] — a user-driven watched-state flip, applied online-or-offline with
 *    the same best-effort mirror + outbox staging contract the callers used to
 *    each re-implement.
 *  - [reconcileOfflineRow] — latest-wins merge of an offline row against the
 *    server view, run by the sync worker after draining the outbox.
 *
 * **Locality:** the flip protocol and the reconcile merge now live together;
 * future "how does a played flip behave" edits touch one module. **Leverage:**
 * one interface, two call sites (`MediaRepositoryImpl`, `PlaybackSyncWorker`).
 *
 * C4p2 note: the interface lives in :shared:core:data jvmShared
 * (`java.time` surface); the impl ([PlayedStateSyncImpl]) stays in the legacy
 * module — its constructor takes the Android-coupled `OfflineModeManager`, so
 * it is not Koin-constructible until the OfflineModeManager/connectivity seam
 * lands.
 */
interface PlayedStateSync {

    /**
     * User-driven played/unplayed flip. Applies the server mutation when online
     * and mirrors it into the offline store regardless of outcome; when offline
     * (or when the online call fails) also stages the flip in the outbox for
     * delivery on reconnect. Always reports success so the caller's optimistic
     * UI flip runs immediately — same contract the previous in-repo paths held.
     */
    suspend fun flip(itemId: String, played: Boolean): Result<Unit>

    /**
     * User-driven favorite toggle. Resolves the current favorite state (from the
     * offline row when offline, from the server when online), flips it, and
     * applies the same online/offline + outbox fan-out contract as [flip]:
     *   - Offline: apply locally + stage a FAVORITE/UNFAVORITE outbox event.
     *   - Online: push via the server; mirror the resolved target into the
     *     offline store. On failure, fall back to local apply + outbox so the
     *     user's intent is never lost.
     * Always reports the resolved target state so the caller's optimistic UI
     * flip is correct regardless of path.
     */
    suspend fun toggleFavorite(itemId: String): Result<Boolean>

    /**
     * Latest-wins reconciliation of one offline row against the server view.
     * Three branches:
     *   - Server played → reset local to played (so a later resume starts clean).
     *   - Server unplayed but local played → mirror the flip + hierarchy cascade.
     *   - Otherwise → most-recent activity wins by timestamp.
     *
     * Returns [ComputeResult] so the caller does not re-derive inputs; returns
     * null when there is nothing to reconcile (no offline row, no server row,
     * stale/future timestamps).
     */
    suspend fun reconcileOfflineRow(itemId: String): ComputeResult?

    /**
     * Outcome of a reconcile attempt. Exposed so the worker can log/count
     * without reaching back into the offline store for state it just wrote.
     */
    enum class ComputeResult { PLAYED, UNPLAYED, POSITION_UPDATED, NOOP }

    companion object {
        /**
         * Percentage of runtime played, derived from ticks. Divide-by-zero
         * guarded. Consolidates the two copies that previously lived in
         * [PlaybackSyncWorker] and [DownloadRepositoryImpl].
         *
         * `isPlayed = true` short-circuits to 100 — matches server UserData
         * semantics where a finished item reports full progress regardless of
         * the last tick position.
         */
        fun computePlayedPercentage(
            positionTicks: Long?,
            runTimeTicks: Long?,
            isPlayed: Boolean,
        ): Double = when {
            isPlayed -> 100.0
            positionTicks == null || positionTicks <= 0L -> 0.0
            runTimeTicks == null || runTimeTicks <= 0L -> 0.0
            else -> ((positionTicks.toDouble() / runTimeTicks.toDouble()) * 100.0)
                .coerceIn(0.0, 100.0)
        }
    }
}
