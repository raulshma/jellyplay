package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * Download lifecycle, status queries, and series-batch orchestration.
 *
 * The offline-artifact-write surface (start, enqueue, saveOfflineMediaItem,
 * saveOfflineMediaDetail, downloadOfflineImage, downloadTrickplayData,
 * downloadExternalSubtitles, downloadMediaSegments) is
 * inherited from [OfflineDownloadWriter] — that narrower port is what
 * [com.raulshma.jellyplay.core.data.util.DownloadDelegate] depends on, so the
 * per-item recipe couples only to writes, not to pause/resume/cancel or
 * series-batch logic.
 */
interface DownloadRepository : OfflineDownloadWriter {

    fun getAllDownloads(): Flow<List<DownloadItem>>

    /**
     * One page of completed audio (`MUSIC`/`AUDIO`) downloads, newest first —
     * the media-library DOWNLOADS browse page's window. Same filter and order
     * the caller previously applied over [getAllDownloads], resolved in one
     * query instead of a full-table fetch per page request.
     */
    suspend fun getCompletedAudioDownloads(limit: Int, offset: Int): List<DownloadItem>

    fun getDownloadByMediaItemIdFlow(mediaItemId: String): Flow<DownloadItem?>

    fun getActiveDownloadCount(): Flow<Int>

    suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadItem?

    /**
     * The display name of a download row, or null if it no longer exists.
     * Thin read accessor for callers (e.g. the notification action receiver)
     * that need only the name and shouldn't depend on the DAO layer directly.
     */
    suspend fun getDownloadName(id: String): String?

    suspend fun cancelDownload(id: String): Result<Unit>

    suspend fun pauseDownload(id: String): Result<Unit>

    suspend fun resumeDownload(id: String): Result<Unit>

    suspend fun deleteDownload(id: String): Result<Unit>

    suspend fun retryDownload(id: String): Result<Unit>

    suspend fun getTotalDownloadedBytes(): Long

    suspend fun downloadSeries(
        seriesId: String,
        episodeIds: Map<String, List<String>>? = null,
    ): Result<List<String>>

    suspend fun getDownloadedEpisodeIdsForSeries(seriesId: String): Set<String>

    /**
     * All downloaded episode ids grouped by their parent series, fetched in a
     * single 2-column query. Intended for callers (e.g. the periodic
     * auto-download worker) that need the ids for *every* series at once —
     * preferable to calling [getDownloadedEpisodeIdsForSeries] per series,
     * which issues N full-row queries (N+1) while consuming only `mediaItemId`.
     */
    suspend fun getDownloadedEpisodeIdsBySeries(): Map<String, Set<String>>

    suspend fun getDownloadedSeriesIds(): List<String>

    /** Returns the locally-cached subtitle manifest for a downloaded item, if any. */
    suspend fun loadLocalSubtitleManifest(downloadPath: String, itemId: String? = null): com.raulshma.jellyplay.core.model.OfflineSubtitleManifest?

    /** Returns locally-cached media segments for a downloaded item, if any. */
    suspend fun loadLocalSegments(itemId: String): List<com.raulshma.jellyplay.core.model.MediaSegment>?

    /**
     * Enumerates every on-disk file belonging to a downloaded item — the media
     * file plus all sidecar artifacts (subtitles, trickplay, segments, images)
     * — each with its absolute path and actual on-disk byte size. Sidecar sizes
     * are not persisted, so they are read live from the filesystem. Returns
     * [DownloadFileInventory.EMPTY] when the item has no resolvable download path.
     */
    suspend fun getDownloadFileInventory(itemId: String): DownloadFileInventory

    /**
     * Auto-resume pass run by the network-reconnect path. Resumes interrupted
     * downloads — `PAUSED` rows whose `pausedReason` is `NETWORK` (an in-flight
     * transfer interrupted by a connectivity drop) and `FAILED` rows — by
     * resetting each to `PENDING` and re-enqueueing its worker.
     *
     * Skips: user-paused rows (`pausedReason == USER`) — those stay paused until
     * the user resumes — and rows past the auto-retry budget (`retryCount >=
     * MAX_AUTO_RETRY`), which are left FAILED for a manual retry so a
     * persistently failing download can't spin on every reconnect. `FAILED`
     * rows resume from byte 0 (their partial is gone / gapped); `PAUSED` rows
     * preserve their contiguous byte offset.
     */
    suspend fun resumeInterruptedDownloads()

    suspend fun setDownloadPriority(id: String, priority: Int): Result<Unit>
}
