package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AudioQueueDao {
    @Query("SELECT * FROM audio_queue ORDER BY position ASC")
    abstract fun observeQueue(): Flow<List<AudioQueueEntity>>

    @Query("SELECT * FROM audio_queue ORDER BY position ASC")
    abstract suspend fun getQueue(): List<AudioQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<AudioQueueEntity>)

    @Query("DELETE FROM audio_queue")
    abstract suspend fun clearQueue()

    @Query("DELETE FROM audio_queue WHERE id = :itemId")
    abstract suspend fun deleteById(itemId: String)

    @Query("SELECT * FROM audio_queue_state WHERE id = 1")
    abstract fun observeState(): Flow<AudioQueueStateEntity?>

    @Query("SELECT * FROM audio_queue_state WHERE id = 1")
    abstract suspend fun getState(): AudioQueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveState(state: AudioQueueStateEntity)

    @Transaction
    open suspend fun replaceQueue(items: List<AudioQueueEntity>) {
        clearQueue()
        insertAll(items)
    }
}
