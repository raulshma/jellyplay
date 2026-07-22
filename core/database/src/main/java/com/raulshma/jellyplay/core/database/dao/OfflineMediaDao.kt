package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineMediaDao {

    @Query("SELECT * FROM offline_media WHERE mediaType IN ('SERIES', 'MOVIE', 'AUDIO', 'MUSIC') ORDER BY createdAt DESC LIMIT 500")
    fun getTopLevelItems(): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE seriesId = :seriesId AND mediaType = 'SEASON' ORDER BY seasonNumber ASC")
    fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE seasonId = :seasonId AND mediaType = 'EPISODE' ORDER BY episodeNumber ASC")
    fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE parentId = :parentId ORDER BY indexNumber ASC")
    fun getChildrenByParent(parentId: String): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE id = :id")
    suspend fun getById(id: String): OfflineMediaEntity?

    @Query("SELECT * FROM offline_media WHERE id = :id")
    fun getByIdFlow(id: String): Flow<OfflineMediaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineMediaEntity)

    /**
     * Updates only the playback-progress columns for a row. A
     * targeted UPDATE avoids clobbering metadata fields and is cheaper than a
     * full upsert. `lastPlayedDate` is set to the supplied ISO timestamp.
     */
    @Query(
        """
        UPDATE offline_media
        SET playbackPositionTicks = :positionTicks,
            playedPercentage = :percentage,
            isPlayed = :isPlayed,
            lastPlayedDate = :lastPlayedDate
        WHERE id = :itemId
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
     * row in its hierarchy: the item itself, its direct children (`parentId`),
     * and any season/episode under it (`seasonId` / `seriesId`). Used when the
     * user marks a season or series played/unplayed online — the Jellyfin
     * `markPlayedItem` endpoint cascades the same change to children server-side,
     * so this mirrors that cascade into the local offline store.
     *
     * `lastPlayedDate` is set on mark-played and cleared on mark-unplayed so the
     * row reflects the server's UserData semantics.
     */
    @Query(
        """
        UPDATE offline_media
        SET isPlayed = :isPlayed,
            playedPercentage = CASE WHEN :isPlayed THEN 100.0 ELSE 0.0 END,
            -- Marking either watched or unwatched is an explicit reset of the
            -- resume state. Keeping an old position when marking watched lets
            -- a later offline resume reopen a title the user intentionally
            -- completed; keeping it when marking unwatched shows stale
            -- Continue Watching progress. Jellyfin clears this field for both
            -- endpoints, so mirror that contract locally.
            playbackPositionTicks = NULL,
            lastPlayedDate = :lastPlayedDate
        WHERE id = :itemId
           OR parentId = :itemId
           OR seasonId = :itemId
           OR seriesId = :itemId
        """
    )
    suspend fun applyPlayedStateToHierarchy(
        itemId: String,
        isPlayed: Boolean,
        lastPlayedDate: String?,
    )

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<OfflineMediaEntity>)

    @Query("DELETE FROM offline_media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offline_media WHERE seriesId = :seriesId")
    suspend fun deleteBySeriesId(seriesId: String)

    @Query("DELETE FROM offline_media WHERE seasonId = :seasonId")
    suspend fun deleteBySeasonId(seasonId: String)

    @Query("DELETE FROM offline_media WHERE mediaType = 'SEASON' AND NOT EXISTS (SELECT 1 FROM offline_media ep WHERE ep.mediaType = 'EPISODE' AND ep.seasonId = offline_media.id AND ep.seasonId IS NOT NULL)")
    suspend fun deleteOrphanedSeasons()

    @Query("DELETE FROM offline_media WHERE mediaType = 'SERIES' AND NOT EXISTS (SELECT 1 FROM offline_media sub WHERE sub.mediaType IN ('SEASON', 'EPISODE') AND sub.seriesId = offline_media.id AND sub.seriesId IS NOT NULL)")
    suspend fun deleteOrphanedSeries()

    @Transaction
    suspend fun cleanupOrphans() {
        deleteOrphanedSeasons()
        deleteOrphanedSeries()
    }

    @Query("SELECT COUNT(*) FROM offline_media WHERE mediaType IN ('SERIES', 'MOVIE', 'AUDIO', 'MUSIC')")
    fun getOfflineItemCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM offline_media
        WHERE (name LIKE :pattern ESCAPE '\' OR seriesName LIKE :pattern ESCAPE '\' OR seasonName LIKE :pattern ESCAPE '\')
        ORDER BY
            CASE WHEN name LIKE :prefixPattern ESCAPE '\' THEN 0 ELSE 1 END,
            name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    suspend fun search(pattern: String, prefixPattern: String, limit: Int): List<OfflineMediaEntity>
}
