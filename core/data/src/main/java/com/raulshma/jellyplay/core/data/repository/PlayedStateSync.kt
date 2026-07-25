package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync.ComputeResult
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class PlayedStateSyncImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val offlineRepository: OfflineRepository,
    private val playbackOutboxRepository: PlaybackOutboxRepository,
    private val offlineModeManager: OfflineModeManager,
    private val mediaRepository: dagger.Lazy<MediaRepository>,
) : PlayedStateSync {

    override suspend fun flip(itemId: String, played: Boolean): Result<Unit> {
        // Offline: apply locally for immediate UI feedback and stage the flip
        // in the outbox so PlaybackSyncWorker delivers it on reconnect.
        if (offlineModeManager.isOffline) {
            runCatching { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            runCatching { playbackOutboxRepository.enqueuePlayedState(itemId, isPlayed = played) }
            return Result.success(Unit)
        }
        val result = if (played) apiClient.markPlayed(itemId) else apiClient.markUnplayed(itemId)
        if (result.isSuccess) {
            // Mirror the server-side cascade into the offline store so
            // downloaded items in this hierarchy stay consistent. Best-effort:
            // a failure here must not surface — the server mutation already
            // succeeded and reconciliation will correct any drift.
            runCatching { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
        } else {
            // Online but the call failed (transient 5xx, auth drop). Don't lose
            // the user's intent: apply locally and enqueue for retry.
            runCatching { offlineRepository.applyPlayedState(itemId, isPlayed = played) }
            runCatching { playbackOutboxRepository.enqueuePlayedState(itemId, isPlayed = played) }
            return Result.success(Unit)
        }
        return result
    }

    override suspend fun reconcileOfflineRow(itemId: String): ComputeResult? {
        val offline = offlineRepository.getOfflineItem(itemId) ?: return null
        // Pull a fresh server view (bypass any cached detail so a stale cache
        // cannot mask a newer played/position state). One operation also drops
        // the series-scoped caches if this item belongs to one.
        mediaRepository.get().invalidateUserDataCaches(itemId)
        val serverItem = mediaRepository.get().getMediaDetail(itemId).getOrNull()?.item ?: return null

        // Server watched (e.g. finished online) always wins — reset the local
        // row so a later offline resume starts the next episode / 0, not a
        // stale half-watched position.
        if (serverItem.isPlayed) {
            offlineRepository.updatePlaybackProgress(
                itemId = itemId,
                positionTicks = 0L,
                percentage = 100.0,
                isPlayed = true,
            )
            return ComputeResult.PLAYED
        }

        // Server unplayed but local played: the user marked the item (or its
        // season/series) unwatched online and that change has not yet reached
        // the local store. Mirror the flip — including the hierarchy cascade —
        // so the offline screen does not show stale watched state.
        if (offline.isPlayed) {
            offlineRepository.applyPlayedState(itemId, isPlayed = false)
            return ComputeResult.UNPLAYED
        }

        // Otherwise the most recent activity wins. Both sides are epoch-millis
        // at heart: the server's `lastPlayedDate` arrives as an ISO string
        // (kotlinx-datetime Instant -> "...Z", or a LocalDateTime when the
        // server omits the offset), and the offline row stores
        // OffsetDateTime.now().toString(). `parseIsoToEpochMillis` accepts
        // both shapes so the comparison is zone-correct regardless of source.
        val serverMillis = parseIsoToEpochMillis(serverItem.lastPlayedDate) ?: return ComputeResult.NOOP
        if (serverMillis > System.currentTimeMillis()) {
            // Sanity guard against future-dated server clocks.
            return ComputeResult.NOOP
        }
        val offlineMillis = offline.lastPlayedDate?.let { parseIsoToEpochMillis(it) } ?: 0L
        if (serverMillis <= offlineMillis) return ComputeResult.NOOP

        val runTime = serverItem.runTimeTicks ?: offline.runTimeTicks
        val percentage = PlayedStateSync.computePlayedPercentage(
            positionTicks = serverItem.playbackPositionTicks,
            runTimeTicks = runTime,
            isPlayed = false,
        )
        offlineRepository.updatePlaybackProgress(
            itemId = itemId,
            positionTicks = serverItem.playbackPositionTicks,
            percentage = percentage,
            isPlayed = false,
        )
        return ComputeResult.POSITION_UPDATED
    }

    companion object {
        private val ISO_OFFSET_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        private val ISO_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /**
         * Parses an ISO-8601 datetime string to epoch-millis, accepting either an
         * offset-aware form (e.g. Jellyfin SDK's `Instant.toString()` →
         * `2024-01-15T10:30:00Z`, or the offline row's `OffsetDateTime.toString()`)
         * or a bare `LocalDateTime` (server sometimes omits the offset). Offset-
         * aware inputs parse in their own zone; bare inputs parse in the system
         * zone (the zone that produced them on-device).
         */
        internal fun parseIsoToEpochMillis(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            // Offset-aware first — covers `...Z`, `...+00:00`, `...+05:30`.
            return runCatching {
                java.time.OffsetDateTime.parse(value, ISO_OFFSET_PARSER).toInstant().toEpochMilli()
            }.getOrElse {
                // Bare LocalDateTime fallback — interpret in the system zone.
                runCatching {
                    LocalDateTime.parse(value, ISO_PARSER)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                }.getOrNull()
            }
        }
    }
}
