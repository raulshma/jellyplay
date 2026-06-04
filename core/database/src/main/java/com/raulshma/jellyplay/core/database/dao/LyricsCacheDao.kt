package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity

@Dao
interface LyricsCacheDao {

    @Query("SELECT * FROM lyrics_cache WHERE itemId = :itemId LIMIT 1")
    suspend fun getByItemId(itemId: String): LyricsCacheEntity?

    @Query("SELECT * FROM lyrics_cache WHERE itemId = :itemId AND provider = :provider LIMIT 1")
    suspend fun getByItemAndProvider(itemId: String, provider: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LyricsCacheEntity)

    @Query("""
        UPDATE lyrics_cache
        SET artistName = :artistName, trackName = :trackName,
            syncedLyrics = :syncedLyrics, plainLyrics = :plainLyrics,
            duration = :duration, lrcLibId = :lrcLibId, fetchedAt = :fetchedAt
        WHERE itemId = :itemId AND provider = :provider
    """)
    suspend fun update(
        itemId: String, provider: String,
        artistName: String?, trackName: String?,
        syncedLyrics: String?, plainLyrics: String?,
        duration: Double?, lrcLibId: Long?, fetchedAt: Long,
    )

    @Query("DELETE FROM lyrics_cache WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("DELETE FROM lyrics_cache WHERE fetchedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
}
