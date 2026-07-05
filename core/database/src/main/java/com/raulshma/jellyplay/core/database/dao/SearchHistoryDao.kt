package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history WHERE userId = :userId ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecent(userId: String, limit: Int = 50): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM search_history WHERE userId = :userId")
    suspend fun clearAll(userId: String)

    @Query("SELECT COUNT(*) FROM search_history WHERE userId = :userId")
    suspend fun getCount(userId: String): Int

    @Query("DELETE FROM search_history WHERE userId = :userId AND id NOT IN (SELECT id FROM search_history WHERE userId = :userId ORDER BY searchedAt DESC LIMIT :keepCount)")
    suspend fun evictOldest(userId: String, keepCount: Int = 50)

    /**
     * Inserts a query and trims the user's history to [keepCount] in a single
     * transaction. Was two separate transactions (2 Flow re-emissions) — wrapping
     * them means the history Flow re-emits once and the insert+evict are atomic.
     */
    @Transaction
    suspend fun insertAndEvict(entity: SearchHistoryEntity, keepCount: Int = 50) {
        insert(entity)
        evictOldest(entity.userId, keepCount)
    }
}
