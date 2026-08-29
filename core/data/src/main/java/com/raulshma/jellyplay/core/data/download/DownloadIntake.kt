package com.raulshma.jellyplay.core.data.download

import android.content.Context
import com.raulshma.jellyplay.core.data.R
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isMusicTrack
import com.raulshma.jellyplay.core.model.isVideoType
import com.raulshma.jellyplay.core.model.maxBitrate
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
 * **Depth**: the implementation absorbs the single-vs-batch-vs-browse routing
 * decision so callers don't branch on it. The bundle policy itself lives one
 * layer down in [DownloadDelegate].
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

    /**
     * Starts a download straight from a browse [item] (card long-press), with
     * no detail screen in between. Single-stream types (movie, episode, music
     * track) resolve their detail via [MediaRepository] and start immediately
     * at the user's default download quality. Types that need the detail
     * screen's richer flows — series (season/episode selection sheet), seasons,
     * albums, artists — return a navigation outcome instead so the host routes
     * there; nothing is enqueued for them.
     */
    suspend fun startFromItem(item: MediaItem): DownloadRequestResult
}

/**
 * Outcome of [DownloadIntake.startFromItem] — either the transfer started or
 * the host must route somewhere / surface a failure.
 */
sealed interface DownloadRequestResult {
    /** The transfer (or whole-season batch) was enqueued. */
    data object Started : DownloadRequestResult

    /**
     * The item is a series: enqueueing needs the user's season/episode
     * selection. Hosts open the detail screen for [seriesId] with the series
     * download sheet pre-presented.
     */
    data class SeriesSelectionRequired(val seriesId: String) : DownloadRequestResult

    /**
     * The item's download flow lives on its detail screen (season, album,
     * artist, ...). Hosts open the detail screen for [itemId]; no sheet needs
     * pre-presenting.
     */
    data class NeedsDetailScreen(val itemId: String) : DownloadRequestResult

    /** Nothing was enqueued; [message] is a displayable error, when known. */
    data class Failed(val message: String?) : DownloadRequestResult
}

@Singleton
class DownloadIntakeImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val delegate: DownloadDelegate,
    private val downloadRepository: DownloadRepository,
    private val mediaRepository: MediaRepository,
    private val downloadsStore: DownloadsStore,
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

    override suspend fun startFromItem(item: MediaItem): DownloadRequestResult {
        val inline = item.mediaType.isVideoType || item.mediaType.isMusicTrack
        if (!inline) {
            return when (item.mediaType) {
                MediaType.SERIES -> DownloadRequestResult.SeriesSelectionRequired(item.id)
                else -> DownloadRequestResult.NeedsDetailScreen(item.id)
            }
        }
        val detail = mediaRepository.getMediaDetail(item.id)
            .getOrElse { return DownloadRequestResult.Failed(it.message) }
        val result = start(detail, downloadsStore.downloads.value.downloadQuality.maxBitrate)
        return if (result.error == null) {
            DownloadRequestResult.Started
        } else {
            DownloadRequestResult.Failed(result.error)
        }
    }
}
