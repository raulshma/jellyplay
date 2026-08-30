package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class OfflineMediaItem(
    val id: String,
    val name: String,
    val mediaType: MediaType,
    val overview: String? = null,
    val year: Int? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val blurHashPrimary: String? = null,
    val blurHashBackdrop: String? = null,
    val genres: List<String> = emptyList(),
    val downloadPath: String? = null,
    val downloadStatus: DownloadStatus? = null,
    val downloadedBytes: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val childCount: Int = 0,
    // Playback progress. Seeded from server UserData at download
    // time and updated locally as the user watches offline. `playedPercentage` is
    // derived (0–100) and stored so it can be rendered without recomputing.
    val playbackPositionTicks: Long? = null,
    val playedPercentage: Double = 0.0,
    val isPlayed: Boolean = false,
    val isFavorite: Boolean = false,
    val lastPlayedDate: String? = null,
    // Rich metadata persisted at download time so the offline detail screens
    // mirror the online detail screen. Populated from MediaDetail; null for
    // items downloaded before these fields existed.
    val originalTitle: String? = null,
    val criticRating: Float? = null,
    val studios: List<String> = emptyList(),
    val tagline: String? = null,
    val cast: List<OfflinePersonInfo> = emptyList(),
    // Provider ids (tmdb/imdb/…) and external URLs persisted at download time
    // so the offline subtitle search can resolve a TMDB/IMDb id without a
    // server round-trip. Empty for items downloaded before these fields
    // existed (migration 43→44) — the subtitle providers then fall back to a
    // title search, matching the pre-fix behaviour.
    val providerIds: Map<String, String> = emptyMap(),
    val externalUrls: List<ExternalUrl> = emptyList(),
    // Chapter list persisted at download time so chapter markers, the player's
    // chapter picker sheet, chapter-based intro/credits segment synthesis and
    // the detail screen's chapter row all work offline. This KDoc is the
    // canonical contract; persistence sites point here instead of restating it.
    // Empty for items downloaded before this field existed (migration 50→51)
    // or with no server chapters.
    val chapters: List<ChapterInfo> = emptyList(),
    // Epoch millis the offline_media row was created (download date).
    val createdAt: Long = 0L,
)

/**
 * A person (actor/crew) persisted for offline detail screens. Stored as a
 * JSON column on [OfflineMediaItem]; decoded at read time.
 *
 * [localImagePath] is resolved at read time (not persisted in the JSON blob):
 * when a cast image has been downloaded to disk beside the item's media file,
 * [com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl] copies
 * the row with this field set to the on-disk absolute path. The offline detail
 * screen then prefers it over the remote URL, so the cast row renders without
 * network even when Coil's memory cache has been evicted (memory pressure, app
 * restart, or a row downloaded before cast-image preloading existed).
 */
@Immutable
@Serializable
data class OfflinePersonInfo(
    val id: String,
    val name: String,
    val role: String? = null,
    val type: String = "Actor",
    val imageTag: String? = null,
    val blurHash: String? = null,
    val localImagePath: String? = null,
)

/**
 * Fraction of [OfflineMediaItem.runTimeTicks] at which an item is treated as
 * watched when adapting to [MediaItem]. Matches the player's own watched
 * threshold (see `PlaybackProgressReporter`'s 95% trigger). Jellyfin's server
 * UserData keeps `playbackPositionTicks` and `played` as independent flags —
 * a near-finished item can have a resume position of 95–99% with `played =
 * false`. Without this normalization the offline episode cards would show
 * "0m left" instead of "Watched".
 *
 * Public because every surface that partitions "in progress" vs "finished"
 * (offline home Continue Watching / Next Up) must agree with the display
 * normalization above — the threshold is one fact, owned here.
 */
const val OFFLINE_WATCHED_THRESHOLD = 0.95

/**
 * Adapts an [OfflineMediaItem] into a [MediaItem] so the shared online card
 * components ([PosterCard], [WideMediaCard], [PersonItem], …) can render
 * offline content unchanged. Only fields present on the offline model are
 * populated; the rest use the [MediaItem] defaults.
 *
 * Watched-state normalization: an item whose resume position is at or above
 * [OFFLINE_WATCHED_THRESHOLD] of its runtime is reported as played with a
 * null position, so display components render the "Watched" badge rather than
 * a spurious "0m left". This is a display-only projection — the underlying
 * [OfflineMediaItem] (and its DB row) keeps the raw flags, so resume logic
 * that reads the entity directly (e.g. `resolveOfflineResumeTicks`) is
 * unaffected.
 */
fun OfflineMediaItem.toMediaItem(): MediaItem {
    val effectiveIsPlayed: Boolean
    val effectivePositionTicks: Long?
    val rt = runTimeTicks
    when {
        isPlayed -> {
            effectiveIsPlayed = true
            effectivePositionTicks = null
        }
        rt != null && rt > 0 && playbackPositionTicks != null &&
            playbackPositionTicks.toDouble() / rt.toDouble() >= OFFLINE_WATCHED_THRESHOLD -> {
            effectiveIsPlayed = true
            effectivePositionTicks = null
        }
        else -> {
            effectiveIsPlayed = isPlayed
            effectivePositionTicks = playbackPositionTicks
        }
    }
    return MediaItem(
        id = id,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        mediaType = mediaType,
        year = year,
        communityRating = communityRating,
        officialRating = officialRating,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = effectivePositionTicks,
        isPlayed = effectiveIsPlayed,
        isFavorite = isFavorite,
        premiereDate = null,
        genres = genres,
        studios = studios,
        parentId = null,
        seriesId = seriesId,
        seasonId = seasonId,
        seriesName = seriesName,
        seasonName = seasonName,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        childCount = if (childCount > 0) childCount else null,
        lastPlayedDate = lastPlayedDate,
        blurHashes = ImageBlurHashes(primary = blurHashPrimary, backdrop = blurHashBackdrop),
    )
}

/**
 * Adapts an [OfflinePersonInfo] into the online [PersonInfo] shape so the shared
 * cast row can render offline cast unchanged. Maps `imageTag` to
 * `primaryImageTag`; `localImagePath` is deliberately NOT carried here — it is
 * storage-specific and is surfaced via [DetailAssets.castImages] by the provider
 * so the UI resolves a local portrait before falling back to the server image.
 */
fun OfflinePersonInfo.toPersonInfo(): PersonInfo = PersonInfo(
    id = id,
    name = name,
    role = role,
    type = type,
    primaryImageTag = imageTag,
)

/**
 * Adapts an [OfflineMediaItem] into a [MediaDetail] so the unified detail screen
 * can render a local projection through the same contract as a remote detail.
 *
 * The mapping is intentionally lossy: [OfflineMediaItem] carries a subset of
 * [MediaDetail], so every field without an offline source defaults. Reuses
 * [toMediaItem] for the embedded item (preserving the watched-state
 * normalization). Named losses:
 * - **Studios** are names only ([StudioInfo] requires a server id). They are
 *   left on [MediaItem.studios] (populated by [toMediaItem]) and rendered as
 *   non-navigable labels; [MediaDetail.studios] is left empty so no server id is
 *   ever invented or navigated.
 * - No metadata-lock info (`lockData = false`, `lockedFields` empty), and the
 *   server-only collections (`tagItems`, `productionLocations`, `airDays`,
 *   `airTime`, `displayOrder`, `dateCreated`, `imageInfos`, `relatedItems`,
 *   `sortName`, `customRating`, `logoImageTag`, `overviewImageTag`,
 *   `mediaSources`) map to empty/null. Chapters are NOT a loss — they are
 *   persisted on the offline row and carried through.
 * - `tagline` (scalar) becomes a one-element `taglines` list. The unified body
 *   must keep the italic rendering the offline screen used.
 *
 * Local artwork and cast file paths are not fields on [MediaDetail]; the provider
 * carries them in [DetailAssets].
 */
fun OfflineMediaItem.toMediaDetail(): MediaDetail = MediaDetail(
    item = toMediaItem(),
    criticRating = criticRating,
    taglines = tagline?.let { listOf(it) } ?: emptyList(),
    people = cast.map { it.toPersonInfo() },
    providerIds = providerIds,
    externalUrls = externalUrls,
    chapters = chapters,
)
