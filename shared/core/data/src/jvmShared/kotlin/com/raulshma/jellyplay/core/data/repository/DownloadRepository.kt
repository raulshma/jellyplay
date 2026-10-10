package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * What one retention sweep pass reclaimed — the storage-settings "Clean up
 * now" summary and the auto-download check's sweep step log line.
 */
data class AutoDownloadSweepResult(
    /** Completed downloads deleted this pass. */
    val deletedCount: Int,
    /** Their persisted [DownloadItem.totalSizeBytes] sum, taken before deletion. */
    val bytesReclaimed: Long,
) {
    companion object {
        val EMPTY = AutoDownloadSweepResult(deletedCount = 0, bytesReclaimed = 0L)
    }
}

/**
 * One snapshot of the download-coverage union — the completed item ids and the
 * series ids (series with at least one downloaded episode) a UI gate or the
 * auto-download worker reads. [ids] is the union the UI actually gates on: a
 * series card's Download action flips to Remove download once either half
 * contains the card's id. Consumers used to build the union themselves and
 * home was the only one honoring the series half; the contract is the
 * interface's, not a convention.
 */
data class DownloadCoverage(
    /** Download-complete item ids ([observeCompletedDownloadedIds]' set). */
    val completedItemIds: Set<String>,
    /** Every series id with at least one downloaded episode. */
    val seriesIds: Set<String>,
) {
    /** The union the UI gates on: [completedItemIds] ∪ [seriesIds]. */
    val ids: Set<String> get() = completedItemIds + seriesIds

    companion object {
        val EMPTY = DownloadCoverage(emptySet(), emptySet())
    }
}

// `DownloadProgress` (the feature-facing per-row transfer projection) lives in
// commonMain now (repository/DownloadProgress.kt) — promoted verbatim with the
// DownloadQueue interface, whose surface names it.

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
     * Live byte/speed progress for in-flight downloads, keyed by download id
     * — the hot companion to [getAllDownloads]. The 2 s transfer
     * ticker's DAO write invalidates the whole `downloads` table, and
     * [getAllDownloads]' change filter (id order + per-item bytes/status)
     * deliberately forwards byte movement for consumers that render live
     * progress from the item list itself (album detail's per-track bars).
     * The downloads screen instead treats bytes/speed as non-structural: it
     * suppresses tick-only list re-emissions and reads the moving values
     * from this narrow projection instead, so a tick re-renders
     * only the actively-downloading rows. Rows drop out of the map as soon
     * as their status leaves the in-flight set (PENDING/QUEUED/DOWNLOADING),
     * which is exactly when the status change re-emits [getAllDownloads]
     * carrying the row's final bytes.
     */
    fun getActiveDownloadProgress(): Flow<Map<String, DownloadProgress>>

    /**
     * One-shot read of every download row, uncapped — unlike [getAllDownloads],
     * whose 500-row window exists for list rendering. The force-resync picker
     * resolves its candidates from this so every downloaded item stays
     * resyncable regardless of library size, and so the picker is correct even
     * when opened before the reactive list has emitted its first value.
     */
    suspend fun getAllDownloadsSnapshot(): List<DownloadItem>

    /**
     * Download rows for exactly [mediaItemIds] — the scoped reactive read for
     * screens tracking a known set of items (e.g. an album's tracks). Fetching
     * only the matching rows costs far less per re-emission than collecting
     * [getAllDownloads]' full-window query (re-run on every 2 s progress tick
     * during transfers) and filtering it client-side.
     */
    fun getDownloadsByMediaItemIdsFlow(mediaItemIds: List<String>): Flow<List<DownloadItem>>

    /**
     * One page of completed audio (`MUSIC`/`AUDIO`) downloads, newest first —
     * the media-library DOWNLOADS browse page's window. Same filter and order
     * the caller previously applied over [getAllDownloads], resolved in one
     * query instead of a full-table fetch per page request.
     */
    suspend fun getCompletedAudioDownloads(limit: Int, offset: Int): List<DownloadItem>

    fun getDownloadByMediaItemIdFlow(mediaItemId: String): Flow<DownloadItem?>

    fun getActiveDownloadCount(): Flow<Int>

    /**
     * Reactive set of ids that are download-complete, for coarse per-item
     * gating (quick actions flip between Download and Remove download).
     * Series/season ids are included when they have completed episodes. The
     * underlying query re-emits on every `downloads` write — including 2 s
     * progress ticks — so the impl collapses equal sets with
     * `distinctUntilChanged`; the mapped set is small and comparison is cheap.
     */
    fun observeCompletedDownloadedIds(): Flow<Set<String>>

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

    suspend fun episodeIdsForSeries(seriesId: String): Set<String>

    /**
     * All downloaded episode ids grouped by their parent series, fetched in a
     * single 2-column query. Intended for callers (e.g. the periodic
     * auto-download worker) that need the ids for *every* series at once —
     * preferable to calling [episodeIdsForSeries] per series,
     * which issues N full-row queries (N+1) while consuming only `mediaItemId`.
     */
    suspend fun getDownloadedEpisodeIdsBySeries(): Map<String, Set<String>>

    /**
     * The [DownloadCoverage] union flow — [observeCompletedDownloadedIds] ∪ the
     * series-with-downloads read, snapped into one value. Collapses equal
     * coverages like its inputs; consumers that need only the flat id set read
     * [DownloadCoverage.ids]. The auto-download worker reads
     * [DownloadCoverage.seriesIds] for its per-pass series walk.
     */
    fun downloadCoverage(): Flow<DownloadCoverage>

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

    /**
     * One keep-days retention sweep pass: deletes every completed download
     * whose `completedAt` is older than the `auto_download_keep_days` window
     * **unless the item is unwatched** (the local played-state mirror protects
     * in-progress content — a row with no offline metadata, or an unplayed
     * one, is never deleted), reusing the shared offline-deletion
     * choreography. `auto_download_keep_days = 0` is off: a no-op returning
     * [AutoDownloadSweepResult.EMPTY].
     *
     * Runs as the first step of the auto-download check pass and is callable
     * on demand from the storage settings screen's "Clean up now" action.
     */
    suspend fun sweepExpiredAutoDownloads(): AutoDownloadSweepResult
}
