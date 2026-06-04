package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
}
