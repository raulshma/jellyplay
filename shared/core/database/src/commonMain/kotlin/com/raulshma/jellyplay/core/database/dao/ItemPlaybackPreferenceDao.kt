package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ItemPlaybackPreferenceEntity)

    @Query("DELETE FROM item_playback_preferences WHERE scope = :scope AND key = :key")
    suspend fun deleteByKey(scope: String, key: String)

    @Query("SELECT COUNT(*) FROM item_playback_preferences WHERE scope = :scope")
    suspend fun countByScope(scope: String): Int
}
