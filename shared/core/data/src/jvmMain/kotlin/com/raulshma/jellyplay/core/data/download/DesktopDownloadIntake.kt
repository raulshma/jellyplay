package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.model.MediaDetail

/**
 * Desktop actual of the [DownloadIntake] seam (V3 downloads conveyor): the
 * same single-item / series routing as the legacy Android DownloadIntakeImpl,
 * minus the localized-error `Context.getString` — the desktop intake emits
 * pre-resolved strings, not resource ids.
 *
 * Series batches go through [DownloadRepository.downloadSeries]; on desktop
 * that path is fully live — MediaRepositoryAccess is real (Koin owns
 * MediaRepositoryImpl on desktop too, see desktopDataModule), so series
 * downloads and the auto-download loop work end-to-end. Single-item downloads
 * likewise work end-to-end — for episodes, any missing series metadata
 * degrades to the minimal parent-row fallback (see saveOfflineMediaItem's
 * runCatching).
 *
 * The no-source error below is a fixed base-locale English literal,
 * byte-matching Android's `R.string.data_no_media_source_download` base
 * locale. This is an accepted desktop locale delta, not a pending item: the
 * intake's consumers (details/player-audio/music message seams) all carry
 * pre-resolved strings and core:data has no compose-resource access, so
 * localizing one desktop-only sentinel would require sentinel-matching
 * translation plumbing across those seams — same rationale as
 * [com.raulshma.jellyplay.core.data.repository.DesktopAdminStatisticsLabels].
 */
class DesktopDownloadIntake(
    private val delegate: DownloadDelegate,
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
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
                // Same copy as the Android R.string.data_no_media_source_download.
                error = "No media source available for download",
            )
    }

    override suspend fun startSeries(
        seriesId: String,
        episodeIds: Map<String, List<String>>?,
    ): Result<List<String>> =
        downloadRepository.downloadSeries(seriesId, episodeIds)
}
