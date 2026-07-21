package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.PlayMethod
import kotlinx.coroutines.flow.Flow

/**
 * Type of playback event held in the offline outbox. Stored as its [name]
 * in the `playback_outbox` table.
 */
enum class PlaybackOutboxEventType {
    START,
    PROGRESS,
    STOP,
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
 * STOP entries are never coalesced. After a STOP is delivered the per-item
 * PROGRESS entries are cleared.
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

    /** Snapshot of all pending entries ordered oldest-first. */
    suspend fun drain(): List<PlaybackOutboxEntry>

    suspend fun delete(id: String)

    /** Clears all entries for an item (called after a STOP is delivered). */
    suspend fun deleteForItem(itemId: String)

    suspend fun count(): Int

    /** Reactive count of pending outbox entries, for UI indicators. */
    fun countFlow(): Flow<Int>

    /** Reactive snapshot of pending entries ordered oldest-first, for the sync details sheet. */
    fun getAllFlow(): Flow<List<PlaybackOutboxEntry>>
}
