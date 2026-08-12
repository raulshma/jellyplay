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

    /**
     * Applies a favorite-state flip to a single offline row. Favorite is
     * per-item (no hierarchy cascade — unlike [applyPlayedStateToHierarchy], the
     * Jellyfin favorite endpoints act on one item only), so this is a targeted
     * single-row UPDATE. No-op (matches zero rows) for a non-downloaded item.
     */
    @Query("UPDATE offline_media SET isFavorite = :isFavorite WHERE id = :itemId")
    suspend fun applyFavoriteState(itemId: String, isFavorite: Boolean)

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

    // ---- Offline resync queries (migration 42→43) ----

    /**
     * Lightweight freshness-baseline projection for batch checks. Carries only
     * the baseline + result columns needed by the sync comparator so a batch
     * check over many items doesn't load full rows (no `peopleJson`, no paths).
     */
    @Query(
        """
        SELECT id, name, syncedPosterTag, syncedBackdropTag, syncedMetadataSignature,
               syncedSubtitleSignature, syncedTrickplaySignature, syncedSegmentsSignature,
               syncedMediaSourceId, syncedMediaSizeBytes, lastSyncedAt,
               syncUpdateAvailable, syncMediaChanged, syncChecking, syncError
        FROM offline_media
        WHERE id IN (:ids)
        """
    )
    suspend fun getSyncBaselines(ids: List<String>): List<SyncBaselineRow>

    /** Single-item baseline lookup, used by the check/resync paths. */
    @Query(
        """
        SELECT id, name, syncedPosterTag, syncedBackdropTag, syncedMetadataSignature,
               syncedSubtitleSignature, syncedTrickplaySignature, syncedSegmentsSignature,
               syncedMediaSourceId, syncedMediaSizeBytes, lastSyncedAt,
               syncUpdateAvailable, syncMediaChanged, syncChecking, syncError
        FROM offline_media
        WHERE id = :itemId
        """
    )
    suspend fun getSyncBaseline(itemId: String): SyncBaselineRow?

    /**
     * Targeted UPDATE of the freshness baseline + result flags after a check or
     * resync completes. Avoids clobbering playback/metadata columns and is
     * cheaper than a full upsert. Mirrors [updatePlaybackProgress].
     */
    @Query(
        """
        UPDATE offline_media
        SET syncedPosterTag = :posterTag,
            syncedBackdropTag = :backdropTag,
            syncedMetadataSignature = :metadataSignature,
            syncedSubtitleSignature = :subtitleSignature,
            syncedTrickplaySignature = :trickplaySignature,
            syncedSegmentsSignature = :segmentsSignature,
            syncedMediaSourceId = :mediaSourceId,
            syncedMediaSizeBytes = :mediaSizeBytes,
            lastSyncedAt = :lastSyncedAt,
            syncUpdateAvailable = :updateAvailable,
            syncMediaChanged = :mediaChanged,
            syncChecking = :checking,
            syncError = :error
        WHERE id = :itemId
        """
    )
    suspend fun updateSyncBaseline(
        itemId: String,
        posterTag: String?,
        backdropTag: String?,
        metadataSignature: String?,
        subtitleSignature: String?,
        trickplaySignature: String?,
        segmentsSignature: String?,
        mediaSourceId: String?,
        mediaSizeBytes: Long?,
        lastSyncedAt: Long?,
        updateAvailable: Int,
        mediaChanged: Int,
        checking: Int,
        error: Int,
    )

    /**
     * Lightweight flag flip for the "check in progress" marker, set before a
     * network fetch and cleared on completion (success or failure). Decoupled
     * from [updateSyncBaseline] so a failed check can clear the marker without
     * touching the baseline columns.
     */
    @Query("UPDATE offline_media SET syncChecking = :checking WHERE id = :itemId")
    suspend fun setSyncChecking(itemId: String, checking: Int)

    /** Clears a stale `syncChecking=1` marker left by a crashed check. */
    @Query("UPDATE offline_media SET syncChecking = 0 WHERE syncChecking = 1")
    suspend fun clearAllCheckingFlags()

    /**
     * Items flagged as having a metadata/image update or a media-file change.
     * Drives the per-row "update available" badge and the downloads-screen
     * aggregate count. Reactive — re-emits as flags flip during a batch check.
     */
    @Query(
        """
        SELECT id, name, mediaType,
               seriesName, seasonNumber, episodeNumber,
               CASE WHEN syncMediaChanged = 1 THEN 1 ELSE 0 END AS mediaFileChanged,
               CASE WHEN syncUpdateAvailable = 1 THEN 1 ELSE 0 END AS updateAvailable,
               CASE WHEN syncChecking = 1 THEN 1 ELSE 0 END AS checking,
               lastSyncedAt
        FROM offline_media
        WHERE syncUpdateAvailable = 1 OR syncMediaChanged = 1
        ORDER BY lastSyncedAt DESC
        """
    )
    fun getItemsWithUpdates(): Flow<List<OfflineSyncUpdateRow>>

    /** Count of items with any pending update, for the appbar badge. */
    @Query("SELECT COUNT(*) FROM offline_media WHERE syncUpdateAvailable = 1 OR syncMediaChanged = 1")
    fun getUpdatesCount(): Flow<Int>

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

/**
 * Freshness-baseline projection — the persisted snapshot a check diffs a fresh
 * [com.raulshma.jellyplay.core.model.MediaDetail] against. See
 * [OfflineMediaDao.getSyncBaselines] / [OfflineMediaDao.getSyncBaseline].
 */
data class SyncBaselineRow(
    val id: String,
    val name: String,
    val syncedPosterTag: String?,
    val syncedBackdropTag: String?,
    val syncedMetadataSignature: String?,
    val syncedSubtitleSignature: String?,
    val syncedTrickplaySignature: String?,
    val syncedSegmentsSignature: String?,
    val syncedMediaSourceId: String?,
    val syncedMediaSizeBytes: Long?,
    val lastSyncedAt: Long?,
    val syncUpdateAvailable: Int,
    val syncMediaChanged: Int,
    val syncChecking: Int,
    val syncError: Int,
)

/**
 * Reactive projection of items flagged for resync — drives the downloads
 * screen's resync sheet and per-row badges without loading full rows.
 */
data class OfflineSyncUpdateRow(
    val id: String,
    val name: String,
    val mediaType: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val mediaFileChanged: Int,
    val updateAvailable: Int,
    val checking: Int,
    val lastSyncedAt: Long?,
)
