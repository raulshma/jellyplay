package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Identity + browsable metadata mirror for an offline item.
 *
 * The historical 49-column row was split along its jobs into three tables,
 * each with one invariant:
 *  - `offline_media` (this entity): identity + the browsable metadata mirror.
 *  - [PlaybackStateEntity]: playback progress + watched/favorite state.
 *  - [SyncBaselineEntity]: freshness baseline signatures + result flags.
 *
 * Previously this row carried four jobs at once with every column
 * nullable/defaultable, so it could represent a bare stub, a full metadata
 * mirror, a progress row, or a freshness baseline row indistinguishably. The
 * split gives each concern its own home and its own invariant; a metadata
 * re-persist no longer needs to copy the sync or playback columns forward to
 * avoid clobbering them.
 *
 * Playback state for a row is read via the `OfflineMediaWithPlayback` LEFT JOIN
 * projection (see [com.raulshma.jellyplay.core.database.dao.OfflineMediaDao]);
 * freshness state via [SyncBaselineEntity]. Hierarchy link columns
 * (`parentId`/`seriesId`/`seasonId`) remain FK-by-convention, matching the rest
 * of the schema.
 */
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
    // Rich metadata persisted at download time so offline detail screens can
    // show the same information as the online detail screen. All columns are
    // nullable so existing rows degrade gracefully until re-download.
    val originalTitle: String? = null,
    val criticRating: Float? = null,
    val studios: String? = null,
    val tagline: String? = null,
    val peopleJson: String? = null,
    // Provider ids (tmdb/imdb/…) and external URLs persisted as JSON blobs at
    // download time so the offline subtitle search can resolve a TMDB/IMDb id
    // without a server round-trip. Nullable so existing rows degrade
    // gracefully until re-download.
    val providerIdsJson: String? = null,
    val externalUrlsJson: String? = null,
    // Download-time snapshot of the item's chapter list, as a JSON blob.
    // Feature-level contract lives on [com.raulshma.jellyplay.core.model.OfflineMediaItem.chapters].
    // Nullable so existing rows degrade gracefully until re-download.
    val chaptersJson: String? = null,
)
