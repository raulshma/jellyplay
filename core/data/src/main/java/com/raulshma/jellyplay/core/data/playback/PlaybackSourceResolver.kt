package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem

/**
 * The resolved answer to "what plays this item": a completed download whose
 * file is still on disk ([Local]), or a server stream URL ([Stream]).
 *
 * Consolidates the download-vs-stream fork that was previously inlined across
 * five call-sites (`MainViewModel.buildExternalPlayerLaunch`,
 * `feature/player/video/PlaybackSource.Auto.resolve`, and the
 * `AudioPlaybackManager` / `AudioCrossfader` / `AudioLibraryBrowser` trio).
 * Each site re-derived the same predicate — *completed download with an
 * existing file → local, else stream* — and each re-built the matching URL
 * (`Uri.fromFile(...)` for local, `PlaybackRepository.getStreamUrl` for
 * streaming). This sealed type carries the resolved URI/title/metadata so the
 * fork lives once, behind [PlaybackSourceResolver].
 *
 * The module works **only in ticks**; callers convert to ms (`ticks / 10_000`)
 * at the boundary (`MainViewModel`, `PlayerSessionManager`). The resolver never
 * divides.
 *
 * `Local.offlineItem` carries the rich offline metadata (seriesName, season /
 * episode numbers) so the audio trio and player can keep their title /
 * subtitle fallbacks without a second lookup.
 */
sealed interface ResolvedPlaybackSource {
    val itemId: String
    val title: String

    /**
     * A completed on-disk download. [uri] is `Uri.fromFile(file).toString()`
     * (a `file://` URI) — the form every caller built inline before this
     * consolidation. [filePath] is the raw filesystem path.
     */
    data class Local(
        override val itemId: String,
        val filePath: String,
        val uri: String,
        override val title: String,
        val download: DownloadItem,
        val offlineItem: OfflineMediaItem? = null,
    ) : ResolvedPlaybackSource

    /**
     * A server stream URL resolved via
     * `PlaybackRepository.getStreamUrl(itemId, mediaSourceId, startTimeTicks)`.
     * [mediaSourceId] is the chosen source (explicit match else first).
     */
    data class Stream(
        override val itemId: String,
        val url: String,
        override val title: String,
        val mediaSourceId: String?,
    ) : ResolvedPlaybackSource
}

/**
 * The deep "what plays this item" module. One owner for the
 * download-vs-stream fork and the completed-download predicate.
 *
 * ## Why this exists
 *
 * Five call-sites duplicated the same decision: probe the downloads store,
 * check `status == DownloadStatus.COMPLETED` AND `File(downloadPath).exists()`,
 * and on success play `Uri.fromFile(file)`, else fetch `getMediaDetail` and
 * stream via `getStreamUrl`. The disk-race handling (a COMPLETED row whose
 * file vanished) was applied inconsistently — `MainViewModel` silently fell
 * back online, `PlayerSessionManager.loadOffline` surfaced a hard error. This
 * resolver owns the predicate once: callers that want the MainViewModel
 * fallback semantics call [resolvePlaybackSource] (auto-falls through to
 * Stream); callers that require a hard local result call [resolveLocalSource]
 * (null when nothing usable exists).
 *
 * ## What stays at the callers
 *
 * - **Ticks↔ms conversion** — never done here (see [ResolvedPlaybackSource]).
 * - **Episode ordering / next-up / adjacency** — owned by
 *   `EpisodeCatalogue` + `EpisodeCatalogueSnapshot.sortedEpisodes`; this
 *   module does not touch series/season windows.
 * - **Smart-play label decision** — `SmartPlayResolver` stays pure in
 *   `feature:details`.
 * - **Download URL building** — `DownloadDelegate.prepareDownloadRequest`
 *   applies `maxBitrate`; not a playback-source concern.
 * - **`OfflinePlaybackFacade` public API** — [resolveStartPositionTicks]
 *   delegates to `OfflinePlaybackFacade.getResumePositionTicks` so the storage
 *   query stays behind its existing seam.
 *
 * ## Disk staleness
 *
 * [resolveUsableDownload] is the single owner of the "is this a usable
 * on-disk download" check. It always `File.exists()`-checks — a COMPLETED row
 * whose file was deleted returns null, so [resolvePlaybackSource] transparently
 * falls through to Stream (preserving `MainViewModel`'s silent-online-fallback
 * behaviour). `PlayerSessionManager.loadOffline` still surfaces its
 * `player_video_error_offline_file_missing` path because the `Offline`
 * `PlaybackSource` variant is only produced when the predicate already passed
 * — the resolver's disk-check is the risk mitigation documented there.
 */
interface PlaybackSourceResolver {

    /**
     * The deep call: completed download with file on disk → [ResolvedPlaybackSource.Local];
     * otherwise `getMediaDetail` → [ResolvedPlaybackSource.Stream]. Returns
     * `null` when the server detail fetch fails (no playable source).
     *
     * This is the auto-detect path `MainViewModel.buildExternalPlayerLaunch`
     * and the audio crossfade/browser trio consume. It **silently** falls back
     * to streaming when a completed download's file is missing — matching the
     * historical MainViewModel behaviour. Callers that need a hard local
     * result should use [resolveLocalSource].
     */
    suspend fun resolvePlaybackSource(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): ResolvedPlaybackSource?

    /**
     * THE shared "is this a completed on-disk download" predicate — the single
     * owner. Returns the [DownloadItem] only when `status == COMPLETED` AND
     * `File(downloadPath).exists()`; `null` otherwise (no row, not completed,
     * or file deleted). Consumed by `PlayerSessionManager.loadMedia` (replacing
     * its raw `downloadRepository.getDownloadByMediaItemId` probe) and by
     * [resolveLocalSource] / [resolvePlaybackSource].
     */
    suspend fun resolveUsableDownload(itemId: String): DownloadItem?

    /**
     * Local-only lookup — no `getMediaDetail` call. Returns a
     * [ResolvedPlaybackSource.Local] when a usable download exists, else `null`.
     *
     * The audio server-failed fallback path
     * (`AudioPlaybackManager` after `getMediaDetail` failed, the queue-only
     * local play) must NOT trigger another server round-trip — it keeps the
     * COMPLETED classification even when detail failed. This method serves
     * that exact case.
     */
    suspend fun resolveLocalSource(itemId: String): ResolvedPlaybackSource.Local?

    /**
     * Start-position resolution: [explicitTicks] > 0 wins; else, for a completed
     * download, the offline store's last-known position; else `0L`. Delegates
     * the storage query to `OfflinePlaybackFacade.getResumePositionTicks` so the
     * resume-position seam stays intact. Replaces `VideoPlayerViewModel.resolveOfflineResumeTicks`.
     */
    suspend fun resolveStartPositionTicks(itemId: String, explicitTicks: Long): Long
}
