package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SeenMediaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<com.raulshma.jellyplay.core.database.entity.SeenMediaEntity>)

    @Query("SELECT itemId FROM seen_media WHERE itemId IN (:ids)")
    suspend fun getSeenIds(ids: List<String>): List<String>

    @Query("DELETE FROM seen_media WHERE seenAt < :cutoffEpochMillis")
    suspend fun pruneOlderThan(cutoffEpochMillis: Long): Int

    @Query("SELECT COUNT(*) FROM seen_media")
    suspend fun count(): Int

    @Query("SELECT itemId FROM seen_media")
    suspend fun getAllSeenItemIds(): List<String>

    /**
     * Deletes every seen-media row whose [itemIds] matches. Used by the
     * reconciliation pass to drop orphan rows whose underlying library item has
     * been removed server-side, so a re-add of the same id can re-notify.
     */
    @Query("DELETE FROM seen_media WHERE itemId IN (:itemIds)")
    suspend fun deleteByItemIds(itemIds: List<String>): Int
}
