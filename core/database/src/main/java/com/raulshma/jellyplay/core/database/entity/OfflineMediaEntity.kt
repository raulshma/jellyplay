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
        Index(value = ["name"]),
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
    // Rich metadata persisted at download time so offline detail screens can
    // show the same information as the online detail screen. Added in
    // migration 29→30; all columns are nullable so existing rows degrade
    // gracefully until re-download.
    val originalTitle: String? = null,
    val criticRating: Float? = null,
    val studios: String? = null,
    val tagline: String? = null,
    val peopleJson: String? = null,
    // ---- Offline resync baseline + result columns (migration 42→43) ----
    // Persisted snapshot of the server's image tags / metadata hash / media source
    // captured at download time and after every resync. Lets a freshness check
    // diff a fresh fetch against this baseline without an extra round-trip, and
    // lets the UI render an "update available" badge from the DB with no network.
    // All nullable/defaulted so existing rows degrade gracefully until first check.
    val syncedPosterTag: String? = null,
    val syncedBackdropTag: String? = null,
    val syncedMetadataSignature: String? = null,
    val syncedMediaSourceId: String? = null,
    val syncedMediaSizeBytes: Long? = null,
    val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val syncUpdateAvailable: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncMediaChanged: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncChecking: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val syncError: Int = 0,
)
