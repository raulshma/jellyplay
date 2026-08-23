package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.model.MediaDetail

/**
 * Desktop actual of the [DownloadIntake] seam (V3 downloads conveyor): the
 * same single-item / series routing as the legacy Android DownloadIntakeImpl,
 * minus the localized-error `Context.getString` (the desktop uses the same
 * English copy the resource holds until the settings/i18n conveyor lands).
 *
 * Series batches go through [DownloadRepository.downloadSeries]; on desktop
 * that path fails loudly with the documented Phase X error at its first
 * MediaRepository use (no desktop definition exists yet — see
 * desktopDataModule's MediaRepositoryAccess). Single-item downloads work
 * end-to-end — for episodes, the missing series metadata degrades to the
 * minimal parent-row fallback (see saveOfflineMediaItem's runCatching).
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
