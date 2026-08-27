package com.raulshma.jellyplay.core.network.arr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Phase W wasm Radarr/Sonarr clients — field-for-field
 * transcriptions of the PRIVATE nested DTOs inside the jvmShared
 * `RadarrApiClientImpl` / `SonarrApiClientImpl`. Same names (those are
 * class-nested there, so no collision with these top-level declarations),
 * same defaults, same optionality: every decode fallback equals the JVM's
 * (missing fields decode to the same values the OkHttp path produced).
 * Decoding runs through [arrSeerrWireJson] — the exact config the JVM impls
 * use (`SeerrApiClientImpl.lenientJson`).
 *
 * These are consumed only by the wasmJs clients; on android/jvm they compile
 * unused (same arrangement as the `library/` / `user/` wire DTOs).
 */

// ── Radarr v3 ───────────────────────────────────────────────────────────────

/** `RadarrApiClientImpl.RadarrQueueResponse` — the `{ records: [...] }` envelope. */
@Serializable
internal data class RadarrQueueResponse(
    val records: List<RadarrQueueResource> = emptyList(),
)

/** `RadarrApiClientImpl.RadarrQueueResource`. */
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
    val quality: RadarrQuality? = null,
    val languages: List<RadarrLanguage> = emptyList(),
    val customFormats: List<RadarrCustomFormat> = emptyList(),
    val statusMessages: List<RadarrStatusMessage> = emptyList(),
    val movie: RadarrMovieResource? = null,
)

/** `RadarrApiClientImpl.RadarrQuality` — the nested `quality.quality.name` walk. */
@Serializable
internal data class RadarrQuality(
    @SerialName("quality") val quality: RadarrQualityName? = null,
) {
    val name: String? get() = quality?.name
}

/** `RadarrApiClientImpl.RadarrQualityName`. */
@Serializable
internal data class RadarrQualityName(val name: String? = null)

/** `RadarrApiClientImpl.RadarrLanguage`. */
@Serializable
internal data class RadarrLanguage(
    val name: String? = null,
)

/** `RadarrApiClientImpl.RadarrCustomFormat`. */
@Serializable
internal data class RadarrCustomFormat(val name: String? = null)

/** `RadarrApiClientImpl.RadarrStatusMessage`. */
@Serializable
internal data class RadarrStatusMessage(
    val title: String? = null,
    val messages: List<String> = emptyList(),
)

/** `RadarrApiClientImpl.RadarrMovieResource` — shared by queue/calendar/wanted/history sub-objects. */
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
    val images: List<RadarrMediaCover> = emptyList(),
)

/** `RadarrApiClientImpl.RadarrMediaCover`. */
@Serializable
internal data class RadarrMediaCover(
    @SerialName("coverType") val coverType: String = "",
    @SerialName("url") val url: String? = null,
    @SerialName("remoteUrl") val remoteUrl: String? = null,
)

/** `RadarrApiClientImpl.RadarrHistoryResponse`. */
@Serializable
internal data class RadarrHistoryResponse(
    val records: List<RadarrHistoryRecord> = emptyList(),
)

/** `RadarrApiClientImpl.RadarrHistoryRecord`. */
@Serializable
internal data class RadarrHistoryRecord(
    val id: Int = 0,
    val eventType: String? = null,
    val date: String? = null,
    val data: Map<String, String> = emptyMap(),
    val movie: RadarrMovieResource? = null,
)

/** `RadarrApiClientImpl.RadarrQueueBulkRequest` — `DELETE /queue/bulk` body. */
@Serializable
internal data class RadarrQueueBulkRequest(val ids: List<Int>)

/** `RadarrApiClientImpl.RadarrIdsBulkRequest` — `DELETE /blocklist/bulk` body. */
@Serializable
internal data class RadarrIdsBulkRequest(val ids: List<Int>)

/** `RadarrApiClientImpl.RadarrBlocklistResponse`. */
@Serializable
internal data class RadarrBlocklistResponse(
    val records: List<RadarrBlocklistRecord> = emptyList(),
)

/** `RadarrApiClientImpl.RadarrBlocklistRecord`. */
@Serializable
internal data class RadarrBlocklistRecord(
    val id: Int = 0,
    val date: String? = null,
    val protocol: String? = null,
    val indexer: String? = null,
    val message: String? = null,
    val movie: RadarrMovieResource? = null,
)

/** `RadarrApiClientImpl.RadarrWantedResponse`. */
@Serializable
internal data class RadarrWantedResponse(
    val records: List<RadarrMovieResource> = emptyList(),
)

/** `RadarrApiClientImpl.RadarrCommandRequest` — `POST /command` body. */
@Serializable
internal data class RadarrCommandRequest(
    val name: String,
    val movieIds: List<Int>? = null,
    val movieId: Int? = null,
)

/** `RadarrApiClientImpl.RadarrMovieMonitorRequest` — `PUT /movie/monitor` body. */
@Serializable
internal data class RadarrMovieMonitorRequest(
    val movieIds: List<Int>,
    val monitored: Boolean,
)

/** `RadarrApiClientImpl.RadarrCommandResource`. */
@Serializable
internal data class RadarrCommandResource(
    val id: Int = 0,
    val name: String = "",
    val status: String = "",
    val message: String? = null,
    val queued: String? = null,
    val started: String? = null,
    val ended: String? = null,
)

// ── Sonarr v3 ───────────────────────────────────────────────────────────────

/** `SonarrApiClientImpl.SonarrQueueResponse` — the `{ records: [...] }` envelope. */
@Serializable
internal data class SonarrQueueResponse(
    val records: List<SonarrQueueResource> = emptyList(),
)

/** `SonarrApiClientImpl.SonarrQueueResource`. */
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
    val quality: SonarrQuality? = null,
    val languages: List<SonarrLanguage> = emptyList(),
    val customFormats: List<SonarrCustomFormat> = emptyList(),
    val statusMessages: List<SonarrStatusMessage> = emptyList(),
    val series: SonarrSeriesResource? = null,
    val episode: SonarrEpisodeResource? = null,
)

/** `SonarrApiClientImpl.SonarrQuality` — the nested `quality.quality.name` walk. */
@Serializable
internal data class SonarrQuality(
    @SerialName("quality") val quality: SonarrQualityName? = null,
) {
    val name: String? get() = quality?.name
}

/** `SonarrApiClientImpl.SonarrQualityName`. */
@Serializable
internal data class SonarrQualityName(val name: String? = null)

/** `SonarrApiClientImpl.SonarrLanguage`. */
@Serializable
internal data class SonarrLanguage(val name: String? = null)

/** `SonarrApiClientImpl.SonarrCustomFormat`. */
@Serializable
internal data class SonarrCustomFormat(val name: String? = null)

/** `SonarrApiClientImpl.SonarrStatusMessage`. */
@Serializable
internal data class SonarrStatusMessage(
    val title: String? = null,
    val messages: List<String> = emptyList(),
)

/** `SonarrApiClientImpl.SonarrSeriesResource` — shared by queue/calendar/wanted/blocklist/history sub-objects. */
@Serializable
internal data class SonarrSeriesResource(
    val id: Int = 0,
    val title: String = "",
    val tvdbId: Int? = null,
    val monitored: Boolean = false,
    val path: String? = null,
    val images: List<SonarrMediaCover> = emptyList(),
)

/** `SonarrApiClientImpl.SonarrEpisodeResource`. */
@Serializable
internal data class SonarrEpisodeResource(
    val id: Int = 0,
    val title: String = "",
    val airDateUtc: String? = null,
    val hasFile: Boolean = false,
    val overview: String? = null,
    val series: SonarrSeriesResource? = null,
)

/** `SonarrApiClientImpl.SonarrMediaCover`. */
@Serializable
internal data class SonarrMediaCover(
    @SerialName("coverType") val coverType: String = "",
    @SerialName("url") val url: String? = null,
    @SerialName("remoteUrl") val remoteUrl: String? = null,
)

/** `SonarrApiClientImpl.SonarrHistoryResponse`. */
@Serializable
internal data class SonarrHistoryResponse(
    val records: List<SonarrHistoryRecord> = emptyList(),
)

/** `SonarrApiClientImpl.SonarrHistoryRecord`. */
@Serializable
internal data class SonarrHistoryRecord(
    val id: Int = 0,
    val eventType: String? = null,
    val date: String? = null,
    val data: Map<String, String> = emptyMap(),
    val series: SonarrSeriesResource? = null,
)

/** `SonarrApiClientImpl.SonarrQueueBulkRequest` — `DELETE /queue/bulk` body. */
@Serializable
internal data class SonarrQueueBulkRequest(val ids: List<Int>)

/** `SonarrApiClientImpl.SonarrIdsBulkRequest` — `DELETE /blocklist/bulk` body. */
@Serializable
internal data class SonarrIdsBulkRequest(val ids: List<Int>)

/** `SonarrApiClientImpl.SonarrEpisodeMonitorRequest` — `PUT /episode/monitor` body. */
@Serializable
internal data class SonarrEpisodeMonitorRequest(
    val episodeIds: List<Int>,
    val monitored: Boolean,
)

/**
 * `SonarrApiClientImpl.SonarrEpisodeLookupResource` — the `/episode`
 * projection the delete & re-download flow filters client-side.
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
 * `SonarrApiClientImpl.SonarrManagedEpisodeResource` — the rich
 * `/episode` projection for the "Manage Series" screen.
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

/** `SonarrApiClientImpl.SonarrEpisodeFileResource`. */
@Serializable
internal data class SonarrEpisodeFileResource(
    val id: Int = 0,
    val size: Double? = null,
    val quality: SonarrQuality? = null,
)

/** `SonarrApiClientImpl.SonarrBlocklistResponse`. */
@Serializable
internal data class SonarrBlocklistResponse(
    val records: List<SonarrBlocklistRecord> = emptyList(),
)

/** `SonarrApiClientImpl.SonarrBlocklistRecord`. */
@Serializable
internal data class SonarrBlocklistRecord(
    val id: Int = 0,
    val date: String? = null,
    val protocol: String? = null,
    val indexer: String? = null,
    val message: String? = null,
    val series: SonarrSeriesResource? = null,
)

/** `SonarrApiClientImpl.SonarrWantedResponse`. */
@Serializable
internal data class SonarrWantedResponse(
    val records: List<SonarrWantedRecord> = emptyList(),
)

/** `SonarrApiClientImpl.SonarrWantedRecord`. */
@Serializable
internal data class SonarrWantedRecord(
    val id: Int = 0,
    val title: String = "",
    val airDateUtc: String? = null,
    val hasFile: Boolean = false,
    val overview: String? = null,
    val series: SonarrSeriesResource? = null,
)

/** `SonarrApiClientImpl.SonarrCommandRequest` — `POST /command` body. */
@Serializable
internal data class SonarrCommandRequest(
    val name: String,
    val seriesId: Int? = null,
    val episodeIds: List<Int>? = null,
    val seasonNumber: Int? = null,
)

/** `SonarrApiClientImpl.SonarrCommandResource`. */
@Serializable
internal data class SonarrCommandResource(
    val id: Int = 0,
    val name: String = "",
    val status: String = "",
    val message: String? = null,
    val queued: String? = null,
    val started: String? = null,
    val ended: String? = null,
)
