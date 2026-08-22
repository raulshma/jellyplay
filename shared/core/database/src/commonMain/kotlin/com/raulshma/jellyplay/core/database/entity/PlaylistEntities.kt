package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "smart_playlists",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["name"]),
    ],
)
data class SmartPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "criteriaJson")
    val criteriaJson: String,
    @ColumnInfo(defaultValue = "50")
    val maxItems: Int = 50,
    @ColumnInfo(defaultValue = "RANDOM")
    val sortBy: String = "RANDOM",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "mood_playlists",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["name"]),
    ],
)
data class MoodPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    @ColumnInfo(name = "genreKeywordsJson")
    val genreKeywordsJson: String,
    @ColumnInfo(name = "excludedGenresJson")
    val excludedGenresJson: String? = null,
    val minRating: Float? = null,
    @ColumnInfo(defaultValue = "RANDOM")
    val sortBy: String = "RANDOM",
    @ColumnInfo(defaultValue = "50")
    val maxItems: Int = 50,
    val themeColorHex: String? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "mood_playlist_preferences",
)
data class MoodPlaylistPreferenceEntity(
    @PrimaryKey val playlistId: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val lastPlayedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
)
