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

// DownloadIntake (the interface) + DownloadRequestResult live in shared/core/data
// jvmShared — same package, re-exported here via the module's api(...) dependency.
// This file keeps only the Android implementation.

class DownloadIntakeImpl(
    private val context: Context,
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
