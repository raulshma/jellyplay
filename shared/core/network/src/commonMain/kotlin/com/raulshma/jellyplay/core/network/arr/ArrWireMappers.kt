package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueMessage
import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem

/**
 * Wire→model mappers for the Radarr/Sonarr clients over the consolidated
 * [ArrWireDto] shapes — the single source of truth both the [ArrV3Client]
 * engine and commonTest use. Same names, same fallbacks, same computation
 * order the former per-impl private mappers carried (they were verbatim
 * twins of these); preserve that when touching.
 */

// ── Radarr ──────────────────────────────────────────────────────────────────

/** `RadarrApiClientImpl.RadarrQueueResource.toModel`. */
internal fun RadarrQueueResource.toArrQueueItem(): ArrQueueItem {
    val sizeBytes = size?.toLong()
    val sizeLeft = sizeleft?.toLong()
    // progress = (size - sizeleft) / size, guarded against zero / null.
    val progress = if (size != null && size > 0.0 && sizeleft != null) {
        ((size - sizeleft) / size).toFloat().coerceIn(0f, 1f)
    } else 0f
    return ArrQueueItem(
        queueId = id,
        downloadId = downloadId,
        tmdbId = movie?.tmdbId,
        title = movie?.title ?: "Unknown",
        status = ArrDownloadStatus.fromApi(status, trackedDownloadStatus, trackedDownloadState),
        trackedDownloadStatus = trackedDownloadStatus,
        trackedDownloadState = trackedDownloadState,
        progress = progress,
        sizeBytes = sizeBytes,
        sizeLeft = sizeLeft,
        timeLeft = timeleft,
        protocol = protocol,
        downloadClient = downloadClient,
        indexer = indexer,
        outputPath = outputPath,
        quality = quality?.name,
        languages = languages.mapNotNull { it.name }.filter { it.isNotBlank() },
        customFormats = customFormats.mapNotNull { it.name }.filter { it.isNotBlank() },
        messages = statusMessages.flatMap { sm ->
            sm.messages.map { msg -> ArrQueueMessage(title = sm.title, message = msg) }
        },
        arrMovieId = movie?.id,
    )
}

/**
 * `RadarrApiClientImpl.RadarrMovieResource.toCalendarItem`: calendar rows
 * carry all three release dates; pick the most relevant for "coming soon"
 * ordering (digital > physical > cinematic).
 */
internal fun RadarrMovieResource.toCalendarItem(): ArrCalendarItem {
    val airDate = digitalRelease ?: physicalRelease ?: inCinemas
    return ArrCalendarItem(
        tmdbId = tmdbId,
        title = title,
        mediaType = ArrMediaType.MOVIE,
        airDateUtc = airDate,
        hasFile = hasFile,
        monitored = monitored,
        overview = overview,
        // Prefer remoteUrl (absolute); fall back to url, which behind a
        // reverse proxy is often the only field populated (relative path).
        posterPath = images.firstOrNull { it.coverType == "poster" }?.posterPreference(),
    )
}

/** `RadarrApiClientImpl.RadarrBlocklistRecord.toModel`. */
internal fun RadarrBlocklistRecord.toArrBlocklistItem(): ArrBlocklistItem = ArrBlocklistItem(
    id = id,
    tmdbId = movie?.tmdbId,
    title = movie?.title ?: "Unknown",
    dateUtc = date,
    protocol = protocol,
    indexer = indexer,
    message = message,
)

/** `RadarrApiClientImpl.RadarrMovieResource.toWantedItem`. */
internal fun RadarrMovieResource.toArrWantedItem(): ArrWantedItem = ArrWantedItem(
    id = id,
    tmdbId = tmdbId,
    title = title,
    airDateUtc = digitalRelease ?: physicalRelease ?: inCinemas,
    hasFile = hasFile,
    monitored = monitored,
    overview = overview,
    posterPath = images.firstOrNull { it.coverType == "poster" }?.posterPreference(),
    mediaType = ArrMediaType.MOVIE,
)

/** `RadarrApiClientImpl.RadarrCommandResource.toModel` — shared `ArrCommandResource` shape. */
internal fun ArrCommandResource.toArrCommand(): ArrCommand = ArrCommand(
    id = id,
    name = name,
    status = status,
    message = message,
    dateUtc = queued ?: started ?: ended,
)

/** `RadarrApiClientImpl.RadarrHistoryRecord.toModel`. */
internal fun RadarrHistoryRecord.toArrHistoryItem(): ArrHistoryItem = ArrHistoryItem(
    historyId = id,
    eventType = eventType ?: "",
    tmdbId = movie?.tmdbId,
    title = movie?.title ?: "Unknown",
    dateUtc = date,
    data = data,
)

/**
 * The `/release` row mapper — SHARED by both services: the wire row shape is
 * field-identical across Radarr and Sonarr (one declaration decodes both, the
 * per-service extras ride along as defaults), so the wire→model step is one
 * twin like the decode. Same fallbacks as the queue mappers' list walks
 * (`mapNotNull { it.name }`, blank-filtered).
 */
internal fun ArrReleaseResource.toArrRelease(): ArrRelease = ArrRelease(
    guid = guid,
    indexerId = indexerId,
    indexer = indexer,
    title = title,
    quality = quality?.name,
    qualityId = quality?.quality?.id,
    languages = languages.mapNotNull { it.name }.filter { it.isNotBlank() },
    size = size?.toLong(),
    ageHours = ageHours,
    seeders = seeders,
    leechers = leechers,
    protocol = protocol,
    releaseGroup = releaseGroup,
    customFormats = customFormats.mapNotNull { it.name }.filter { it.isNotBlank() },
    customFormatScore = customFormatScore,
    approved = approved,
    temporarilyRejected = temporarilyRejected,
    rejections = rejections,
    publishDate = publishDate,
    downloadUrl = downloadUrl,
    magnetUrl = magnetUrl,
    infoUrl = infoUrl,
    edition = edition,
    fullSeason = fullSeason,
    seasonNumber = seasonNumber,
    episodeNumbers = episodeNumbers,
    mappedEpisodeInfo = mappedEpisodeInfo,
    movieTitles = movieTitles,
)

// ── Sonarr ──────────────────────────────────────────────────────────────────

/** `SonarrApiClientImpl.SonarrQueueResource.toModel`. */
internal fun SonarrQueueResource.toArrQueueItem(): ArrQueueItem {
    val sizeBytes = size?.toLong()
    val sizeLeft = sizeleft?.toLong()
    val progress = if (size != null && size > 0.0 && sizeleft != null) {
        ((size - sizeleft) / size).toFloat().coerceIn(0f, 1f)
    } else 0f
    return ArrQueueItem(
        queueId = id,
        downloadId = downloadId,
        tvdbId = series?.tvdbId,
        title = buildString {
            series?.title?.let { append(it) }
            episode?.let { ep ->
                if (isNotEmpty()) append(" - ")
                if (ep.title.isNotBlank()) append(ep.title)
            }
            if (isEmpty()) append("Unknown")
        },
        status = ArrDownloadStatus.fromApi(status, trackedDownloadStatus, trackedDownloadState),
        trackedDownloadStatus = trackedDownloadStatus,
        trackedDownloadState = trackedDownloadState,
        progress = progress,
        sizeBytes = sizeBytes,
        sizeLeft = sizeLeft,
        timeLeft = timeleft,
        protocol = protocol,
        downloadClient = downloadClient,
        indexer = indexer,
        outputPath = outputPath,
        quality = quality?.name,
        languages = languages.mapNotNull { it.name }.filter { it.isNotBlank() },
        customFormats = customFormats.mapNotNull { it.name }.filter { it.isNotBlank() },
        messages = statusMessages.flatMap { sm ->
            sm.messages.map { msg -> ArrQueueMessage(title = sm.title, message = msg) }
        },
        arrSeriesId = series?.id,
        arrEpisodeId = episode?.id,
    )
}

/** `SonarrApiClientImpl.SonarrEpisodeResource.toCalendarItem`. */
internal fun SonarrEpisodeResource.toCalendarItem(): ArrCalendarItem {
    val series = series
    return ArrCalendarItem(
        tvdbId = series?.tvdbId,
        title = series?.title ?: title.ifBlank { "Unknown" },
        mediaType = ArrMediaType.SERIES,
        airDateUtc = airDateUtc,
        hasFile = hasFile,
        monitored = series?.monitored ?: true,
        overview = overview,
        // Prefer remoteUrl (absolute); fall back to url (relative path),
        // which behind a reverse proxy is often the only field populated.
        posterPath = series?.images?.firstOrNull { it.coverType == "poster" }?.posterPreference(),
    )
}

/** `SonarrApiClientImpl.SonarrHistoryRecord.toModel`. */
internal fun SonarrHistoryRecord.toArrHistoryItem(): ArrHistoryItem = ArrHistoryItem(
    historyId = id,
    eventType = eventType ?: "",
    tvdbId = series?.tvdbId,
    title = series?.title ?: "Unknown",
    dateUtc = date,
    data = data,
)

/** `SonarrApiClientImpl.SonarrBlocklistRecord.toModel`. */
internal fun SonarrBlocklistRecord.toArrBlocklistItem(): ArrBlocklistItem = ArrBlocklistItem(
    id = id,
    tvdbId = series?.tvdbId,
    title = series?.title ?: "Unknown",
    dateUtc = date,
    protocol = protocol,
    indexer = indexer,
    message = message,
)

/**
 * `SonarrApiClientImpl.SonarrWantedRecord.toWantedItem` — the wanted row IS a
 * [SonarrEpisodeResource] (field-identical wire), so the mapper lives on the
 * shared episode row.
 */
internal fun SonarrEpisodeResource.toArrWantedItem(): ArrWantedItem = ArrWantedItem(
    id = id,
    tvdbId = series?.tvdbId,
    title = series?.title ?: title.ifBlank { "Unknown" },
    airDateUtc = airDateUtc,
    hasFile = hasFile,
    monitored = true,
    overview = overview,
    mediaType = ArrMediaType.SERIES,
)

/**
 * `SonarrApiClientImpl.SonarrEpisodeLookupResource.toInfo` (as a free
 * function so the DTO stays a plain @Serializable value).
 */
internal fun SonarrEpisodeLookupResource.toSonarrEpisodeInfo(): SonarrEpisodeInfo =
    SonarrEpisodeInfo(
        id = id,
        episodeFileId = episodeFileId,
        hasFile = hasFile,
        monitored = monitored,
        seasonNumber = seasonNumber,
    )

/**
 * `SonarrApiClientImpl.SonarrManagedEpisodeResource.toModel` — maps to
 * [ArrSeriesEpisode] for the "Manage Series" screen.
 */
internal fun SonarrManagedEpisodeResource.toArrSeriesEpisode(): ArrSeriesEpisode = ArrSeriesEpisode(
    id = id,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    absoluteEpisodeNumber = absoluteEpisodeNumber,
    title = title.ifBlank { "Episode $episodeNumber" },
    airDateUtc = airDateUtc,
    overview = overview,
    hasFile = hasFile,
    monitored = monitored,
    episodeFileId = episodeFileId,
    fileSizeBytes = episodeFile?.size?.toLong(),
    quality = episodeFile?.quality?.name,
)

/**
 * The client-side tvdbId filter BOTH `SonarrApiClientImpl.findSeriesByTvdb`
 * and `getSeriesInfo` apply (some Sonarr versions ignore the `?tvdbId=`
 * param and return ALL series — trusting `firstOrNull()` would target the
 * wrong series). Returns the first row whose OWN `tvdbId` field matches.
 */
internal fun filterSeriesByTvdb(rows: List<SonarrSeriesResource>, tvdbId: Int): SonarrSeriesResource? =
    rows.firstOrNull { it.tvdbId == tvdbId }

/**
 * The shared `/release` grab body both Radarr's and Sonarr's
 * `grabRelease` post: the release's guid/indexer, the override arm's
 * prefilled quality (the services key the override off the numeric quality
 * id), and the shouldOverride flag. The per-service identity fields
 * ([ArrReleaseGrabBody.movieId] / [ArrReleaseGrabBody.seriesId] +
 * [ArrReleaseGrabBody.episodeIds]) are the caller's arguments.
 */
internal fun releaseGrabBody(
    release: ArrRelease,
    shouldOverride: Boolean,
    movieId: Int? = null,
    seriesId: Int? = null,
    episodeIds: List<Int> = emptyList(),
): ArrReleaseGrabBody = ArrReleaseGrabBody(
    guid = release.guid,
    indexerId = release.indexerId,
    quality = if (shouldOverride) {
        release.qualityId?.let { id -> ArrQuality(quality = ArrQualityName(release.quality, id)) }
    } else {
        null
    },
    movieId = movieId,
    seriesId = seriesId,
    episodeIds = episodeIds.takeIf { it.isNotEmpty() },
    shouldOverride = shouldOverride.takeIf { it },
)
