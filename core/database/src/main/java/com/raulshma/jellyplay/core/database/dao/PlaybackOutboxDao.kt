package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.PlaybackOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlaybackOutboxEntity)

    @Query("SELECT * FROM playback_outbox WHERE itemId = :itemId ORDER BY createdAt ASC")
    suspend fun getForItem(itemId: String): List<PlaybackOutboxEntity>

    @Query("SELECT * FROM playback_outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<PlaybackOutboxEntity>

    @Query("SELECT COUNT(*) FROM playback_outbox")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM playback_outbox")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM playback_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playback_outbox WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: String)

    @Query("DELETE FROM playback_outbox WHERE itemId = :itemId AND eventType = :eventType")
    suspend fun deleteForItemByType(itemId: String, eventType: String)
}
