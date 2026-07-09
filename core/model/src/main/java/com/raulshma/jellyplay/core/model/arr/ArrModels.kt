package com.raulshma.jellyplay.core.model.arr

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.TmdbImageUrls
import kotlinx.serialization.Serializable

/**
 * Which *arr service a [ArrServerConfig] targets. Radarr manages movies,
 * Sonarr manages series. Kept as a pure identifier in `core/model` so it can be
 * reused across the network, datastore, and feature layers without pulling
 * Android or OkHttp dependencies.
 */
@Immutable
@Serializable
enum class ArrServiceKind {
    RADARR,
    SONARR,
}

/**
 * Connection configuration for a single Radarr or Sonarr instance.
 *
 * Auto-discovered entries (sourced from Seerr's `/service/{radarr,sonarr}`
 * endpoints) carry [isManual] = false; manually-entered entries carry
 * [isManual] = true. The two lists are merged by [ArrRepository] and de-duped
 * by [baseUrl] so a server configured in both Seerr and the manual override
 * screen does not double-fetch.
 *
 * Stored verbatim (manual entries only) inside [ArrSecureCredentialsStore]'s
 * encrypted preferences, hence [Serializable].
 */
@Immutable
@Serializable
data class ArrServerConfig(
    /** Stable identifier; auto-discovered servers use their Seerr id ("radarr-3"), manual ones use a UUID-like slug. */
    val id: String,
    val baseUrl: String,
    val apiKey: String,
    val name: String,
    val kind: ArrServiceKind,
    val isManual: Boolean = false,
)

/**
 * Aggregated view of all configured Radarr + Sonarr servers, produced by
 * [com.raulshma.jellyplay.core.data.repository.ArrRepository.resolveServers].
 *
 * [discoveryError] is non-null when Seerr auto-discovery failed for a reason
 * the user can act on (most importantly, the Seerr account lacking Admin
 * permission — `/settings/...` is admin-only, unlike `/service/...`). It is null
 * when discovery succeeded (including the "Seerr has no servers configured"
 * case, which is a successful empty result rather than an error).
 */
@Immutable
data class ArrServiceSummary(
    val radarrServers: List<ArrServerConfig> = emptyList(),
    val sonarrServers: List<ArrServerConfig> = emptyList(),
    val discoveryError: ArrDiscoveryError? = null,
) {
    val isEmpty: Boolean get() = radarrServers.isEmpty() && sonarrServers.isEmpty()
}

/**
 * Why Seerr auto-discovery could not resolve servers. Surfaced verbatim in the
 * settings UI so users can tell an auth/permission problem apart from "Seerr
 * has nothing configured".
 */
@Immutable
sealed class ArrDiscoveryError {
    /**
     * The Seerr account JellyPlay uses lacks Admin permission. `/settings/radarr`
     * and `/settings/sonarr` (the only endpoints returning the real apiKey +
     * hostname) require Admin in Seerr; `/service/...` is non-sensitive and won't
     * do. Detected via HTTP 401/403.
     */
    data object NoAdminPermission : ArrDiscoveryError()

    /**
     * Any other discovery failure (network, 5xx, parse). [message] is the
     * friendly ApiException text.
     */
    data class Other(val message: String) : ArrDiscoveryError()
}

@Immutable
@Serializable
enum class ArrMediaType {
    MOVIE,
    SERIES,
}

/**
 * Collapses Radarr/Sonarr's multi-field download state into a single
 * render-friendly enum.
 *
 * Radarr and Sonarr each expose a download client status string ("queued",
 * "downloading", "paused", "completed", "failed", "warning"), a
 * `trackedDownloadStatus` ("ok" / "warning" / "error"), and an
 * `trackedDownloadState` ("importPending" / "imported" / "importBlocked").
 * [fromApi] reduces the three to one value the UI can switch on.
 */
@Immutable
enum class ArrDownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    WARNING,
    IMPORTED,
    UNKNOWN;

    companion object {
        fun fromApi(
            status: String?,
            trackedDownloadStatus: String? = null,
            trackedDownloadState: String? = null,
        ): ArrDownloadStatus {
            // Import state takes priority: a download that has finished but is
            // blocked from importing is the state users most need to see.
            val lower = status?.lowercase()?.trim().orEmpty()
            val tracked = trackedDownloadStatus?.lowercase()?.trim().orEmpty()
            val state = trackedDownloadState?.lowercase()?.trim().orEmpty()

            if (state == "imported" || lower == "imported") return IMPORTED
            if (tracked == "error" || lower == "failed") return FAILED
            if (tracked == "warning" || lower == "warning") return WARNING

            return when (lower) {
                "queued" -> QUEUED
                "downloading", "leeching" -> DOWNLOADING
                "paused" -> PAUSED
                "completed", "seeding" -> COMPLETED
                else -> UNKNOWN
            }
        }
    }
}

/**
 * A single row from the `/api/v3/queue` endpoint of either *arr, mapped to the
 * shared model. [progress] is a 0..1 fraction; UIs multiply by 100 for display.
 *
 * Carries the full set of fields surfaced by Sonarr v4 / Radarr v3 queue
 * resources: quality, languages, custom formats, download client, indexer,
 * estimated output path, and the `statusMessages` array the *arr UI shows when
 * a download is stuck / blocked.
 */
@Immutable
@Serializable
data class ArrQueueItem(
    /**
     * The *arr queue row's primary key (`id` on the `/queue` resource). Used
     * for delete / grab / import paths. NOT the download-client guid
     * (Radarr/Sonarr also expose a `downloadId`/`downloadGuid` field for that);
     * the name `queueId` avoids the ambiguity that led to a previous footgun.
     */
    val queueId: Int,
    /**
     * The download-client guid (`downloadId` on the *arr `QueueResource`).
     * Distinct from [queueId]: this is the id the download client (qBittorrent
     * / SABnzbd / etc.) uses, and it's what the *arr `GET /manualimport` and
     * `POST /manualimport` endpoints key off when forcing an import. Null when
     * the queue row lacks one (rare; legacy/untracked rows).
     */
    val downloadId: String? = null,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val title: String,
    val status: ArrDownloadStatus,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val progress: Float = 0f,
    val sizeBytes: Long? = null,
    val sizeLeft: Long? = null,
    val timeLeft: String? = null,
    val protocol: String? = null,
    val downloadClient: String? = null,
    val indexer: String? = null,
    val outputPath: String? = null,
    val quality: String? = null,
    val languages: List<String> = emptyList(),
    val customFormats: List<String> = emptyList(),
    val messages: List<ArrQueueMessage> = emptyList(),
    /**
     * Stable id of the [ArrServerConfig] this row originated from, set by
     * [com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl] when it
     * fans out across servers. Lets management actions (delete / grab / import)
     * route to the owning server without re-querying every instance.
     */
    val serverId: String = "",
    /** Which service kind owns this row (Radarr = movie, Sonarr = series). */
    val serverKind: ArrServiceKind = ArrServiceKind.RADARR,
) {
    /** Bytes already downloaded, derived for convenience. Null when either input is null. */
    val downloadedBytes: Long?
        get() = if (sizeBytes != null && sizeLeft != null) sizeBytes - sizeLeft else null

    /** Whole-number percentage for direct UI display. */
    val percent: Int get() = (progress * 100f).toInt().coerceIn(0, 100)

    /** True when the row is stuck and needs user intervention (warning/error state + messages). */
    val needsAttention: Boolean
        get() = status == ArrDownloadStatus.WARNING ||
            status == ArrDownloadStatus.FAILED ||
            messages.isNotEmpty()
}

/** A single status message from a queue resource (Sonarr v4 / Radarr v3). */
@Immutable
@Serializable
data class ArrQueueMessage(
    val title: String? = null,
    val message: String,
    val type: String? = null,
)

/**
 * A single row from the `/api/v3/calendar` endpoint. Radarr yields one per
 * movie; Sonarr yields one per airing episode. [toSeerrSearchItem] maps it into
 * the existing TMDB-keyed card model so Home renders it through [SeerrMediaCard]
 * with no new card composable.
 */
@Immutable
@Serializable
data class ArrCalendarItem(
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val title: String,
    val mediaType: ArrMediaType,
    val airDateUtc: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = true,
    val overview: String? = null,
    val posterPath: String? = null,
) {
    /**
     * Adapts to [SeerrSearchItem] so Home's calendar row reuses the existing
     * TMDB-image rendering. Fields absent from *arr (voteAverage, genreIds)
     * are left null — [SeerrMediaCard] already handles nulls gracefully.
     */
    fun toSeerrSearchItem(): SeerrSearchItem = SeerrSearchItem(
        // tmdbId is the natural key when present. When absent (common for
        // Sonarr episodes whose series has no tvdb mapping), synthesize a
        // stable non-zero id from the media kind + tvdbId + title so multiple
        // id-less rows don't collapse onto id = 0 (which would crash Compose
        // LazyRow items(key=...) with a duplicate key). The negative range
        // keeps synthetic ids out of the TMDB id space.
        id = tmdbId ?: syntheticId(),
        mediaType = if (mediaType == ArrMediaType.MOVIE) "movie" else "tv",
        // SeerrSearchItem exposes both title/name; populate the one matching mediaType
        // so displayName picks the right field.
        title = if (mediaType == ArrMediaType.MOVIE) title else null,
        name = if (mediaType == ArrMediaType.SERIES) title else null,
        overview = overview,
        posterPath = posterPath?.let { if (it.startsWith("/")) it else "/$it" },
        releaseDate = if (mediaType == ArrMediaType.MOVIE) airDateUtc else null,
        firstAirDate = if (mediaType == ArrMediaType.SERIES) airDateUtc else null,
    )

    /**
     * Stable synthetic id for rows with no tmdbId. tvdbId alone may collide
     * (multiple episodes share one series tvdbId), and `tvdbId ?: 0` collapses
     * every id-less row onto 0. Hashing kind + tvdbId + title gives a stable,
     * distinct value; mapping off zero avoids the all-zero collision.
     */
    private fun syntheticId(): Int {
        val raw = "$mediaType|$tvdbId|$title".hashCode()
        return if (raw == 0) -1 else if (raw > 0) -raw else raw
    }
}

/**
 * History entry from `/api/v3/history` — used to surface "recently grabbed"
 * status (grabbed but not yet imported). Currently a thin carrier; kept in the
 * model so the repository can return a typed list rather than raw DTOs.
 */
@Immutable
@Serializable
data class ArrHistoryItem(
    val historyId: Int,
    val eventType: String,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val title: String,
    val dateUtc: String? = null,
    val data: Map<String, String> = emptyMap(),
)

/**
 * One row from `/api/v3/blocklist` (both Radarr + Sonarr). Blocklist entries
 * are releases that were rejected (manual delete-with-blocklist, or automatic
 * failure). Removing one re-enables searching for that release again.
 */
@Immutable
@Serializable
data class ArrBlocklistItem(
    val id: Int,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val title: String,
    val dateUtc: String? = null,
    val protocol: String? = null,
    val indexer: String? = null,
    val message: String? = null,
    /** Owning server id (see [ArrQueueItem.serverId]). */
    val serverId: String = "",
    val serverKind: ArrServiceKind = ArrServiceKind.RADARR,
)

/**
 * Identifies a command queued/executed against the *arr `/api/v3/command`
 * endpoint. The *arr command runner processes these asynchronously; [status]
 * reflects the latest known state (queued / started / completed / failed).
 */
@Immutable
@Serializable
data class ArrCommand(
    val id: Int,
    val name: String,
    val status: String,
    val message: String? = null,
    val dateUtc: String? = null,
) {
    val isCompleted: Boolean get() = status.equals("completed", ignoreCase = true)
    val isFailed: Boolean get() = status.equals("failed", ignoreCase = true)
}

/**
 * One row from `/api/v3/wanted/missing` (Sonarr) or `/api/v3/wanted/missing`
 * (Radarr) — episodes/movies that are monitored but have no file. Surfaced as
 * "Missing" in the *arr UI; JellyPlay can offer a "Search again" action.
 */
@Immutable
@Serializable
data class ArrWantedItem(
    val id: Int,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val title: String,
    val airDateUtc: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = true,
    val overview: String? = null,
    val posterPath: String? = null,
    val mediaType: ArrMediaType = ArrMediaType.MOVIE,
)

/**
 * Options for removing a queue item, mirroring the query params the *arr
 * `DELETE /queue/{id}` and `DELETE /queue/bulk` endpoints accept.
 *
 * - [removeFromClient]: also delete the download from the download client
 *   (qBittorrent / SABnzbd / etc.). When false, only the *arr queue row is
 *   removed and the download keeps running in the client.
 * - [blocklist]: add the release to the blocklist so *arr won't grab it again.
 * - [skipRedownload]: when true, *arr won't immediately search for a
 *   replacement. Default false — matches the *arr web UI default behaviour
 *   where removing + blocklisting triggers a fresh search.
 */
@Immutable
data class ArrQueueDeleteOptions(
    val removeFromClient: Boolean = true,
    val blocklist: Boolean = false,
    val skipRedownload: Boolean = false,
)

/**
 * Known asynchronous commands both *arr services accept via
 * `POST /api/v3/command`. The [serialName] is the exact value the *arr command
 * runner expects in the `name` field of the POST body.
 */
@Immutable
enum class ArrCommandName(val serialName: String) {
    /** Global "search all monitored missing movies" trigger; ignores movieId. */
    SEARCH_MOVIES("MoviesSearch"),
    /** Single-movie search; keys off the Radarr internal `movieId` (not tmdbId). */
    SEARCH_MOVIE("SearchMovie"),
    REFRESH_MOVIE("RefreshMovie"),
    RESCAN_MOVIE("RescanMovie"),
    SEARCH_SERIES("SeriesSearch"),
    SEARCH_EPISODES("EpisodeSearch"),
    REFRESH_SERIES("RefreshSeries"),
    RESCAN_SERIES("RescanSeries"),
    RSS_SYNC("RssSync"),
    MISSING_SEARCH("MissingMoviesSearch"),
    MISSING_EPISODES("MissingEpisodesSearch"),
    CUTTING_OFF_SEARCH("CuttingOffSeriesSearch"),
}

/**
 * Which step of a delete & re-download flow failed (if any). Surfaced in
 * [ArrRedownloadResult] so the UI can tell the user precisely what happened
 * ("file deleted, but Sonarr search failed: ...") rather than a blanket error.
 */
@Immutable
enum class ArrRedownloadStep {
    /** Re-marking the item monitored in *arr failed. */
    MONITOR,
    /** Queueing the search command in *arr failed. */
    SEARCH,
}

/**
 * Outcome of a "delete & re-download" flow's *arr half (monitor + search). The
 * Jellyfin file delete is handled separately by `MediaRepository.deleteMediaItem`;
 * this result captures only the *arr management steps.
 *
 * - [monitored]: true when the monitor PUT succeeded on at least one server.
 * - [searchStarted]: true when at least one search command was queued.
 * - [commands]: the queued [ArrCommand]s (one per matching server).
 * - [failureStep] / [errorMessage]: set when a step failed across all servers,
 *   so the UI can show a partial-failure message. Null when everything
 *   succeeded.
 *
 * The flow is best-effort across servers: a monitor failure on one server does
 * not abort the search on others, mirroring [com.raulshma.jellyplay.core.data.repository.ArrRepository.searchForTmdb]'s
 * fan-out-with-swallowed-failures contract.
 */
@Immutable
data class ArrRedownloadResult(
    val monitored: Boolean = false,
    val searchStarted: Boolean = false,
    val commands: List<ArrCommand> = emptyList(),
    val failureStep: ArrRedownloadStep? = null,
    val errorMessage: String? = null,
)

/**
 * Lightweight display-oriented summary written into RequestsUiState so the
 * bottom sheet does not need to carry the full [ArrQueueItem] (which exposes
 * raw client status strings used nowhere else in the UI).
 */
@Immutable
data class ArrDownloadSummary(
    val status: ArrDownloadStatus,
    val percent: Int,
    val sizeLeft: Long?,
    val timeLeft: String?,
)
