package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ScanStateEntity)

    @Update
    suspend fun update(entry: ScanStateEntity)

    @Query("SELECT * FROM scan_state WHERE scanId = :scanId")
    suspend fun getById(scanId: String): ScanStateEntity?

    @Query("SELECT * FROM scan_state WHERE scanId = :scanId")
    fun observeById(scanId: String): Flow<ScanStateEntity?>

    /**
     * Progress-only projection for reactive scan UIs. `observeById` re-reads
     * the full row — including the `resultJson` blob (every scanned item) — on
     * every progress write and on any other `scan_state` write; this query
     * only carries the fields [com.raulshma.jellyplay.core.model.ScanProgress]
     * consumes. `SELECT *` stays on the result-JSON path ([getById]).
     */
    @Query("SELECT status, progress, total, itemsFound FROM scan_state WHERE scanId = :scanId")
    fun observeProgress(scanId: String): Flow<ScanStateProgressRow?>

    /**
     * Progress-only write for the scan page loops, replacing their full-row
     * `getById` + [update] round-trip per page. Returns the affected-row
     * count — 0 means the scan row is gone (cancelled/deleted), which is the
     * caller's signal to stop scanning.
     */
    @Query("UPDATE scan_state SET progress = :progress, total = :total, itemsFound = :itemsFound WHERE scanId = :scanId")
    suspend fun updateProgress(scanId: String, progress: Int, total: Int, itemsFound: Int): Int

    @Query("DELETE FROM scan_state WHERE scanId = :scanId")
    suspend fun deleteById(scanId: String)

    @Query("DELETE FROM scan_state WHERE createdAt < :timestamp AND status IN ('COMPLETED', 'FAILED', 'DELETED')")
    suspend fun deleteOlderThan(timestamp: Long): Int
}

/** Progress projection of a `scan_state` row (see [ScanStateDao.observeProgress]). */
data class ScanStateProgressRow(
    val status: String,
    val progress: Int,
    val total: Int,
    val itemsFound: Int,
)
