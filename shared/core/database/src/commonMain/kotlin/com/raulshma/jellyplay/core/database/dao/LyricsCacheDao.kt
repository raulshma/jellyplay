package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity

@Dao
interface LyricsCacheDao {

    @Query("SELECT * FROM lyrics_cache WHERE itemId = :itemId ORDER BY id DESC LIMIT 1")
    suspend fun getByItemId(itemId: String): LyricsCacheEntity?

    @Query("SELECT * FROM lyrics_cache WHERE itemId = :itemId AND provider = :provider LIMIT 1")
    suspend fun getByItemAndProvider(itemId: String, provider: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("DELETE FROM lyrics_cache WHERE fetchedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
}
