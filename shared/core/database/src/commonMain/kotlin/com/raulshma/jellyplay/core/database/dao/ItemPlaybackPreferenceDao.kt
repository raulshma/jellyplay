package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity

/**
 * DAO for the per-item / per-series playback-language preference table.
 * Follows the Flow-for-observe / suspend-for-write convention used by the
 * rest of this package (see [SearchHistoryDao]).
 */
@Dao
interface ItemPlaybackPreferenceDao {

    @Query("SELECT * FROM item_playback_preferences WHERE scope = :scope AND key = :key LIMIT 1")
    suspend fun getByKey(scope: String, key: String): ItemPlaybackPreferenceEntity?

    /**
     * Every row, newest-write first — the settings-backup slice source's whole
     * store face. Query only: no entity change, so no schema/version bump.
     */
    @Query("SELECT * FROM item_playback_preferences ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ItemPlaybackPreferenceEntity>

    /**
     * The [cap] most-recent rows, newest-write first — [getAll]'s head pushed
     * into the query so the settings-sync adapter's snapshot stops
     * materializing the whole (unbounded) store to take its roam cap in
     * Kotlin (the head of the list IS the roam cap). Served by the `updatedAt`
     * index. Query only: no entity change, so no schema/version bump.
     */
    @Query("SELECT * FROM item_playback_preferences ORDER BY updatedAt DESC LIMIT :cap")
    suspend fun getRecentForRoam(cap: Int): List<ItemPlaybackPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ItemPlaybackPreferenceEntity)

    @Query("DELETE FROM item_playback_preferences WHERE scope = :scope AND key = :key")
    suspend fun deleteByKey(scope: String, key: String)

    @Query("SELECT COUNT(*) FROM item_playback_preferences WHERE scope = :scope")
    suspend fun countByScope(scope: String): Int
}
