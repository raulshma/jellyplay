package com.raulshma.jellyplay.core.network.arr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Radarr/Sonarr v3 clients — the consolidated *arr v3 wire
 * schema the [ArrV3Client] engine and both client impls decode/encode
 * through. Shapes with byte-identical fields across the two services are
 * shared (`Arr*`); only the genuinely divergent rows keep per-service
 * declarations (Sonarr's `series`/`episode` sub-objects vs Radarr's `movie`,
 * and the two command/monitor request bodies whose field names differ on the
 * wire). Defaults, optionality, and every `@SerialName` value are carried
 * over verbatim from the former private per-impl DTOs, so decode fallbacks
 * and encode bytes are unchanged. Decoding/encoding runs through
 * [arrSeerrWireJson] — the exact config the JVM impls use
 * (`SeerrApiClientImpl.lenientJson`).
 */

// ── Shared *arr v3 shapes (field-identical on Radarr AND Sonarr) ────────────

/** The `{ records: [...] }` page envelope both services wrap list endpoints in. */
@Serializable
internal data class ArrRecords<T>(val records: List<T> = emptyList())

/**
 * The nested `quality.quality.name` walk both services use (same wire shape).
 * The Kotlin property names the JSON fields; the outer wire field is
 * `quality`, hence the [SerialName] repetition.
 */
@Serializable
internal data class ArrQuality(
    @SerialName("quality") val quality: ArrQualityName? = null,
) {
    val name: String? get() = quality?.name
}

/** The inner `quality` object of [ArrQuality]. */
@Serializable
internal data class ArrQualityName(val name: String? = null)

/** A queue row's language entry. */
@Serializable
internal data class ArrLanguage(val name: String? = null)

/** A queue row's custom-format entry. */
@Serializable
internal data class ArrCustomFormat(val name: String? = null)

/** A queue row's status message (`statusMessages` array entries). */
@Serializable
internal data class ArrStatusMessage(
    val title: String? = null,
    val messages: List<String> = emptyList(),
)

/** An image row (`images` array) — identical on series and movie resources. */
@Serializable
internal data class ArrMediaCover(
    @SerialName("coverType") val coverType: String = "",
    @SerialName("url") val url: String? = null,
    @SerialName("remoteUrl") val remoteUrl: String? = null,
)

/**
 * Picks the best available poster URL. `remoteUrl` is absolute and
 * preferred; behind a reverse proxy the *arr services often leave
 * `remoteUrl` null and populate only `url` (a path relative to the service
 * root), so fall back to it rather than rendering no poster.
 */
internal fun ArrMediaCover.posterPreference(): String? = remoteUrl ?: url

/** `POST /command` response — identical shape on both services. */
@Serializable
internal data class ArrCommandResource(
    val id: Int = 0,
    val name: String = "",
    val status: String = "",
    val message: String? = null,
    val queued: String? = null,
    val started: String? = null,
    val ended: String? = null,
)

/**
 * The `DELETE /queue/bulk` AND `DELETE /blocklist/bulk` body — a bare ids
 * object on both services (the four former `*BulkRequest` DTOs were
 * byte-identical).
 */
@Serializable
internal data class ArrIdsBody(val ids: List<Int>)

// ── Radarr v3 (rows carrying the `movie` sub-object) ────────────────────────

/** A Radarr queue row (`GET /queue` record): download state + `movie`. */
@Serializable
internal data class RadarrQueueResource(
    val id: Int = 0,
    val downloadId: String? = null,
    val size: Double? = null,
    val sizeleft: Double? = null,
    val timeleft: String? = null,
    val status: String? = null,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val protocol: String? = null,
    val downloadClient: String? = null,
    val indexer: String? = null,
    val outputPath: String? = null,
    val quality: ArrQuality? = null,
    val languages: List<ArrLanguage> = emptyList(),
    val customFormats: List<ArrCustomFormat> = emptyList(),
    val statusMessages: List<ArrStatusMessage> = emptyList(),
    val movie: RadarrMovieResource? = null,
)

/**
 * Radarr's movie resource — shared by the queue / calendar / wanted /
 * history / blocklist sub-objects and the `/movie?tmdbId=` lookups. The
 * Sonarr counterpart is [SonarrSeriesResource] (different fields: tvdbId +
 * path vs tmdbId + movieFileId + release dates), which is why the two stay
 * separate.
 */
@Serializable
internal data class RadarrMovieResource(
    val id: Int = 0,
    val title: String = "",
    val tmdbId: Int? = null,
    val monitored: Boolean = false,
    val hasFile: Boolean = false,
    val movieFileId: Int = 0,
    val inCinemas: String? = null,
    val digitalRelease: String? = null,
    val physicalRelease: String? = null,
    val overview: String? = null,
    val images: List<ArrMediaCover> = emptyList(),
)

/** A Radarr history row (`GET /history` record): event + `movie`. */
@Serializable
internal data class RadarrHistoryRecord(
    val id: Int = 0,
    val eventType: String? = null,
    val date: String? = null,
    val data: Map<String, String> = emptyMap(),
    val movie: RadarrMovieResource? = null,
)

/** A Radarr blocklist row: rejection metadata + `movie`. */
@Serializable
internal data class RadarrBlocklistRecord(
    val id: Int = 0,
    val date: String? = null,
    val protocol: String? = null,
    val indexer: String? = null,
    val message: String? = null,
    val movie: RadarrMovieResource? = null,
)

/** Radarr `POST /command` body — the movie-id fields are Radarr-only wire names. */
@Serializable
internal data class RadarrCommandRequest(
    val name: String,
    val movieIds: List<Int>? = null,
    val movieId: Int? = null,
)

/** Radarr `PUT /movie/monitor` body (Sonarr's counterpart keys `episodeIds`). */
@Serializable
internal data class RadarrMovieMonitorRequest(
    val movieIds: List<Int>,
    val monitored: Boolean,
)

// ── Sonarr v3 (rows carrying the `series` / `episode` sub-objects) ──────────

/** A Sonarr queue row (`GET /queue` record): download state + `series` + `episode`. */
@Serializable
internal data class SonarrQueueResource(
    val id: Int = 0,
    val downloadId: String? = null,
    val size: Double? = null,
    val sizeleft: Double? = null,
    val timeleft: String? = null,
    val status: String? = null,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val protocol: String? = null,
    val downloadClient: String? = null,
    val indexer: String? = null,
    val outputPath: String? = null,
    val quality: ArrQuality? = null,
    val languages: List<ArrLanguage> = emptyList(),
    val customFormats: List<ArrCustomFormat> = emptyList(),
    val statusMessages: List<ArrStatusMessage> = emptyList(),
    val series: SonarrSeriesResource? = null,
    val episode: SonarrEpisodeResource? = null,
)

/**
 * Sonarr's series resource — shared by the queue / calendar / wanted /
 * blocklist / history sub-objects and the `/series?tvdbId=` lookups. The
 * Radarr counterpart is [RadarrMovieResource] (different fields), which is
 * why the two stay separate.
 */
@Serializable
internal data class SonarrSeriesResource(
    val id: Int = 0,
    val title: String = "",
    val tvdbId: Int? = null,
    val monitored: Boolean = false,
    val path: String? = null,
    val images: List<ArrMediaCover> = emptyList(),
)

/**
 * Sonarr's episode row — one shape serves three endpoints whose wire rows
 * are field-identical: `GET /calendar` (episode + parent series), `GET
 * /wanted/missing` (the former `SonarrWantedRecord` twin), and the `episode`
 * sub-object of a queue row.
 */
@Serializable
internal data class SonarrEpisodeResource(
    val id: Int = 0,
    val title: String = "",
    val airDateUtc: String? = null,
    val hasFile: Boolean = false,
    val overview: String? = null,
    val series: SonarrSeriesResource? = null,
)

/** A Sonarr history row (`GET /history` record): event + `series`. */
@Serializable
internal data class SonarrHistoryRecord(
    val id: Int = 0,
    val eventType: String? = null,
    val date: String? = null,
    val data: Map<String, String> = emptyMap(),
    val series: SonarrSeriesResource? = null,
)

/** A Sonarr blocklist row: rejection metadata + `series`. */
@Serializable
internal data class SonarrBlocklistRecord(
    val id: Int = 0,
    val date: String? = null,
    val protocol: String? = null,
    val indexer: String? = null,
    val message: String? = null,
    val series: SonarrSeriesResource? = null,
)

/** Sonarr `POST /command` body — the series/episode fields are Sonarr-only wire names. */
@Serializable
internal data class SonarrCommandRequest(
    val name: String,
    val seriesId: Int? = null,
    val episodeIds: List<Int>? = null,
    val seasonNumber: Int? = null,
)

/** Sonarr `PUT /episode/monitor` body (Radarr's counterpart keys `movieIds`). */
@Serializable
internal data class SonarrEpisodeMonitorRequest(
    val episodeIds: List<Int>,
    val monitored: Boolean,
)

/**
 * Sonarr-only `/episode` projections — the series-management surface has no
 * Radarr twin, so these keep Sonarr-scoped declarations.
 */

/**
 * Projection of `/episode` rows used by the delete & re-download flow.
 * Carries the fields that flow needs (id, episodeFileId, hasFile, monitored)
 * plus the season/episode numbers for client-side filtering.
 */
@Serializable
internal data class SonarrEpisodeLookupResource(
    val id: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeFileId: Int = 0,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
)

/**
 * Rich episode projection for the "Manage Series" screen. Carries every
 * field the management UI needs: season/episode/absolute numbers, title,
 * air date, overview, monitored flag, and (when a file exists) the nested
 * file resource for id + size + quality.
 */
@Serializable
internal data class SonarrManagedEpisodeResource(
    val id: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val absoluteEpisodeNumber: Int? = null,
    val title: String = "",
    val airDateUtc: String? = null,
    val overview: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val episodeFileId: Int = 0,
    val episodeFile: SonarrEpisodeFileResource? = null,
)

/** The `episodeFile` sub-object of [SonarrManagedEpisodeResource]. */
@Serializable
internal data class SonarrEpisodeFileResource(
    val id: Int = 0,
    val size: Double? = null,
    val quality: ArrQuality? = null,
)
