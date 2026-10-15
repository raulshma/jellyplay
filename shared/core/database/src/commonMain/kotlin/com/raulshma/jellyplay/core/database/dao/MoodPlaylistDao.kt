package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodPlaylistDao {
    @Query("SELECT * FROM mood_playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MoodPlaylistEntity>>

    @Query("SELECT * FROM mood_playlists ORDER BY createdAt DESC")
    suspend fun getAll(): List<MoodPlaylistEntity>

    @Query("SELECT * FROM mood_playlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MoodPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: MoodPlaylistEntity)

    @Update
    suspend fun update(playlist: MoodPlaylistEntity)

    @Query("DELETE FROM mood_playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    /** The sync tombstone's batch form of [deleteById] — one statement for the whole adopted delete. */
    @Query("DELETE FROM mood_playlists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM mood_playlists")
    suspend fun deleteAll()

    @Query("SELECT * FROM mood_playlist_preferences WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getPreference(playlistId: String): MoodPlaylistPreferenceEntity?

    @Query("SELECT * FROM mood_playlist_preferences")
    fun observePreferences(): Flow<List<MoodPlaylistPreferenceEntity>>

    @Query("SELECT * FROM mood_playlist_preferences")
    suspend fun getAllPreferences(): List<MoodPlaylistPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: MoodPlaylistPreferenceEntity)

    /** Removes one playlist's preference row — the sync adapter's adopted-tombstone face. */
    @Query("DELETE FROM mood_playlist_preferences WHERE playlistId = :playlistId")
    suspend fun deletePreferenceById(playlistId: String)

    /** The sync tombstone's batch form of [deletePreferenceById]. */
    @Query("DELETE FROM mood_playlist_preferences WHERE playlistId IN (:playlistIds)")
    suspend fun deletePreferenceByIds(playlistIds: List<String>)
}
