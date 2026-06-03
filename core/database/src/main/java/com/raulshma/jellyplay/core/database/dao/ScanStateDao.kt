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

    @Query("DELETE FROM scan_state WHERE scanId = :scanId")
    suspend fun deleteById(scanId: String)

    @Query("DELETE FROM scan_state WHERE createdAt < :timestamp AND status IN ('COMPLETED', 'FAILED', 'DELETED')")
    suspend fun deleteOlderThan(timestamp: Long): Int
}
