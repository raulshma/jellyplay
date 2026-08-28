package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT :limit")
    fun getAllDownloads(limit: Int = 500): Flow<List<DownloadEntity>>

    /**
     * One-shot read of every download row, newest first — deliberately uncapped,
     * unlike [getAllDownloads]' 500-row UI window. The force-resync picker uses
     * this so a library larger than the UI window still offers all of its
     * downloaded items for resync.
     */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAllDownloadsSnapshot(): List<DownloadEntity>

    /**
     * One page of `COMPLETED` audio (`MUSIC`/`AUDIO`) downloads, newest
     * first. The media-library DOWNLOADS browse page previously fetched
     * [getAllDownloads]' full 500-row window and filtered/sliced it in
     * Kotlin on every page request; this resolves the same filter, order,
     * and window in one query.
     */
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND mediaType IN ('MUSIC', 'AUDIO') ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getCompletedAudioDownloads(limit: Int, offset: Int): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadEntity?

    /**
     * Targeted single-column read of a download's status. Used by the
     * per-2-s progress loop in DownloadWorker, which previously did a full
     * `SELECT *` (23 cols incl. downloadUrl / errorMessage) just to detect a
     * pause/cancel transition.
     */
    @Query("SELECT status FROM downloads WHERE id = :id")
    suspend fun getStatus(id: String): String?

    @Query("SELECT * FROM downloads WHERE mediaItemId = :mediaItemId")
    suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE mediaItemId = :mediaItemId LIMIT 1")
    fun getDownloadByMediaItemIdFlow(mediaItemId: String): Flow<DownloadEntity?>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('PENDING', 'QUEUED', 'DOWNLOADING', 'PAUSED')")
    fun getActiveDownloadCount(): Flow<Int>

    /**
     * Count of downloads actively in flight — `PENDING`/`QUEUED`/`DOWNLOADING`
     * only (excludes `PAUSED`, which the summary counts as resolved). Used by the
     * download notification group summary so it collapses to one shade item and
     * dismisses itself when the last transfer finishes.
     */
    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('PENDING', 'QUEUED', 'DOWNLOADING')")
    suspend fun getInFlightDownloadCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("DELETE FROM downloads WHERE id IN (:ids)")
    suspend fun deleteDownloadsByIds(ids: List<String>)

    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getTotalDownloadedBytes(): Long

    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, status: String)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = :status, speedBytesPerSec = :speedBytesPerSec WHERE id = :id")
    suspend fun updateProgressWithSpeed(id: String, bytes: Long, status: String, speedBytesPerSec: Long)

    @Query("UPDATE downloads SET errorMessage = :message WHERE id = :id")
    suspend fun updateErrorMessage(id: String, message: String?)

    @Query("UPDATE downloads SET totalSizeBytes = :totalSize WHERE id = :id")
    suspend fun updateTotalSize(id: String, totalSize: Long)

    @Query("SELECT * FROM downloads WHERE seriesId = :seriesId AND status = 'COMPLETED'")
    fun getCompletedDownloadsForSeries(seriesId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE seasonId = :seasonId AND status = 'COMPLETED'")
    fun getCompletedDownloadsForSeason(seasonId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE mediaItemId = :mediaItemId AND status = 'COMPLETED'")
    suspend fun getCompletedDownloadByMediaItemId(mediaItemId: String): DownloadEntity?

    /**
     * Cold-start recovery projection. The recovery initializer on every app
     * launch consumes only [RecoveryRow.id] (and `downloadedBytes` when
     * rewriting a stuck DOWNLOADING row back to PENDING). Materialising up to
     * 500 full 23-column entities — incl. `downloadUrl`, `errorMessage`,
     * `container`, `imageBlurHash` — was pure overhead on the cold-start path.
     */
    @Query("SELECT id, downloadedBytes FROM downloads WHERE status = :status LIMIT 500")
    suspend fun getRecoveryRows(status: String): List<RecoveryRow>

    /**
     * Lightweight rows for downloads whose [status] is in [statuses], used by the
     * network-reconnect path to enumerate interrupted (`PAUSED`/`FAILED`)
     * downloads for bulk resume. Carries `status` and `pausedReason` so the
     * caller can skip user-paused rows (only network interruptions auto-resume)
     * and `retryCount` so it can dead-letter rows past the auto-retry budget.
     * Same projection rationale as [getRecoveryRows].
     */
    @Query(
        """
        SELECT id, downloadedBytes, status, pausedReason, retryCount
        FROM downloads
        WHERE status IN (:statuses)
        LIMIT 500
        """
    )
    suspend fun getInterruptedResumeRows(statuses: List<String>): List<InterruptedResumeRow>

    /**
     * Projected `COMPLETED` rows for the cold-start reconciliation pass, which
     * re-validates each completed download's file against the persisted
     * [totalSizeBytes] and resets truncated/missing files to `PENDING` so they
     * re-download. Carries only the columns the pass needs. Capped at 500 like
     * [getRecoveryRows] so a large offline library doesn't pay an unbounded
     * `File.exists()`/`length()` syscall burst every cold start.
     */
    @Query("SELECT id, downloadPath, totalSizeBytes FROM downloads WHERE status = 'COMPLETED' LIMIT 500")
    suspend fun getCompletedForReconciliation(): List<ReconciliationRow>

    @Query("SELECT * FROM downloads WHERE seriesId = :seriesId")
    suspend fun getDownloadsForSeries(seriesId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE seasonId = :seasonId")
    suspend fun getDownloadsForSeason(seasonId: String): List<DownloadEntity>

    /**
     * Download rows for episodes belonging to [seriesId], resolved by joining
     * `offline_media` on `mediaItemId`. Catches legacy orphan rows whose
     * `downloads.seriesId` is NULL (single-episode downloads queued before the
     * series link was propagated at download time). The episode's
     * `offline_media` row still carries the correct `seriesId`, so the join
     * recovers them so [com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl.deleteOfflineSeries]
     * can clean up their files + rows.
     */
    @Query(
        """
        SELECT d.* FROM downloads d
        INNER JOIN offline_media m ON m.id = d.mediaItemId
        WHERE m.seriesId = :seriesId
        """
    )
    suspend fun getDownloadsForSeriesViaOfflineMedia(seriesId: String): List<DownloadEntity>

    /** Same recovery join as [getDownloadsForSeriesViaOfflineMedia], scoped to a season. */
    @Query(
        """
        SELECT d.* FROM downloads d
        INNER JOIN offline_media m ON m.id = d.mediaItemId
        WHERE m.seasonId = :seasonId
        """
    )
    suspend fun getDownloadsForSeasonViaOfflineMedia(seasonId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE mediaItemId IN (:mediaItemIds)")
    fun getDownloadsByMediaItemIdsFlow(mediaItemIds: List<String>): Flow<List<DownloadEntity>>

    /** One-shot variant of [getDownloadsByMediaItemIdsFlow] for suspend callers. */
    @Query("SELECT * FROM downloads WHERE mediaItemId IN (:mediaItemIds)")
    suspend fun getDownloadsByMediaItemIds(mediaItemIds: List<String>): List<DownloadEntity>

    @Query("UPDATE downloads SET status = 'PENDING' WHERE status IN ('DOWNLOADING', 'QUEUED')")
    suspend fun resetStuckDownloading()

    @Query("SELECT * FROM downloads WHERE status = 'FAILED' LIMIT 100")
    suspend fun getFailedDownloads(): List<DownloadEntity>

    @Query("UPDATE downloads SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int)

    @Query("UPDATE downloads SET pausedReason = :reason WHERE id = :id")
    suspend fun updatePausedReason(id: String, reason: String?)

    /**
     * Single-statement status transition that also writes the pause reason
     * (user pause / reconnect auto-resume). Replaces the former
     * updateProgress + updatePausedReason pair — same end state, one commit and
     * one invalidation per action instead of two.
     */
    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = :status, pausedReason = :reason WHERE id = :id")
    suspend fun updateProgressWithPausedReason(id: String, bytes: Long, status: String, reason: String?)

    /**
     * Manual resume/retry in one statement: status back to `PENDING`, pause
     * reason cleared, auto-retry budget reset — the exact end state of the
     * former updateProgress + updatePausedReason + resetRetryCount triple.
     */
    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = 'PENDING', pausedReason = NULL, retryCount = 0 WHERE id = :id")
    suspend fun markPendingForManualResume(id: String, bytes: Long)

    @Query("UPDATE downloads SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: String)

    @Query("UPDATE downloads SET retryCount = 0 WHERE id = :id")
    suspend fun resetRetryCount(id: String)

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'PAUSED') ORDER BY priority DESC, createdAt ASC")
    fun getPendingDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT DISTINCT seriesId FROM downloads WHERE seriesId IS NOT NULL")
    suspend fun getDownloadedSeriesIds(): List<String>

    /**
     * Single projected read of every `(seriesId, mediaItemId)` pair in the
     * table, intended for the periodic [com.raulshma.jellyplay.core.data.worker.AutoDownloadWorker]
     * which previously issued N per-series queries (N+1) each decoding all 23
     * columns when only `mediaItemId` is consumed. Returning every series's
     * rows in one round-trip lets the worker build an in-memory index once and
     * look it up per series. Movies/standalone items (seriesId IS NULL) are
     * excluded since they are irrelevant to the auto-download check.
     */
    @Query("SELECT seriesId, mediaItemId FROM downloads WHERE seriesId IS NOT NULL")
    suspend fun getDownloadedEpisodeIdsBySeries(): List<EpisodeSeriesRow>

    /**
     * `(seriesId, downloadPath)` projection of [getDownloadsForSeries] for
     * every series in [seriesIds] at once. The offline library's artwork
     * resolution only needs each SERIES item's first downloaded-episode dir;
     * resolving it per series cost one full-row query per series on every
     * Room emission during downloads. Same table order as the per-series
     * query, so "first row per series" picks the same dir.
     */
    @Query("SELECT seriesId, downloadPath FROM downloads WHERE seriesId IN (:seriesIds)")
    suspend fun getDownloadPathsForSeries(seriesIds: List<String>): List<SeriesDownloadPathRow>

    /**
     * Aggregated `(totalSizeBytes, downloadedBytes)` per series, summing every
     * downloaded episode that belongs to each series id in [seriesIds] — one
     * GROUP BY pass instead of the former two correlated SUM subqueries per
     * series row.
     *
     * A SERIES row has no `downloads` entry of its own — episodes are
     * downloaded individually (each row carries `seriesId`). Joining via
     * `offline_media.seriesId` (instead of `downloads.seriesId`) recovers
     * legacy orphan rows whose `downloads.seriesId` is NULL but whose
     * `offline_media` episode row still carries the correct link — the same
     * recovery join used by [getDownloadsForSeriesViaOfflineMedia]. A series
     * with no joined download rows produces no row here; the repository maps a
     * missing aggregate row to 0.
     *
     * Reactive so storage summaries update as episode downloads progress;
     * Room re-emits on any write to `downloads` or `offline_media`.
     */
    @Query(
        """
        SELECT m.seriesId AS seriesId,
            COALESCE(SUM(d.totalSizeBytes), 0) AS totalSizeBytes,
            COALESCE(SUM(d.downloadedBytes), 0) AS downloadedBytes
        FROM downloads d
        INNER JOIN offline_media m ON m.id = d.mediaItemId
        WHERE m.seriesId IN (:seriesIds)
        GROUP BY m.seriesId
        """
    )
    fun getSeriesSizeAggregatesFlow(seriesIds: List<String>): Flow<List<SeriesSizeAggregate>>
}

/**
 * Lightweight row projected out of `downloads` for the cold-start recovery
 * path — see [DownloadDao.getRecoveryRows]. Carries only the columns the
 * recovery initializer actually consumes.
 */
data class RecoveryRow(
    val id: String,
    val downloadedBytes: Long,
)

/**
 * Lightweight row projected out of `downloads` for the reconnect auto-resume
 * path — see [DownloadDao.getInterruptedResumeRows]. Carries the status/reason/
 * retry-count fields the caller needs to decide whether a row is eligible to
 * auto-resume (skip user-paused rows, dead-letter exhausted retries).
 */
data class InterruptedResumeRow(
    val id: String,
    val downloadedBytes: Long,
    val status: String,
    val pausedReason: String?,
    val retryCount: Int,
)

/**
 * Lightweight row projected out of `downloads` for the cold-start
 * reconciliation pass — see [DownloadDao.getCompletedForReconciliation].
 * Carries only the columns needed to validate a completed download's file.
 */
data class ReconciliationRow(
    val id: String,
    val downloadPath: String,
    val totalSizeBytes: Long,
)

/**
 * `(seriesId, mediaItemId)` projection used by
 * [DownloadDao.getDownloadedEpisodeIdsBySeries] so the periodic auto-download
 * worker can fetch all series' episode ids in a single 2-column query instead
 * of N full-row queries.
 */
data class EpisodeSeriesRow(
    val seriesId: String,
    val mediaItemId: String,
)

/**
 * `(seriesId, downloadPath)` projection used by
 * [DownloadDao.getDownloadPathsForSeries] so the offline library resolves
 * every series' artifact dir in one 2-column query.
 */
data class SeriesDownloadPathRow(
    val seriesId: String,
    val downloadPath: String,
)

/**
 * Per-series aggregated size used by the offline library/home summary.
 * See [DownloadDao.getSeriesSizeAggregatesFlow].
 */
data class SeriesSizeAggregate(
    val seriesId: String,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
)
