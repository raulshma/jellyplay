package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.PlaybackOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlaybackOutboxEntity)

    @Query("SELECT * FROM playback_outbox WHERE itemId = :itemId AND deadLetter = 0 ORDER BY createdAt ASC")
    suspend fun getForItem(itemId: String): List<PlaybackOutboxEntity>

    /**
     * Projected single-row variant of [getForItem] for the coalescing paths —
     * one row by (itemId, eventType) instead of reading every live row for the
     * item and Kotlin-filtering (runs ~every 10 s during playback).
     */
    @Query(
        "SELECT * FROM playback_outbox WHERE itemId = :itemId AND eventType = :eventType AND deadLetter = 0 ORDER BY createdAt ASC LIMIT 1"
    )
    suspend fun getForItemByType(itemId: String, eventType: String): PlaybackOutboxEntity?

    /** Single-row primary-key lookup for the deterministic-id state flips. */
    @Query("SELECT * FROM playback_outbox WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaybackOutboxEntity?

    /**
     * Whether an undelivered intent row of [eventType] exists for [itemId] —
     * pending OR dead-lettered. Dead-lettered rows are included on purpose:
     * the intent never reached the server, so callers reconciling
     * "server unplayed vs local played" must not treat the server's state as
     * newer knowledge (#153).
     *
     * Known limitation: the row is keyed by itemId only — the whole offline
     * mirror (`playback_state`, `offline_media`) is single-user-per-database,
     * so this query inherits that assumption rather than scoping to a userId.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playback_outbox
            WHERE itemId = :itemId AND eventType = :eventType
        )
        """
    )
    suspend fun hasUnsyncedIntent(itemId: String, eventType: String): Boolean

    /**
     * Deletes the item's rows for the given event types (pending and
     * dead-lettered) once a push has delivered the latest local intent to the
     * server. Without this, a dead-lettered flip that is later re-pushed by
     * reconciliation would be re-pushed on every reconcile — forever
     * overriding newer state set on other devices (#153).
     */
    @Query(
        """
        DELETE FROM playback_outbox
        WHERE itemId = :itemId AND eventType IN (:eventTypes)
        """
    )
    suspend fun deleteByItemAndTypes(itemId: String, eventTypes: List<String>)

    @Query("SELECT * FROM playback_outbox WHERE deadLetter = 0 ORDER BY createdAt ASC")
    suspend fun getAll(): List<PlaybackOutboxEntity>

    @Query("SELECT * FROM playback_outbox WHERE deadLetter = 0 ORDER BY createdAt ASC")
    fun getAllFlow(): Flow<List<PlaybackOutboxEntity>>

    @Query("SELECT COUNT(*) FROM playback_outbox WHERE deadLetter = 0")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM playback_outbox WHERE deadLetter = 0")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM playback_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playback_outbox WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: String)

    @Query("DELETE FROM playback_outbox WHERE itemId = :itemId AND eventType = :eventType")
    suspend fun deleteForItemByType(itemId: String, eventType: String)

    /**
     * Flags a row as dead-lettered (retry budget exhausted) instead of
     * deleting it. Dead-lettered rows are retained for audit / a future manual
     * "retry sync" affordance, but excluded from [getAll]/[count]/[countFlow]
     * so the drain skips them and the pending-count indicator still clears.
     */
    @Query("UPDATE playback_outbox SET deadLetter = 1 WHERE id = :id")
    suspend fun markDeadLetter(id: String)

    /**
     * Deletes START/PROGRESS/STOP telemetry rows for [itemId] while leaving
     * PLAYED / UNPLAYED flips in place. A delivered STOP supersedes pending
     * telemetry for this item, but the user's explicit played-state intent is
     * orthogonal and must survive until the drain delivers it.
     */
    @Query(
        """
        DELETE FROM playback_outbox
        WHERE itemId = :itemId
          AND eventType IN ('START', 'PROGRESS', 'STOP')
        """
    )
    suspend fun deletePlaybackTelemetryForItem(itemId: String)
}
