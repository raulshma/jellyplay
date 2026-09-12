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
        WHERE mediaType IN ($TOP_LEVEL_MEDIA_TYPES)
        ORDER BY createdAt DESC
        LIMIT 500
        """
    )
    fun getTopLevelItems(): Flow<List<OfflineMediaWithPlayback>>

    /**
     * Downloaded top-level items of one server library folder — the data source
     * behind the library screen's "Downloaded" filter. A top-level offline
     * row's `parentId` is the server folder the item was saved from; in
     * Jellyfin's metadata hierarchy movies and series sit directly under their
     * library view, so `parentId` matches the view id on standard single-folder
     * layouts. (Multi-folder libraries that nest extra physical folders under
     * one view would need a stored view id — rows saved there won't match.)
     */
    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE parentId = :libraryId
          AND mediaType IN ($TOP_LEVEL_MEDIA_TYPES)
        ORDER BY createdAt DESC
        """
    )
    fun getTopLevelItemsInLibrary(libraryId: String): Flow<List<OfflineMediaWithPlayback>>

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

    /**
     * Every episode under a series in one query (season/index ordered — the same
     * order materializing each season's [getEpisodesForSeason] flow produces),
     * with playback state joined. Replaces the per-season flow fan-out on
     * offline series-detail / catalogue paths.
     */
    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE seriesId = :seriesId AND mediaType = 'EPISODE'
        ORDER BY seasonNumber ASC, episodeNumber ASC
        """
    )
    suspend fun getEpisodesForSeries(seriesId: String): List<OfflineMediaWithPlayback>

    /**
     * Every downloaded episode in one reactive query, with playback state
     * joined — the data source behind the offline home's Continue Watching /
     * Next Up rows (episodes are excluded from the top-level library queries
     * by design, so this is the only whole-library episode source). Capped
     * generously; a library past the cap drops whole trailing series from the
     * offline rows rather than corrupting per-series episode order.
     */
    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE mediaType = 'EPISODE'
        ORDER BY seriesId ASC, seasonNumber ASC, episodeNumber ASC
        LIMIT 2000
        """
    )
    fun getDownloadedEpisodes(): Flow<List<OfflineMediaWithPlayback>>

    /**
     * Per-series count of downloaded episodes that are not yet finished —
     * the offline source for the unwatched-count badge on series cards
     * (online cards take the same number from server
     * `userData.unplayedItemCount`, which is never persisted). Only counts
     * an episode when it is both unplayed and below the watched threshold:
     * [watchedThresholdPercent] is the model's `OFFLINE_WATCHED_THRESHOLD`
     * scaled to the 0–100 unit `playback_state` stores, passed in by the
     * caller so this SQL never mirrors the constant (it mirrors the
     * normalization `toMediaItem` applies per item). Reactive so the badge
     * updates as episodes are watched offline.
     */
    @Query(
        """
        SELECT seriesId AS groupedId, COUNT(*) AS unplayedCount
        FROM offline_media_with_playback
        WHERE mediaType = 'EPISODE'
          AND seriesId IN (:seriesIds)
          AND (isPlayed IS NULL OR isPlayed = 0)
          AND (playedPercentage IS NULL OR playedPercentage < :watchedThresholdPercent)
        GROUP BY seriesId
        """
    )
    fun getUnplayedEpisodeCountsBySeriesFlow(
        seriesIds: List<String>,
        watchedThresholdPercent: Double,
    ): Flow<List<UnplayedCountRow>>

    /**
     * Per-season counterpart of [getUnplayedEpisodeCountsBySeriesFlow] —
     * same counting rule, grouped by the episode's `seasonId`, feeding the
     * unwatched-count badge on the offline detail screen's season rows (the
     * online seasons take it from server `userData.unplayedItemCount`).
     */
    @Query(
        """
        SELECT seasonId AS groupedId, COUNT(*) AS unplayedCount
        FROM offline_media_with_playback
        WHERE mediaType = 'EPISODE'
          AND seasonId IN (:seasonIds)
          AND (isPlayed IS NULL OR isPlayed = 0)
          AND (playedPercentage IS NULL OR playedPercentage < :watchedThresholdPercent)
        GROUP BY seasonId
        """
    )
    fun getUnplayedEpisodeCountsBySeasonFlow(
        seasonIds: List<String>,
        watchedThresholdPercent: Double,
    ): Flow<List<UnplayedCountRow>>

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

    /**
     * Metadata-only batch lookup. Prefetches the distinct parent series rows for
     * episode artwork resolution in one query instead of re-querying the shared
     * `seriesId` once per episode.
     */
    @Query("SELECT * FROM offline_media WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<OfflineMediaEntity>

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

    @Query("SELECT COUNT(*) FROM offline_media WHERE mediaType IN ($TOP_LEVEL_MEDIA_TYPES)")
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

    /**
     * Ids of every offline item that gets a batch freshness check — the
     * top-level shelf types plus EPISODE (episodes hang off series rows but
     * are still checked individually).
     */
    @Query("SELECT id FROM offline_media WHERE mediaType IN ($TOP_LEVEL_MEDIA_TYPES, 'EPISODE')")
    suspend fun getDownloadedItemIds(): List<String>

    /**
     * Returns the persisted local poster/backdrop paths for an item, so a resync
     * can preserve them when re-persisting metadata (only the image bytes are
     * re-downloaded; the row must keep pointing at the same on-disk files).
     */
    @Query("SELECT posterPath, backdropPath FROM offline_media WHERE id = :itemId")
    suspend fun getLocalImagePaths(itemId: String): OfflineImagePaths?

    /**
     * Whether any offline row's `peopleJson` references the person whose
     * [personReferenceLikePattern] built [pattern]. Per-candidate existence
     * check for cast-image cleanup after a delete (see
     * [com.raulshma.jellyplay.core.data.repository.OfflineDeletionCore]): the
     * former approach loaded and JSON-decoded EVERY row's multi-KB cast blob
     * (`getAllPeopleJson`, O(rows × blob size) on first pass per blob) just to
     * decide which shared person-image files survive; this answers the same
     * question with one EXISTS per candidate id (≤ dozens — the deleted rows'
     * cast), which stops at the first surviving reference and never ships the
     * blobs across the DB boundary. No query runs at all when the delete
     * produced no candidates.
     *
     * The blob is scanned as text, not decoded: `peopleJson` holds a
     * kotlinx-serialized `List<OfflinePersonInfo>`, so a row references a
     * person iff the JSON fragment `"id":"<personId>"` appears in its blob —
     * [personReferenceLikePattern] builds exactly that fragment and the
     * collision-safety argument lives there.
     */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM offline_media WHERE peopleJson LIKE :pattern ESCAPE '\' LIMIT 1)
        """
    )
    suspend fun isPersonReferenced(pattern: String): Boolean

    /**
     * Offline "More like this": top-level titles (MOVIE/SERIES) whose CSV `genres`
     * column contains [genre]. The `',' || genres || ','` wrap avoids false
     * substring matches ("Action" inside "ActionAdventure"). One genre per call —
     * the repository fans out over the item's genres and de-dupes — because Room
     * cannot expand a list bind into multiple LIKE OR clauses. The top-level set
     * is capped at 500 rows so this scan is cheap in practice.
     */
    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE id != :currentId
          AND mediaType IN ('MOVIE', 'SERIES')
          AND (',' || genres || ',') LIKE '%,' || :genre || ',%'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRelatedByGenre(currentId: String, genre: String, limit: Int): List<OfflineMediaWithPlayback>

    /** Studio fallback for offline "More like this" when genre matches are sparse. */
    @Query(
        """
        SELECT * FROM offline_media_with_playback
        WHERE id != :currentId
          AND mediaType IN ('MOVIE', 'SERIES')
          AND (',' || studios || ',') LIKE '%,' || :studio || ',%'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRelatedByStudio(currentId: String, studio: String, limit: Int): List<OfflineMediaWithPlayback>

    companion object {
        /**
         * The top-level shelf media types — one literal interpolated into the
         * queries above so the lists can't drift apart when a type is added
         * (BOOK was the latest). Compile-time constant, so Room resolves it
         * inside @Query.
         */
        const val TOP_LEVEL_MEDIA_TYPES = "'SERIES', 'MOVIE', 'AUDIO', 'MUSIC', 'BOOK'"
    }
}

/** Local on-disk image path projection for resync path preservation. */
data class OfflineImagePaths(
    val posterPath: String?,
    val backdropPath: String?,
)

/**
 * `(groupedId, unplayedCount)` projection shared by the per-series and
 * per-season unwatched-count queries — `groupedId` is whichever column the
 * query grouped by (the seriesId or seasonId).
 */
data class UnplayedCountRow(
    val groupedId: String,
    val unplayedCount: Int,
)

/**
 * Builds the [OfflineMediaDao.isPersonReferenced] LIKE pattern matching exactly
 * the rows whose `peopleJson` references [personId].
 *
 * `peopleJson` stores a kotlinx-serialized `List<OfflinePersonInfo>`, so the
 * pattern anchors on the real JSON key — `"id":"<personId>"` — rather than a
 * bare quoted id, and is collision-safe on both ends:
 *
 *  - **False positives** — a *different* person id that merely contains
 *    [personId] as a substring cannot match: inside a longer id the characters
 *    adjacent to the substring are id characters (Jellyfin person ids are
 *    GUIDs, `[0-9a-fA-F-]`), never the closing `"` the pattern demands; and a
 *    `"` can never appear inside another JSON string value (kotlinx escapes it
 *    as `\"`), so `"id":"…"` can only line up with a genuine key/value token
 *    boundary — never with the `name`/`role`/… fields.
 *  - **False negatives** — the id is JSON-escaped (`\` → `\\`, `"` → `\"`)
 *    before matching so an id carrying those characters matches its encoded
 *    form in the blob.
 *
 * LIKE wildcards (`%`, `_`) and the escape character itself are then escaped
 * for the query's `ESCAPE '\'` clause — the same convention as
 * `OfflineMediaDao.search` — so an id containing them matches literally.
 */
fun personReferenceLikePattern(personId: String): String {
    val jsonEscaped = personId
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val likeEscaped = jsonEscaped
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%\"id\":\"$likeEscaped\"%"
}
