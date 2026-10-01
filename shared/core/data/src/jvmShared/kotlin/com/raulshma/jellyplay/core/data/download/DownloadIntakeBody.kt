package com.raulshma.jellyplay.core.data.download

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

/**
 * The one JVM body of the [DownloadIntake] seam (V3 downloads conveyor) —
 * the I1 fold of the platform hand-twins. Before this, the Android
 * `DownloadIntakeImpl` and the desktop `DesktopDownloadIntake` restated
 * start/startSeries/startFromItem line-for-line, the ONLY delta being the
 * no-source error string (Android resolves `R.string.data_no_media_source_download`
 * via Context; desktop emits the base-locale literal — see its KDoc for the
 * accepted locale-delta rationale). Per the OfflineResync /
 * QuickDownloadActions / TrackDownloadStatusWindow fold precedent, the
 * shared body lives here in jvmShared beside the [DownloadDelegate] /
 * [DownloadRepository] machinery it drives, and both platform classes
 * shrink to constructors supplying [noSourceError] — their names, packages
 * and constructor signatures stay (Koin binds both: androidIntakeStorageModule
 * / desktopDownloadsSeamsModule), so this fold changes no public surface.
 */
open class DownloadIntakeBody(
    private val delegate: DownloadDelegate,
    private val downloadRepository: DownloadRepository,
    private val mediaRepository: MediaRepository,
    private val downloadsStore: DownloadsStore,
    /** Resolved no-source error copy — the twins' single real platform delta. */
    private val noSourceError: () -> String,
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
                error = noSourceError(),
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
