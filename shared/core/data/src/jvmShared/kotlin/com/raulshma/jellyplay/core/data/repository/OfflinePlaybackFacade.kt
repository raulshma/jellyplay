package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaSegment

// Phase X MediaRepository cluster flip: moved verbatim from the legacy
// :core:data shim (same package/name); `@Singleton` / `@Inject` stripped
// (one framework per type — Koin's dataJvmModule constructs this single; the
// legacy DataModule bridges the remaining Hilt injectors — notably
// PlaybackSourceResolverImpl, whose ctor takes this facade — via koin().get()).

/**
 * Single face the video player surfaces use for everything that touches the
 * offline / download storage layer.
 *
 * **Why this exists.** The download row (`DownloadDao` / [DownloadRepository])
 * and the offline-media row (`OfflineMediaDao` / [OfflineRepository]) are a
 * storage-level split: a download row tracks the transfer (bytes, status,
 * download path) and an offline-media row tracks playback metadata (watched
 * state, resume position, episode/season linkage). Both are legitimate
 * concerns, but the split had climbed all the way to the view model —
 * `VideoPlayerViewModel` injected both repositories and called them directly
 * at ~7 sites, having to know that "to delete a download, call
 * `DownloadRepository`; to record progress, call `OfflineRepository`; to
 * resolve seasons, call `OfflineRepository`; to load segments, call
 * `DownloadRepository`." That routing is a storage detail the player has no
 * business carrying.
 *
 * This facade owns the routing. The player records progress, deletes
 * downloads, resolves resume positions, discovers offline seasons/episodes,
 * and fetches bundled segments through one collaborator. Each method maps to
 * one player concern; the download-vs-offline distinction stays behind the
 * seam. The two underlying repositories remain available to lifecycle code
 * (the download worker, the offline detail screens) that needs them directly.
 *
 * **Depth.** The interface is the player's offline vocabulary; the
 * implementation absorbs the two-repository split. Deleting this class would
 * re-surface the routing decision at every call site — concentrates
 * complexity, so the module is deep rather than shallow.
 */
class OfflinePlaybackFacade(
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
) {

    /**
     * Records playback progress into the offline store so downloaded items
     * render watched / resume state while offline. No-op for non-downloaded
     * items (the underlying DAO upsert is keyed on the offline-media row).
     */
    suspend fun recordProgress(
        itemId: String,
        positionTicks: Long,
        percentage: Double,
        isPlayed: Boolean,
    ) {
        offlineRepository.updatePlaybackProgress(itemId, positionTicks, percentage, isPlayed)
    }

    /** Marks [itemId] as fully watched in the offline store. */
    suspend fun recordPlayed(itemId: String) {
        offlineRepository.updatePlaybackProgress(
            itemId,
            positionTicks = null,
            percentage = 100.0,
            isPlayed = true,
        )
    }

    /**
     * Deletes the download for [itemId], if any. Returns `true` when a download
     * was found and deleted, `false` when nothing was downloaded (no-op).
     */
    suspend fun deleteDownload(itemId: String): Boolean {
        val download = downloadRepository.getDownloadByMediaItemId(itemId) ?: return false
        downloadRepository.deleteDownload(download.id)
        return true
    }

    /**
     * Resume position (ticks) for [itemId], or `0L` when the item is not a
     * completed download or has no recorded position. Used to seed the player
     * start position on offline playback.
     */
    suspend fun getResumePositionTicks(itemId: String): Long {
        val download = downloadRepository.getDownloadByMediaItemId(itemId)
        if (download?.status != com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) return 0L
        return offlineRepository.getOfflineItem(itemId)?.playbackPositionTicks
            ?.takeIf { it > 0L } ?: 0L
    }

    /** On-disk path of the completed download for [itemId], or `null`. */
    suspend fun getDownloadPath(itemId: String): String? =
        downloadRepository.getDownloadByMediaItemId(itemId)?.downloadPath

    // resolveSeasons / resolveEpisodes used to live here — the player's offline
    // episode discovery now goes through EpisodeCatalogue.loadSeriesEpisodes /
    // loadSeasonEpisodes (offline = true), which read the same OfflineRepository
    // flows. With no remaining callers, the duplicates are gone.

    /**
     * Intro/outro/recap segments bundled with [itemId]'s download, or `null`
     * when the item has no local segment cache (caller should fall back to the
     * server). Offline-first so skip controls work without a round-trip.
     */
    suspend fun loadSegments(itemId: String): List<MediaSegment>? =
        downloadRepository.loadLocalSegments(itemId)
}
