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

    /**
     * Most recently persisted snapshot for the identity, across cacheKeys.
     * Backs the offline home's layout mirror (issue #147): while offline the
     * home reproduces the last-rendered online layout — section types, titles,
     * per-library rows, order — filtered to downloaded items. Key-agnostic on
     * purpose: a preference change made while offline shifts the cacheKey, and
     * the last-fetched row is still the layout the user last saw.
     */
    @Query(
        """
        SELECT * FROM home_section_cache
        WHERE serverId = :serverId AND userId = :userId
        ORDER BY fetchedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForIdentity(serverId: String, userId: String): HomeSectionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HomeSectionCacheEntity)

    /** Clears every row for a (server, user) — used on logout / identity switch for privacy. */
    @Query("DELETE FROM home_section_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun clearForIdentity(serverId: String, userId: String)
}
