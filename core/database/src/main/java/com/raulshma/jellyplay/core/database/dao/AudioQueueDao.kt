package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioQueueDao {
    @Query("SELECT * FROM audio_queue ORDER BY position ASC")
    fun observeQueue(): Flow<List<AudioQueueEntity>>

    @Query("SELECT * FROM audio_queue ORDER BY position ASC")
    suspend fun getQueue(): List<AudioQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AudioQueueEntity>)

    @Query("DELETE FROM audio_queue")
    suspend fun clearQueue()

    @Query("DELETE FROM audio_queue WHERE id = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("SELECT * FROM audio_queue_state WHERE id = 1")
    fun observeState(): Flow<AudioQueueStateEntity?>

    @Query("SELECT * FROM audio_queue_state WHERE id = 1")
    suspend fun getState(): AudioQueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: AudioQueueStateEntity)
}
