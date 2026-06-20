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

    @Query("SELECT * FROM downloads WHERE status = :status LIMIT 500")
    suspend fun getDownloadsByStatus(status: String): List<DownloadEntity>

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
}
