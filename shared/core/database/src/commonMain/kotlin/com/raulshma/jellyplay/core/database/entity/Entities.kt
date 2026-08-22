package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "servers",
    indices = [
        Index(value = ["address"], unique = true),
        Index(value = ["userId"]),
    ],
)
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val userId: String? = null,
    val accessToken: String? = null,
    val lastConnected: Long = System.currentTimeMillis(),
    val alternateAddresses: String? = null,
)

@Entity(
    tableName = "users",
    indices = [
        androidx.room.Index(value = ["serverId"]),
        androidx.room.Index(value = ["serverId", "lastConnected"]),
    ]
)
data class UserEntity(
    @PrimaryKey val userId: String,
    val serverId: String,
    val name: String,
    val accessToken: String,
    val primaryImageTag: String? = null,
    val maxParentalAgeRating: Int? = null,
    val enabledFolderIds: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isAdmin: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val canDeleteContent: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val lastConnected: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["mediaItemId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
        Index(value = ["seriesId"]),
        Index(value = ["seasonId"]),
        Index(value = ["mediaItemId", "status"]),
        Index(value = ["seriesId", "status"]),
        Index(value = ["seasonId", "status"]),
        Index(value = ["status", "priority", "createdAt"]),
        // Serves getCompletedAudioDownloads (filter on status + mediaType,
        // order by createdAt) — the music-library DOWNLOADS browse page query.
        Index(value = ["status", "mediaType", "createdAt"]),
    ],
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val mediaItemId: String,
    val name: String,
    val mediaType: String,
    val downloadPath: String,
    val downloadUrl: String,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    @ColumnInfo(defaultValue = "0")
    val speedBytesPerSec: Long = 0L,
    val mediaSourceId: String? = null,
    val imageUrl: String? = null,
    val imageBlurHash: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "0")
    val priority: Int = 0,
    /**
     * Original container format (e.g. "mkv", "mp4", "ts") as reported by the
     * Jellyfin MediaSource. Used at playback time to attach the correct MIME
     * type to ExoPlayer so the right extractor is selected for misnamed files
     * (downloads historically get a hardcoded `.mp4` extension). NULL for
     * pre-existing rows until the item is re-downloaded.
     */
    val container: String? = null,
    /**
     * Why a `PAUSED` row is paused: `"USER"` (user long-pressed Pause) or
     * `"NETWORK"` (an in-flight transfer was interrupted by a network drop).
     * Drives the reconnect auto-resume: only `NETWORK` pauses resume
     * automatically on the next `Offline → Online` transition; `USER` pauses
     * stay paused until the user resumes. NULL when the row is not paused.
     */
    val pausedReason: String? = null,
    /**
     * How many times the network-reconnect path has re-enqueued this row.
     * After [DownloadRepositoryImpl.MAX_AUTO_RETRY] failed auto-resumes the row
     * is dead-lettered (left FAILED for a manual retry) so a persistently
     * failing download (storage full, 404, auth) can't spin forever on every
     * reconnect. Reset to 0 on a successful `COMPLETED` write or a manual
     * resume/retry.
     */
    @ColumnInfo(defaultValue = "0")
    val retryCount: Int = 0,
)

@Entity(
    tableName = "lyrics_cache",
    indices = [
        Index(value = ["fetchedAt"]),
        // Explicit single-column index so the `WHERE itemId = :itemId` lookup
        // (getByItemId, called on every lyrics render) is unambiguously indexed
        // rather than relying on a left-prefix of the composite (itemId, provider).
        Index(value = ["itemId"]),
        Index(value = ["itemId", "provider"], unique = true),
    ],
)
data class LyricsCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val provider: String,
    val artistName: String? = null,
    val trackName: String? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val duration: Double? = null,
    val lrcLibId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val fetchedAt: Long = System.currentTimeMillis(),
)
