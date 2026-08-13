package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Browse / lookup / lifecycle access to offline item identity + metadata.
 *
 * Playback state and freshness baseline each have their own DAO
 * ([PlaybackStateDao], [SyncBaselineDao]) after the row split. The browse
 * queries below read from the [OfflineMediaWithPlayback] view (a single
 * `offline_media ⟕ playback_state` join) so the library grid, episode lists,
 * detail, and search paths still surface resume / watched / favorite state in
 * one query — no N+1 — while a metadata-only lookup ([getById] / [getByIdFlow])
 * stays available for the cast-reference and series-link reads that don't need
 * playback.
 */
@Dao
interface OfflineMediaDao {

    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE mediaType IN ('SERIES', 'MOVIE', 'AUDIO', 'MUSIC')
        ORDER BY createdAt DESC
        LIMIT 500
        """
    )
    fun getTopLevelItems(): Flow<List<OfflineMediaWithPlayback>>

    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE seriesId = :seriesId AND mediaType = 'SEASON'
        ORDER BY seasonNumber ASC
        """
    )
    fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaWithPlayback>>

    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE seasonId = :seasonId AND mediaType = 'EPISODE'
        ORDER BY episodeNumber ASC
        """
    )
    fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaWithPlayback>>

    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE parentId = :parentId
        ORDER BY indexNumber ASC
        """
    )
    fun getChildrenByParent(parentId: String): Flow<List<OfflineMediaWithPlayback>>

    /** Metadata-only single-row lookup (cast-reference, series-link reads). */
    @Query("SELECT * FROM offline_media WHERE id = :id")
    suspend fun getById(id: String): OfflineMediaEntity?

    /** Metadata-only reactive single-row lookup. */
    @Query("SELECT * FROM offline_media WHERE id = :id")
    fun getByIdFlow(id: String): Flow<OfflineMediaEntity?>

    /** Single-row lookup with playback state joined, for the offline detail / item paths. */
    @Query("SELECT * FROM offline_media_with_playback WHERE id = :id")
    suspend fun getByIdWithPlayback(id: String): OfflineMediaWithPlayback?

    /** Reactive single-row lookup with playback state joined. */
    @Query("SELECT * FROM offline_media_with_playback WHERE id = :id")
    fun getByIdWithPlaybackFlow(id: String): Flow<OfflineMediaWithPlayback?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineMediaEntity)

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
        SELECT * FROM offline_media_with_playback
        WHERE (name LIKE :pattern ESCAPE '\' OR seriesName LIKE :pattern ESCAPE '\' OR seasonName LIKE :pattern ESCAPE '\')
        ORDER BY
            CASE WHEN name LIKE :prefixPattern ESCAPE '\' THEN 0 ELSE 1 END,
            name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    suspend fun search(pattern: String, prefixPattern: String, limit: Int): List<OfflineMediaWithPlayback>

    /** Ids of every top-level offline item (for batch freshness checks). */
    @Query("SELECT id FROM offline_media WHERE mediaType IN ('SERIES', 'MOVIE', 'AUDIO', 'MUSIC', 'EPISODE')")
    suspend fun getDownloadedItemIds(): List<String>

    /**
     * Returns the persisted local poster/backdrop paths for an item, so a resync
     * can preserve them when re-persisting metadata (only the image bytes are
     * re-downloaded; the row must keep pointing at the same on-disk files).
     */
    @Query("SELECT posterPath, backdropPath FROM offline_media WHERE id = :itemId")
    suspend fun getLocalImagePaths(itemId: String): OfflineImagePaths?

    /**
     * Returns `(id, peopleJson)` for every offline row. Used by cast-image
     * cleanup after a delete to decide whether a person is still referenced by
     * any remaining offline item before deleting their shared image file — a
     * person can appear across multiple movies/episodes, and the
     * `personId`-keyed image is shared by all of them. Call this *after* the
     * deleted rows are removed so only surviving references are counted.
     */
    @Query("SELECT id, peopleJson FROM offline_media")
    suspend fun getAllPeopleJson(): List<OfflinePeopleRow>
}

/** Local on-disk image path projection for resync path preservation. */
data class OfflineImagePaths(
    val posterPath: String?,
    val backdropPath: String?,
)

/** `(id, peopleJson)` projection for cast-image reference counting on delete. */
data class OfflinePeopleRow(
    val id: String,
    val peopleJson: String?,
)
