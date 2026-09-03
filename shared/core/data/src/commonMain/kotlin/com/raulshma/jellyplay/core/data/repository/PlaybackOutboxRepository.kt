package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.PlayMethod
import kotlinx.coroutines.flow.Flow

/**
 * Type of playback event held in the offline outbox. Stored as its [name]
 * in the `playback_outbox` table.
 *
 * PLAYED / UNPLAYED store a user-driven watched-state flip (mark as watched /
 * unwatched) that could not reach the server. Unlike START/PROGRESS/STOP these
 * carry no session payload — only the target state — and are coalesced to one
 * row per item (latest intent wins).
 */
enum class PlaybackOutboxEventType {
    START,
    PROGRESS,
    STOP,
    PLAYED,
    UNPLAYED,
    FAVORITE,
    UNFAVORITE,
}

/**
 * A playback event pending delivery to the Jellyfin server. Mirrors the
 * payload of [PlaybackRepository.reportPlaybackStart] /
 * [reportPlaybackProgress] / [reportPlaybackStopped].
 *
 * @property recordedAt epoch-millis capture time; used by the sync worker for
 * latest-wins reconciliation against the server's `lastPlayedDate`.
 */
data class PlaybackOutboxEntry(
    val id: String,
    val itemId: String,
    val eventType: PlaybackOutboxEventType,
    val sessionId: String,
    val positionTicks: Long,
    val isPaused: Boolean,
    val playMethod: PlayMethod,
    val mediaSourceId: String?,
    val recordedAt: Long,
    val createdAt: Long,
)

/**
 * Stores playback events that could not be delivered to the server
 * (device was offline, or the HTTP call failed). The
 * [PlaybackSyncWorker][com.raulshma.jellyplay.core.data.worker.PlaybackSyncWorker]
 * drains the outbox on reconnect and periodically.
 *
 * PROGRESS entries are coalesced per item (latest position wins); START and
 * STOP entries are never coalesced with each other. Enqueuing a STOP deletes
 * any superseded pending PROGRESS for the item (the STOP carries the final
 * position). After a STOP is delivered online the per-item START/PROGRESS/STOP
 * telemetry entries are cleared via [deletePlaybackTelemetryForItem].
 */
interface PlaybackOutboxRepository {

    suspend fun enqueueStart(
        itemId: String,
        sessionId: String,
        playMethod: PlayMethod,
        startPositionTicks: Long?,
    )

    suspend fun enqueueProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: PlayMethod,
        mediaSourceId: String?,
    )

    suspend fun enqueueStop(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    )

    /**
     * Stages a user-driven played-state flip (mark as watched / unwatched) for
     * delivery on reconnect. Uses a deterministic id (`"played_state:$itemId"`)
     * so a second enqueue for the same item REPLACE-lands in place — the latest
     * user intent wins, which is the only sensible semantics for a state flip.
     * Does not touch START/PROGRESS/STOP rows for the item: a final STOP and a
     * watched flip are orthogonal and can coexist.
     */
    suspend fun enqueuePlayedState(itemId: String, isPlayed: Boolean)

    /**
     * Stages a user-driven favorite-state flip for delivery on reconnect. Uses a
     * deterministic id (`"favorite_state:$itemId"`) so a re-flip for the same
     * item lands in place — latest intent wins. Mirrors [enqueuePlayedState].
     */
    suspend fun enqueueFavoriteState(itemId: String, isFavorite: Boolean)

    /** Snapshot of all pending entries ordered oldest-first. */
    suspend fun drain(): List<PlaybackOutboxEntry>

    /**
     * Whether an undelivered PLAYED intent (pending or dead-lettered) exists
     * for [itemId]. Consumers reconciling offline rows against the server use
     * this to avoid erasing a local watched flag the server never actually
     * received (#153).
     */
    suspend fun hasUnsyncedPlayedIntent(itemId: String): Boolean

    /**
     * UNPLAYED mirror of [hasUnsyncedPlayedIntent]: an undelivered
     * mark-unwatched intent exists for [itemId], so the server's "watched"
     * state must not override the local intent (#153).
     */
    suspend fun hasUnsyncedUnplayedIntent(itemId: String): Boolean

    /**
     * Deletes the item's played-state intent rows (PLAYED/UNPLAYED, pending
     * and dead-lettered) after a push has delivered the latest local intent
     * to the server — otherwise a dead-lettered flip re-pushed by
     * reconciliation would be re-pushed on every later reconcile (#153).
     */
    suspend fun deletePlayedStateIntents(itemId: String)

    /**
     * Delivery probe for a played-state flip push (#153): `flip()` /
     * `markPlayed` report success even when the server call failed — they
     * apply locally and (re-)stage the intent row instead, precisely so the
     * intent survives. The surviving row is therefore the real delivery
     * signal: absent after the push ⟺ the flip landed on the server.
     *
     * Single home for the probe so the drain (`PlaybackSyncWorker`) and
     * reconcile (`PlayedStateSyncImpl`) cannot drift apart on it.
     */
    suspend fun isPlayedStateIntentDelivered(itemId: String, played: Boolean): Boolean =
        !if (played) {
            hasUnsyncedPlayedIntent(itemId)
        } else {
            hasUnsyncedUnplayedIntent(itemId)
        }

    suspend fun delete(id: String)

    /**
     * Flags the entry as dead-lettered (retry budget exhausted) instead of
     * deleting it. The row is retained for audit / a future manual "retry sync"
     * affordance but skipped by [drain] and excluded from [count]/[countFlow],
     * so a persistently undeliverable entry no longer hard-deletes telemetry the
     * server may already have received.
     */
    suspend fun markDeadLetter(id: String)

    /** Clears all entries for an item. */
    suspend fun deleteForItem(itemId: String)

    /**
     * Clears START/PROGRESS/STOP telemetry rows for an item, leaving any
     * pending PLAYED/UNPLAYED flip in place. Called after a STOP is delivered
     * online: the server now holds the authoritative final position, so the
     * pending telemetry is redundant — but a user's explicit played-state
     * intent is orthogonal to the playback telemetry channel and must still
     * drain.
     */
    suspend fun deletePlaybackTelemetryForItem(itemId: String)

    suspend fun count(): Int

    /** Reactive count of pending outbox entries, for UI indicators. */
    fun countFlow(): Flow<Int>

    /** Reactive snapshot of pending entries ordered oldest-first, for the sync details sheet. */
    fun getAllFlow(): Flow<List<PlaybackOutboxEntry>>
}
