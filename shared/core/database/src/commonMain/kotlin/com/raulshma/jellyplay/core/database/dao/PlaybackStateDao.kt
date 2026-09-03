package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity

/**
 * Owns the playback-progress + watched/favorite state for offline items, split
 * out of `offline_media`. The writers are targeted UPSERTs
 * (`INSERT ... ON CONFLICT DO UPDATE`) so:
 *  - a row is created on first contact (no eager seeding of every metadata row),
 *  - a progress tick never clobbers `isFavorite`, and a favorite flip never
 *    clobbers progress.
 *
 * The played-state cascade mirrors the previous single-table `UPDATE … WHERE
 * hierarchy` but, because playback now lives in its own table, the hierarchy
 * match is resolved against `offline_media` (which still owns the
 * `parentId`/`seriesId`/`seasonId` link columns) and UPSERTed here.
 */
@Dao
interface PlaybackStateDao {

    /**
     * Full-row upsert used at download time to seed the server `UserData`
     * snapshot (position / percentage / played / favorite). REPLACE semantics
     * match the previous single-table upsert that wrote these same columns.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackStateEntity)

    /** Single-row lookup (projection / testability). */
    @Query("SELECT * FROM playback_state WHERE id = :id")
    suspend fun getById(id: String): PlaybackStateEntity?

    /**
     * Targeted progress update. INSERTs a row on first contact (defaulting the
     * un-supplied columns), or UPDATEs only the progress columns on conflict —
     * `isFavorite` is preserved. `WHERE id = :itemId` matches zero rows for a
     * non-downloaded item; the INSERT branch is harmless in that case because
     * the repository only calls this for items it has seeded.
     *
     * On conflict, `isPlayed` is sticky: a stored `true` is never downgraded
     * to `false` by a progress write. A tick that races the watched-threshold
     * flip (threshold callback sets `true`, a later sub-threshold tick reports
     * `false`) must not un-watch the row — only the explicit unwatch path
     * ([applyPlayedStateToHierarchy]) clears the flag (#153). `false` still
     * lands on rows that were never watched.
     */
    @Query(
        """
        INSERT INTO playback_state (id, playbackPositionTicks, playedPercentage, isPlayed, lastPlayedDate)
        VALUES (:itemId, :positionTicks, :percentage, :isPlayed, :lastPlayedDate)
        ON CONFLICT(id) DO UPDATE SET
            playbackPositionTicks = excluded.playbackPositionTicks,
            playedPercentage = excluded.playedPercentage,
            isPlayed = playback_state.isPlayed OR excluded.isPlayed,
            lastPlayedDate = excluded.lastPlayedDate
        """
    )
    suspend fun updatePlaybackProgress(
        itemId: String,
        positionTicks: Long?,
        percentage: Double,
        isPlayed: Boolean,
        lastPlayedDate: String?,
    )

    /**
     * Batch-applies a played/unplayed state to a single item and every offline
     * row in its hierarchy (item itself, direct children via `parentId`, and any
     * season/episode under it via `seasonId`/`seriesId`). The hierarchy match
     * runs against `offline_media`; results are UPSERTed here so children that
     * never had a playback row are created (marked played) rather than skipped.
     *
     * Mirrors Jellyfin's `markPlayedItem` cascade. `lastPlayedDate` is set on
     * mark-played and cleared on mark-unplayed to match server `UserData`.
     */
    @Query(
        """
        INSERT INTO playback_state (id, isPlayed, playedPercentage, playbackPositionTicks, lastPlayedDate)
        SELECT id,
               :isPlayed,
               CASE WHEN :isPlayed THEN 100.0 ELSE 0.0 END,
               NULL,
               :lastPlayedDate
        FROM offline_media
        WHERE id = :itemId
           OR parentId = :itemId
           OR seasonId = :itemId
           OR seriesId = :itemId
        ON CONFLICT(id) DO UPDATE SET
            isPlayed = excluded.isPlayed,
            playedPercentage = excluded.playedPercentage,
            playbackPositionTicks = excluded.playbackPositionTicks,
            lastPlayedDate = excluded.lastPlayedDate
        """
    )
    suspend fun applyPlayedStateToHierarchy(
        itemId: String,
        isPlayed: Boolean,
        lastPlayedDate: String?,
    )

    /**
     * Single-item favorite flip. Favorite is per-item (no hierarchy cascade),
     * matching the Jellyfin favorite endpoints. INSERTs a row (defaulting the
     * progress columns) on first contact, UPDATEs only `isFavorite` on conflict.
     */
    @Query(
        """
        INSERT INTO playback_state (id, isFavorite)
        VALUES (:itemId, :isFavorite)
        ON CONFLICT(id) DO UPDATE SET isFavorite = excluded.isFavorite
        """
    )
    suspend fun applyFavoriteState(itemId: String, isFavorite: Boolean)

    @Query("DELETE FROM playback_state WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Cascade helper for series deletes (the `seriesId` link lives on offline_media). */
    @Query(
        """
        DELETE FROM playback_state
        WHERE id IN (SELECT id FROM offline_media WHERE seriesId = :seriesId)
        """
    )
    suspend fun deleteBySeriesId(seriesId: String)

    /** Cascade helper for season deletes (the `seasonId` link lives on offline_media). */
    @Query(
        """
        DELETE FROM playback_state
        WHERE id IN (SELECT id FROM offline_media WHERE seasonId = :seasonId)
        """
    )
    suspend fun deleteBySeasonId(seasonId: String)

    /**
     * Removes rows whose `offline_media` row is gone (orphaned season/series
     * cleanup, and any drift from older data). Called within the repositories'
     * cleanup transaction.
     */
    @Query(
        """
        DELETE FROM playback_state
        WHERE id NOT IN (SELECT id FROM offline_media)
        """
    )
    suspend fun deleteUnreferenced()
}
