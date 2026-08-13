package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * The origin of a resolved media-detail snapshot. Orthogonal to whether a
 * download is *attached* — a remote detail with a completed download is still
 * [REMOTE]; its download card comes from [DetailContext.download], not from a
 * misleading offline origin.
 *
 * There is deliberately no `CACHED` variant: this repository has a durable
 * local-download projection, but no general cached [MediaDetail] representation.
 */
@Immutable
enum class DetailOrigin {
    /** Server response is the primary detail model. A download may still be attached. */
    REMOTE,

    /** Manual or auto offline mode selected a local projection without contacting the server. */
    LOCAL_OFFLINE_MODE,

    /** Remote request failed and the provider fell back in place to a local projection. */
    LOCAL_REMOTE_FAILURE;

    /**
     * True for either local origin (offline mode or remote-failure fallback). The
     * single predicate for "is this snapshot sourced from local data?" — prefer
     * this over repeating `origin != null && origin != REMOTE` at each call site.
     */
    val isLocal: Boolean get() = this != REMOTE
}

/**
 * Whether a connection exists that may reach the server. Combines manual/auto
 * offline mode with the live [NetworkStatus]. Distinct from [DetailOrigin]:
 * a [DetailOrigin.LOCAL_REMOTE_FAILURE] snapshot can still have
 * [RemoteConnectivity.AVAILABLE] (the failure was transient), which permits a
 * one-shot retry; a forced-offline snapshot never does.
 */
@Immutable
enum class RemoteConnectivity {
    /** Server may be reachable (online, or LAN-only still permits the attempt). */
    AVAILABLE,
    /** Manual or auto offline mode forbids a server request. */
    BLOCKED,
}

/**
 * A download attached to a detail snapshot, carrying the lifecycle state the
 * download-info card and local-management actions render. The provider derives
 * [isCompletedFilePresent] from the same completed-file predicate as
 * `PlaybackSourceResolver` (status + file-on-disk), so advertising local
 * playback/deletion is a tested behavior decision rather than a DB-status guess.
 *
 * `createdAtEpochMillis` is sourced from [OfflineMediaItem.createdAt] —
 * [com.raulshma.jellyplay.core.model.DownloadItem] does not carry a creation
 * timestamp.
 */
@Immutable
data class DownloadAttachment(
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalSizeBytes: Long,
    val mediaSourceId: String?,
    val container: String?,
    val downloadPath: String?,
    val createdAtEpochMillis: Long,
    val isCompletedFilePresent: Boolean,
) {
    /** A download that has finished and whose file still exists on disk. */
    val isCompleted: Boolean get() = isCompletedFilePresent && status == DownloadStatus.COMPLETED
    /** A download in progress (or paused/queued) — not yet deletable as completed content. */
    val isInProgress: Boolean
        get() = status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.QUEUED ||
            status == DownloadStatus.PAUSED ||
            status == DownloadStatus.PENDING
}

/**
 * Aggregate counts for a local series header ("N episodes · size"), mirroring
 * the old `OfflineSeriesScreen`. Only populated for a local series origin.
 */
@Immutable
data class LocalSeriesAggregate(
    val downloadedEpisodeCount: Int,
    val totalSizeBytes: Long,
)

/**
 * Local presentation artwork, resolved from the enriched offline row *before*
 * the server-image fallback is used. Paths are absolute on-disk file paths.
 *
 * These fields are deliberately NOT on [MediaDetail] / [MediaItem]: they are a
 * storage concern surfaced as immutable presentation data so the Compose image
 * callbacks can check a local path first and then delegate to `ImageUrlProvider`
 * for a remote item id. This keeps the UI's interface stable while the storage
 * implementation changes.
 *
 * [castImages] is keyed by person id so the cast row can resolve each portrait
 * independently; an absent entry falls back to the server image url.
 */
@Immutable
data class DetailAssets(
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val castImages: Map<String, String> = emptyMap(),
    /** Per-episode on-disk primary-image paths, keyed by episode item id. */
    val episodeImages: Map<String, String> = emptyMap(),
)

/**
 * A manifest-backed external subtitle selectable for local playback. Scope is
 * intentionally narrow: the persisted [OfflineSubtitleManifest] stores
 * downloaded external subtitle entries and their original stream indexes only —
 * it carries no full [MediaSource], no audio-stream inventory, and no
 * hearing-impaired/SDH flag. The local selector must therefore not synthesize a
 * full media-source UI or promise offline audio-track selection.
 *
 * [index] is the original server stream index, which matches
 * [OfflineSubtitleEntry.index] and the key the detail selector writes.
 */
@Immutable
data class LocalSubtitleOption(
    val index: Int,
    val fileName: String,
    val displayTitle: String?,
    val language: String?,
    val isDefault: Boolean,
    val isForced: Boolean,
) {
    /**
     * Single source of truth for the row/pill label: prefers an explicit
     * display title, then a 3-letter language code, then the file name. Mirrors
     * the fallback chain the local-subtitle picker used inline.
     */
    fun displayLabel(): String =
        displayTitle?.takeIf { it.isNotBlank() }
            ?: language?.uppercase()?.take(3)
            ?: fileName
}

/**
 * Compact, derive-once capability set for a snapshot. Replaces scattered
 * `context.isOnline` checks across sections. Adds only the *source* prerequisite
 * — unrelated business rules (Seerr connection/preferences, the experimental ARR
 * flag, the tvdb + resolved-Sonarr requirement for Manage Series) stay in their
 * current owners and are ANDed with these at the call site.
 *
 * Lifecycle-action distinctions are NOT collapsed here:
 * - an in-progress download is not a completed item that can be deleted (see
 *   [DownloadAttachment.isInProgress]);
 * - resync/re-download additionally require [remoteWorkAllowed].
 */
@Immutable
data class DetailCapabilities(
    /** Remote-only discovery/admin: similar, trailers, Seerr, ARR, metadata editor,
     *  playlists, home-list controls, theme music, InstantMix, Cinema Mode intros. */
    val remoteDiscovery: Boolean,
    /** Server stream / audio / subtitle selector is available. */
    val remoteStreamSelection: Boolean,
    /** Local manifest-backed external subtitle selector is available. */
    val localSubtitleSelection: Boolean,
    /** The local file was probed successfully — read-only quality/audio badges
     *  can render from the synthesized media source. The player remains the
     *  place to switch audio offline; this only gates informational badges. */
    val localStreamInfo: Boolean,
    /** Person drill-in (`Route.PersonDetail`) is enabled. */
    val personNavigation: Boolean,
    /** Studio drill-in (`Route.StudioDetail`) is enabled — requires a persisted server id. */
    val studioNavigation: Boolean,
    /** Up-Next / smart-play target may be offered. Null target still suppresses the section. */
    val smartPlay: Boolean,
    /** Connectivity + mode permit a server request (refresh / resync / re-download). */
    val remoteWorkAllowed: Boolean,
    /** A completed, on-disk download exists — surfaces delete/freshness/resync/re-download. */
    val localDownloadManagement: Boolean,
    /** Genre/tag chips are tappable → deep-link into a filtered library section. Remote-only. */
    val tagNavigation: Boolean,
    /** A chapter list can be rendered and a chapter is playable (resume-from-position). */
    val chapters: Boolean,
)

/**
 * Source reason, attached download lifecycle state, sync state, and the
 * local-only metadata the download/sync UI renders. Distinguishes [origin] from
 * attachment: a remote detail with a completed download is [DetailOrigin.REMOTE]
 * — its download card and deletion controls come from [download].
 */
@Immutable
data class DetailContext(
    val origin: DetailOrigin,
    val connectivity: RemoteConnectivity,
    val download: DownloadAttachment?,
    val syncState: OfflineSyncState?,
    val seriesAggregate: LocalSeriesAggregate?,
)

/**
 * The resolved media-detail contract consumed by `DetailViewModel`. Owns the
 * projected [MediaDetail], its [context] and [capabilities], the local
 * presentation [assets], and the source-aware companion content.
 *
 * The provider emits incrementally: a base loaded snapshot first, then a
 * replacement snapshot as remote subordinate work or local Room flows update.
 * Seasons/episodes come from the shared `EpisodeCatalogue` (online and offline
 * alike) so the online/offline fork is not recreated one level down. Album tracks
 * for a local origin come from `OfflineRepository.getChildren`; for a remote
 * origin from the server album content.
 *
 * `collectionItems` / `relatedItems` are intentionally NOT part of the snapshot:
 * they are remote-only and remain VM-owned, capability-gated launches, so they
 * cannot create a local fork.
 */
@Immutable
data class MediaDetailSnapshot(
    val detail: MediaDetail,
    val context: DetailContext,
    val capabilities: DetailCapabilities,
    val assets: DetailAssets,
    val seasons: List<MediaItem> = emptyList(),
    val episodesBySeason: Map<String, List<MediaItem>> = emptyMap(),
    val fetchedSeasonIds: Set<String> = emptySet(),
    /**
     * Every episode across [seasons] in canonical playback order
     * (`seasonNumber`, then `episodeNumber`/`indexNumber`, then `name`) — the
     * single source of truth for smart-play resolution and playlist expansion.
     * Consumers must NOT re-derive this; it mirrors the catalogue's
     * `sortedEpisodes` so the VM no longer needs to reach into the catalogue
     * (or rebuild a local snapshot) to get the playback order.
     */
    val sortedEpisodes: List<MediaItem> = emptyList(),
    val albumTracks: List<MediaItem> = emptyList(),
    val localSubtitles: List<LocalSubtitleOption> = emptyList(),
    /**
     * Monotonic per-item id bumped whenever the *content* sections (detail,
     * seasons, episodes, album tracks, local subtitles, origin) are re-resolved
     * — i.e. on first load, on [MediaDetailProvider.refresh], on a source
     * switch, or on a reconnect retry. It does NOT bump when only the reactive
     * attachment (download progress, sync state) changes.
     *
     * Consumers use it to adopt content sections only on a real resolution
     * change, so optimistic UI mutations the consumer applied between
     * resolutions (e.g. flipping a watched badge without a server refetch) are
     * not clobbered by attachment-only re-emissions.
     */
    val contentGeneration: Long = 0L,
)
