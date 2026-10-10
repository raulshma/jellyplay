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
     * Every row, newest-write first — the settings-sync adapter's snapshot
     * face (it takes the most-recent slice under its roam cap). Query only:
     * no entity change, so no schema/version bump.
     */
    @Query("SELECT * FROM item_playback_preferences ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ItemPlaybackPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ItemPlaybackPreferenceEntity)

    @Query("DELETE FROM item_playback_preferences WHERE scope = :scope AND key = :key")
    suspend fun deleteByKey(scope: String, key: String)

    @Query("SELECT COUNT(*) FROM item_playback_preferences WHERE scope = :scope")
    suspend fun countByScope(scope: String): Int
}
