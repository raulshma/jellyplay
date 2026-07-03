package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_media",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["seriesId"]),
        Index(value = ["seasonId"]),
        Index(value = ["mediaType"]),
        Index(value = ["seriesId", "mediaType"]),
        Index(value = ["seasonId", "mediaType"]),
        Index(value = ["mediaType", "createdAt"]),
    ],
)
data class OfflineMediaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mediaType: String,
    val overview: String? = null,
    val year: Int? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val parentId: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val indexNumber: Int? = null,
    val childCount: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val blurHashPrimary: String? = null,
    val blurHashBackdrop: String? = null,
    val premiereDate: String? = null,
    val genres: String? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    // Playback progress. Nullable/defaulted so existing rows
    // (no recorded progress) resolve to "not started". Added in migration 28→29.
    val playbackPositionTicks: Long? = null,
    @ColumnInfo(defaultValue = "0.0")
    val playedPercentage: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val isPlayed: Boolean = false,
    val lastPlayedDate: String? = null,
)
