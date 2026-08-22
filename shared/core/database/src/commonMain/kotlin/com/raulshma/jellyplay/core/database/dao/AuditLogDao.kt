package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.MediaAuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MediaAuditLogEntity)

    @Query("SELECT * FROM media_audit_log ORDER BY timestamp DESC LIMIT 500")
    fun getAll(): Flow<List<MediaAuditLogEntity>>

    @Query("SELECT * FROM media_audit_log WHERE actionType = :actionType ORDER BY timestamp DESC LIMIT 500")
    fun getByActionType(actionType: String): Flow<List<MediaAuditLogEntity>>

    @Query("DELETE FROM media_audit_log WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM media_audit_log")
    fun getCount(): Flow<Int>
}
