package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.TrickplayInfo

/**
 * The offline-artifact-write surface of a download: start the transfer, enqueue
 * the worker, and persist every sibling artifact (offline metadata row, local
 * images, trickplay, subtitles, intro/outro segments) that makes a download
 * usable offline.
 *
 * **Why this seam exists.** `DownloadDelegate` owns the per-item recipe (build a
 * [com.raulshma.jellyplay.core.data.util.DownloadRequest], start it, then
 * bundle its artifacts) and previously depended on the full 25-method
 * [DownloadRepository] interface to execute it — coupling the artifact writer to
 * lifecycle actions (pause/resume/cancel), series-batch orchestration, and
 * status queries it never calls. That god-interface coupling was the real cost
 * of the `DownloadRepositoryImpl ↔ DownloadDelegate` edge; this port narrows it
 * to exactly the 8 methods the writer needs.
 *
 * [DownloadRepository] extends this interface so every existing consumer keeps
 * compiling unchanged; only `DownloadDelegate` narrows to this type. The
 * implementation lives in [DownloadRepositoryImpl].
 *
 * **Depth**: a focused write surface behind a narrow interface. The artifact
 * bundle recipe sits one layer up in `DownloadDelegate`; the writers themselves
 * (directory policy, sanitization, network fetch, persistence) sit behind this
 * port — one place to test the write contract without dragging in lifecycle.
 */
/**
 * The start-a-download value object — the inputs the former 15-positional-
 * parameter `startDownload` signature declared THREE times (this interface,
 * the impl's override + internal forwarding twin, and the delegate call site
 * unpacking its request into named args). Building it is the caller's ONE
 * decision point; consumers read fields.
 *
 * **Series linkage is non-null only for episodes:** [seriesId]/[seasonId]/
 * [seriesName]/[seasonName]/[episodeNumber]/[seasonNumber] propagate the
 * parent series/season of an episode download so the row is linked to its
 * series — without the link, `deleteOfflineSeries` (WHERE seriesId =
 * :seriesId) finds no rows and orphans episode files + download rows behind
 * a deleted series. The single episode-context guard lives in
 * [com.raulshma.jellyplay.core.data.util.DownloadDelegate]'s builder; the
 * writer consumes the fields verbatim and must not re-guard.
 */
data class DownloadStartRequest(
    val mediaItemId: String,
    val name: String,
    val mediaType: String,
    val mediaSourceId: String?,
    val downloadUrl: String,
    val imageUrl: String?,
    val imageBlurHash: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val container: String? = null,
    /**
     * Pre-fetched `SUM(downloadedBytes)` across the download table, used to
     * skip the per-call budget query when the caller has already evaluated
     * the cap (notably `DownloadRepositoryImpl.downloadSeries`, which
     * enqueues many episodes in a batch where no bytes are actually
     * transferred yet). `null` (default) queries the DAO normally.
     */
    val precomputedCurrentBytes: Long? = null,
)

interface OfflineDownloadWriter {

    suspend fun startDownload(request: DownloadStartRequest): Result<DownloadItem>

    suspend fun saveOfflineMediaItem(
        item: MediaItem,
        imageUrl: String?,
        backdropUrl: String?,
        downloadPath: String? = null,
    )

    /**
     * Persist full metadata (overview, genres, ratings, cast, studios, …) for
     * a downloaded item from a [MediaDetail]. Prefer this over
     * [saveOfflineMediaItem] when rich metadata is available so the offline
     * detail screens can show the same information as the online ones.
     */
    suspend fun saveOfflineMediaDetail(detail: MediaDetail, imageUrl: String?, backdropUrl: String?)

    /**
     * Downloads the given item's image to a local file so it renders offline,
     * returning the absolute path, or null if the item has no such image or the
     * download failed. [fileName] is the sibling filename written under
     * [parentDir]; callers should pass a per-item name (e.g.
     * [DownloadArtifacts.posterFile]) so items sharing the flat downloads dir
     * don't overwrite each other's images.
     */
    suspend fun downloadOfflineImage(
        itemId: String,
        imageType: String,
        maxWidth: Int,
        parentDir: java.io.File,
        fileName: String,
    ): String?

    /**
     * Writes the trickplay sprite sheets + `meta.json` for [itemId] to disk.
     * Returns `true` only when the write completed; `false` signals a best-effort
     * failure (network/IO) so the caller can avoid re-seeding the freshness
     * baseline from a fetch the disk never received.
     */
    suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        downloadPath: String,
    ): Boolean

    /**
     * Downloads every external/deliverable subtitle stream in [mediaStreams] for
     * offline playback, storing them under `<video-dir>/subtitles/` alongside a
     * [com.raulshma.jellyplay.core.model.OfflineSubtitleManifest]. Failures for
     * individual streams are tolerated (best-effort) and never abort the download.
     * Returns `true` when the overall write (manifest + at least the attempted
     * streams) completed; `false` lets the caller skip baseline re-seeding so a
     * failed fetch isn't masked as "synced".
     */
    suspend fun downloadExternalSubtitles(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
        downloadPath: String,
    ): Boolean

    /**
     * Fetches media segments (intro/outro/recap/…) for [itemId] and persists them
     * to `<video-dir>/segments.json` so skip controls work for offline playback.
     * Returns `true` on a successful write, `false` on a best-effort failure so
     * the caller can retain the prior baseline instead of re-seeding from a
     * fetch whose result never reached disk.
     */
    suspend fun downloadMediaSegments(itemId: String, downloadPath: String): Boolean

    /**
     * Flags [itemId]'s freshness baseline so the next freshness check and the
     * next resync both retry the subtitle bundle. Call this whenever a subtitle
     * bundle fails; see the call site in
     * `DownloadDelegate.executeDownload` for why a failure would otherwise go
     * unnoticed, and `SyncBaselineDao.markSubtitlesPending` for the flag's
     * canonical lifecycle (immediate badge lighting, TTL interaction,
     * cleared only by a successful fetch).
     */
    suspend fun markSubtitlesPending(itemId: String)

    fun enqueueDownload(downloadId: String)
}
