package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity

@Dao
interface HomeSectionCacheDao {

    @Query(
        """
        SELECT * FROM home_section_cache
        WHERE serverId = :serverId AND userId = :userId AND cacheKey = :cacheKey
        LIMIT 1
        """
    )
    suspend fun get(serverId: String, userId: String, cacheKey: String): HomeSectionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HomeSectionCacheEntity)

    /** Clears every row for a (server, user) — used on logout / identity switch for privacy. */
    @Query("DELETE FROM home_section_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun clearForIdentity(serverId: String, userId: String)
}
