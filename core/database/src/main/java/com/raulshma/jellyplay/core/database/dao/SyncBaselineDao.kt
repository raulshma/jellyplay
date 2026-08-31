package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persistence home of the offline freshness module. Owns the baseline
 * signatures + per-axis result flags that
 * [com.raulshma.jellyplay.core.data.sync.OfflineSyncManager] writes and
 * projects.
 *
 * The previous shape exposed a 14-positional-argument `updateSyncBaseline`
 * updater on `OfflineMediaDao`; tests asserted behaviour by counting argument
 * slots. That collapses here to a single [upsert] taking the whole entity —
 * one argument, one source of truth — and the lossy 5-axis-→-1-flag projection
 * is replaced by reading the persisted per-axis flags directly.
 */
@Dao
interface SyncBaselineDao {

    /** Single-call write of the full baseline + flags. Replaces the 14-arg updater. */
    @Upsert
    suspend fun upsert(entity: SyncBaselineEntity)

    @Query("SELECT * FROM sync_baseline WHERE id = :itemId")
    suspend fun getBaseline(itemId: String): SyncBaselineEntity?

    @Query("SELECT * FROM sync_baseline WHERE id IN (:ids)")
    suspend fun getBaselines(ids: List<String>): List<SyncBaselineEntity>

    /** Reactive projection for the DB-driven freshness badge. */
    @Query("SELECT * FROM sync_baseline WHERE id = :itemId")
    fun getBaselineFlow(itemId: String): Flow<SyncBaselineEntity?>

    @Query("UPDATE sync_baseline SET syncChecking = :checking WHERE id = :itemId")
    suspend fun setSyncChecking(itemId: String, checking: Int)

    /**
     * Canonical home of the subtitle-pending retry state's lifecycle: raises
     * [SyncBaselineEntity.syncSubtitlesPending] (the retry driver the check and
     * resync gates read) plus `syncSubtitlesChanged` + `syncUpdateAvailable` so
     * the downloads-screen badge lights immediately — without touching
     * `lastSyncedAt`, which would otherwise keep the TTL-gated check
     * short-circuiting for up to an hour before surfacing anything. Only a
     * successful fetch clears these; checks and failed fetches preserve them.
     *
     * Atomic by construction: the stub insert guarantees some row carries
     * [itemId] before the flag raise runs, so the mark lands even when the
     * download recipe's minimal-item fallback marks before any baseline was
     * seeded. The stub's null signatures deliberately read as "first contact"
     * to the freshness check, which re-seeds them while retaining the flag.
     */
    @Transaction
    suspend fun markSubtitlesPending(itemId: String) {
        insertSubtitlesPendingStub(itemId)
        raiseSubtitlesPendingFlags(itemId)
    }

    /** The flag-raising half of [markSubtitlesPending]; row must exist. */
    @Query(
        """
        UPDATE sync_baseline
        SET syncSubtitlesPending = 1, syncSubtitlesChanged = 1, syncUpdateAvailable = 1
        WHERE id = :itemId
        """
    )
    suspend fun raiseSubtitlesPendingFlags(itemId: String): Int

    /**
     * The row-guaranteeing half of [markSubtitlesPending]. `OR IGNORE` keeps an
     * existing full baseline intact.
     */
    @Query(
        """
        INSERT OR IGNORE INTO sync_baseline
            (id, syncSubtitlesPending, syncSubtitlesChanged, syncUpdateAvailable)
        VALUES (:itemId, 1, 1, 1)
        """
    )
    suspend fun insertSubtitlesPendingStub(itemId: String)

    /** Clears stale `syncChecking=1` markers left by a crashed check. */
    @Query("UPDATE sync_baseline SET syncChecking = 0 WHERE syncChecking = 1")
    suspend fun clearAllCheckingFlags()

    /**
     * Items flagged as having a resyncable update or a media-file change. Joins
     * `offline_media` for the display fields the downloads sheet renders (name,
     * type, episode context). Reactive — re-emits as flags flip during a batch
     * check. Projects only the columns the sheet consumes.
     */
    @Query(
        """
        SELECT m.id AS id, m.name AS name, m.mediaType AS mediaType,
               m.seriesName AS seriesName, m.seasonNumber AS seasonNumber, m.episodeNumber AS episodeNumber,
               CASE WHEN s.syncMediaChanged = 1 THEN 1 ELSE 0 END AS mediaFileChanged
        FROM sync_baseline s
        INNER JOIN offline_media m ON m.id = s.id
        WHERE s.syncUpdateAvailable = 1 OR s.syncMediaChanged = 1
        ORDER BY s.lastSyncedAt DESC
        """
    )
    fun getItemsWithUpdates(): Flow<List<OfflineSyncUpdateRow>>

    /** Count of items with any pending update, for the appbar badge. */
    @Query("SELECT COUNT(*) FROM sync_baseline WHERE syncUpdateAvailable = 1 OR syncMediaChanged = 1")
    fun getUpdatesCount(): Flow<Int>

    @Query("DELETE FROM sync_baseline WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Cascade helper for series deletes (the `seriesId` link lives on offline_media). */
    @Query(
        """
        DELETE FROM sync_baseline
        WHERE id IN (SELECT id FROM offline_media WHERE seriesId = :seriesId)
        """
    )
    suspend fun deleteBySeriesId(seriesId: String)

    /** Cascade helper for season deletes (the `seasonId` link lives on offline_media). */
    @Query(
        """
        DELETE FROM sync_baseline
        WHERE id IN (SELECT id FROM offline_media WHERE seasonId = :seasonId)
        """
    )
    suspend fun deleteBySeasonId(seasonId: String)

    /**
     * Removes rows whose `offline_media` row is gone. Called within the
     * repositories' cleanup transaction so orphaned season/series cleanup and
     * item deletes leave no stale baseline behind.
     */
    @Query(
        """
        DELETE FROM sync_baseline
        WHERE id NOT IN (SELECT id FROM offline_media)
        """
    )
    suspend fun deleteUnreferenced()
}

/**
 * Reactive projection of items flagged for resync — drives the downloads
 * screen's resync sheet and per-row badges without loading full rows. Carries
 * only the display fields joined from `offline_media` plus the
 * media-file-change flag derived from `sync_baseline`.
 */
data class OfflineSyncUpdateRow(
    val id: String,
    val name: String,
    val mediaType: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val mediaFileChanged: Int,
)
