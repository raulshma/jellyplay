package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlaylistDao {
    @Query("SELECT * FROM smart_playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SmartPlaylistEntity>>

    @Query("SELECT * FROM smart_playlists ORDER BY createdAt DESC")
    suspend fun getAll(): List<SmartPlaylistEntity>

    @Query("SELECT * FROM smart_playlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SmartPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: SmartPlaylistEntity)

    @Update
    suspend fun update(playlist: SmartPlaylistEntity)

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM smart_playlists")
    suspend fun deleteAll()
}
