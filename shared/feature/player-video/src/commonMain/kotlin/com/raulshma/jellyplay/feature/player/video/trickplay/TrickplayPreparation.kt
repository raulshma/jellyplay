package com.raulshma.jellyplay.feature.player.video.trickplay

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.TrickplayInfo
import java.io.File

/**
 * Owns the trickplay three-way selection for the session load spine (formerly
 * ~45 inline lines in the ViewModel's `initializeTrickplayForItem`):
 *
 *  1. The resolved [MediaSource] carries the server manifest → stream tiles
 *     from the server, or cache them next to the download when the item plays
 *     from a local download.
 *  2. No source manifest, but the download bundles local trickplay
 *     (`meta.json` + sprite sheets) → serve fully offline.
 *  3. No source manifest, no local bundle → fetch the live server manifest
 *     and cache fetched tiles into the download's trickplay dir, so the next
 *     offline session resolves through arm 2.
 *
 * [prepare] returns the [TrickplayInfo] the [controller] was initialized
 * with (null when no arm produced one), so the caller surfaces exactly one
 * uiState write per successful arm. The arms are mutually exclusive, so the
 * former three inline writes collapse into that single conditional write
 * with the same observable outcome.
 *
 * **Directory derivation** — the three former derivations disagreed; this
 * class is now their single home:
 *  - The cache arms (1 and 3) derive the download's trickplay dir once,
 *    un-scoped: `<download file's parent>/trickplay`
 *    ([downloadTrickplayDir]) — the exact path the former inline VM code
 *    used for both cache dispatches.
 *  - The local-bundle arm (2) keeps
 *    [OfflineTrickplayHelper.getLocalTrickplayDir]'s scoped-aware resolution
 *    (`trickplay_<itemId>` preferred, legacy un-scoped fallback), and its
 *    read ([OfflineTrickplayHelper.loadLocalTrickplayInfo]) uses the same
 *    resolution. That IS the former effective behavior: the download
 *    pipeline ([com.raulshma.jellyplay.core.data.repository.DownloadArtifacts
 *    .trickplayDir]) writes the item-scoped dir for every modern download,
 *    so read and dispatch must agree on it. The un-scoped path remains the
 *    legacy fallback and the target arm 3 caches into (which arm 2 then
 *    finds via that same fallback on the next session).
 */
internal class TrickplayPreparation(
    private val controller: TrickplayController,
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    private val mediaRepository: MediaRepository,
) {

    /**
     * Resolves and initializes trickplay for [itemId]. Exactly one arm runs;
     * returns the manifest the [controller] was initialized with, or null
     * when trickplay is unavailable (the caller then writes no uiState).
     */
    suspend fun prepare(itemId: String, source: MediaSource?): TrickplayInfo? {
        // Arm 1 — the resolved source already carries the server manifest.
        source?.trickplayInfo?.let { info ->
            val downloadPath = offlinePlaybackFacade.getDownloadPath(itemId)
            if (downloadPath != null) {
                controller.initializeWithCache(itemId, info, downloadTrickplayDir(downloadPath))
            } else {
                controller.initialize(itemId, info)
            }
            return info
        }

        // Arms 2/3 only exist for offline playback; online without a source
        // manifest stays manifest-less.
        val downloadPath = offlinePlaybackFacade.getDownloadPath(itemId) ?: return null

        // Arm 2 — trickplay bundled with the download (the download pipeline
        // writes the item-scoped dir; see the class KDoc).
        val localInfo = OfflineTrickplayHelper.loadLocalTrickplayInfo(downloadPath, itemId)
        if (localInfo != null) {
            val cacheDir = OfflineTrickplayHelper.getLocalTrickplayDir(downloadPath, itemId)
                ?: return null
            controller.initializeLocal(itemId, localInfo, cacheDir)
            return localInfo
        }

        // Arm 3 — nothing bundled at download time (the detached trickplay
        // fetch failed or the server had none). Fetch the server's manifest
        // and cache fetched tiles into the download's trickplay dir so the
        // next offline session reads them locally via arm 2.
        val serverInfo = mediaRepository.getMediaDetail(itemId)
            .getOrNull()
            ?.mediaSources
            ?.firstOrNull()
            ?.trickplayInfo
            ?: return null
        val cacheDir = downloadTrickplayDir(downloadPath)
        cacheDir.mkdirs()
        controller.initializeWithCache(itemId, serverInfo, cacheDir)
        return serverInfo
    }

    /** The un-scoped download trickplay dir: `<download file's parent>/trickplay`. */
    private fun downloadTrickplayDir(downloadPath: String): File =
        File(File(downloadPath).parentFile, "trickplay")
}
