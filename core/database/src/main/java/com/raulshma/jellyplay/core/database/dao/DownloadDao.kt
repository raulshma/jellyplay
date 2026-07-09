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

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('PENDING', 'DOWNLOADING', 'PAUSED')")
    fun getActiveDownloadCount(): Flow<Int>

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

    @Query("SELECT * FROM downloads WHERE seriesId = :seriesId")
    suspend fun getDownloadsForSeries(seriesId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE seasonId = :seasonId")
    suspend fun getDownloadsForSeason(seasonId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE mediaItemId IN (:mediaItemIds)")
    fun getDownloadsByMediaItemIdsFlow(mediaItemIds: List<String>): Flow<List<DownloadEntity>>

    @Query("UPDATE downloads SET status = 'PENDING' WHERE status = 'DOWNLOADING'")
    suspend fun resetStuckDownloading()

    @Query("SELECT * FROM downloads WHERE status = 'FAILED' LIMIT 100")
    suspend fun getFailedDownloads(): List<DownloadEntity>

    @Query("UPDATE downloads SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int)

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
 * `(seriesId, mediaItemId)` projection used by
 * [DownloadDao.getDownloadedEpisodeIdsBySeries] so the periodic auto-download
 * worker can fetch all series' episode ids in a single 2-column query instead
 * of N full-row queries.
 */
data class EpisodeSeriesRow(
    val seriesId: String,
    val mediaItemId: String,
)
