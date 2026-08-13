package com.raulshma.jellyplay.core.data.download

import android.content.Context
import com.raulshma.jellyplay.core.data.R
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.model.MediaDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature-facing intake seam for offline downloads.
 *
 * Every caller that wants to start a download — a single movie/episode/audio
 * item from a detail screen, an album batch, a whole series from the
 * download sheet, or the periodic auto-download worker — goes through this
 * interface. It routes single-item downloads through [DownloadDelegate],
 * which owns the full artifact bundle (local poster/backdrop, trickplay,
 * external subtitles, intro/outro segments, rich offline metadata) so no
 * call site can re-implement the recipe and silently drop one of the
 * artifacts. Series batches delegate to [DownloadRepository.downloadSeries],
 * which performs the same bundle per episode.
 *
 * The low-level [DownloadRepository] API (startDownload, enqueueDownload,
 * saveOfflineMediaItem, downloadOfflineImage, ...) stays available for
 * lifecycle actions (pause/resume/cancel/delete) and status queries, but is
 * no longer the intake entry point for feature modules.
 *
 * **Depth**: this module's interface is two functions; the implementation
 * absorbs the single-vs-batch routing decision so callers don't branch on it.
 * The bundle policy itself lives one layer down in [DownloadDelegate].
 */
interface DownloadIntake {

    /**
     * Starts a download for a single item. Builds a [DownloadRequest] from
     * [detail], starts the transfer, enqueues the WorkManager job, and
     * bundles all offline artifacts (local images, trickplay, subtitles,
     * segments, offline metadata row).
     *
     * [maxBitrate] (bits per second) is applied to the stream URL so the
     * server transcodes to the user's chosen download quality; pass null for
     * original quality.
     *
     * [selectedSubtitleIndices] narrows the external subtitles bundled offline
     * to the given stream indices (by [com.raulshma.jellyplay.core.model.MediaStream.index]);
     * pass null to bundle every external/deliverable subtitle (the default).
     *
     * Returns a [DownloadResult] with either the started [DownloadItem] or
     * an error message. A null request (no media source / blank stream URL)
     * yields a result with a descriptive error rather than throwing.
     */
    suspend fun start(
        detail: MediaDetail,
        maxBitrate: Int? = null,
        selectedSubtitleIndices: Set<Int>? = null,
    ): DownloadResult

    /**
     * Starts a batch download for the given episodes of a series. If
     * [episodeIds] is null, every episode of every season of [seriesId] is
     * downloaded; otherwise only the listed episode ids per season id are.
     *
     * Returns the list of started download ids (may be empty if nothing
     * matched), or a failure if the series lookup or per-episode start
     * failed irrecoverably.
     */
    suspend fun startSeries(
        seriesId: String,
        episodeIds: Map<String, List<String>>? = null,
    ): Result<List<String>>
}

@Singleton
class DownloadIntakeImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val delegate: DownloadDelegate,
    private val downloadRepository: DownloadRepository,
) : DownloadIntake {

    override suspend fun start(
        detail: MediaDetail,
        maxBitrate: Int?,
        selectedSubtitleIndices: Set<Int>?,
    ): DownloadResult {
        // The per-item recipe (prepare + execute) lives in DownloadDelegate.startOne
        // so single-item intake and the series-batch loop share one code path.
        return delegate.startOne(detail, maxBitrate, selectedSubtitleIndices)
            ?: DownloadResult(
                downloadItem = null,
                error = context.getString(R.string.data_no_media_source_download),
            )
    }

    override suspend fun startSeries(
        seriesId: String,
        episodeIds: Map<String, List<String>>?,
    ): Result<List<String>> =
        downloadRepository.downloadSeries(seriesId, episodeIds)
}
