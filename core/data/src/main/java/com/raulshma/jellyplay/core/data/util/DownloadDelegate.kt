package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadRequest(
    val mediaItemId: String,
    val name: String,
    val mediaType: String,
    val mediaSourceId: String,
    val downloadUrl: String,
    val imageUrl: String,
    val imageBlurHash: String?,
    val trickplayInfo: com.raulshma.jellyplay.core.model.TrickplayInfo? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
)

data class DownloadResult(
    val downloadItem: DownloadItem?,
    val error: String?,
)

@Singleton
class DownloadDelegate @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val playbackRepository: PlaybackRepository,
) {

    suspend fun prepareDownloadRequest(detail: MediaDetail): DownloadRequest? {
        val item = detail.item
        val source = detail.mediaSources.firstOrNull() ?: return null
        val streamUrl = playbackRepository.getStreamUrl(item.id, source.id)
        if (streamUrl.isBlank()) return null
        val imageUrl = playbackRepository.getImageUrl(item.id, maxWidth = 300)
        val mediaType = when (item.mediaType) {
            MediaType.AUDIO, MediaType.MUSIC -> MediaType.AUDIO.name
            else -> item.mediaType.name
        }
        return DownloadRequest(
            mediaItemId = item.id,
            name = item.name,
            mediaType = mediaType,
            mediaSourceId = source.id,
            downloadUrl = streamUrl,
            imageUrl = imageUrl,
            imageBlurHash = item.blurHashes.primary,
            trickplayInfo = source.trickplayInfo,
            mediaStreams = source.mediaStreams,
        )
    }

    suspend fun executeDownload(request: DownloadRequest): DownloadResult {
        val result = downloadRepository.startDownload(
            mediaItemId = request.mediaItemId,
            name = request.name,
            mediaType = request.mediaType,
            mediaSourceId = request.mediaSourceId,
            downloadUrl = request.downloadUrl,
            imageUrl = request.imageUrl,
            imageBlurHash = request.imageBlurHash,
        )
        return result.fold(
            onSuccess = { downloadItem ->
                if (downloadItem.status == com.raulshma.jellyplay.core.model.DownloadStatus.PENDING) {
                    downloadRepository.enqueueDownload(downloadItem.id)
                    try {
                        val backdropUrl = playbackRepository.getBackdropUrl(request.mediaItemId, maxWidth = 1280)
                        val stubItem = com.raulshma.jellyplay.core.model.MediaItem(
                            id = request.mediaItemId,
                            name = request.name,
                            mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE,
                        )
                        downloadRepository.saveOfflineMediaItem(
                            stubItem,
                            request.imageUrl,
                            backdropUrl,
                        )
                    } catch (_: Exception) {}
                    request.trickplayInfo?.let { info ->
                        try {
                            downloadRepository.downloadTrickplayData(request.mediaItemId, info, downloadItem.downloadPath)
                        } catch (_: Exception) {}
                    }
                    // Bundle external subtitles + intro/outro segments for offline use.
                    if (request.mediaStreams.isNotEmpty()) {
                        try {
                            downloadRepository.downloadExternalSubtitles(
                                request.mediaItemId,
                                request.mediaSourceId,
                                request.mediaStreams,
                                downloadItem.downloadPath,
                            )
                        } catch (_: Exception) {}
                    }
                    try {
                        downloadRepository.downloadMediaSegments(request.mediaItemId, downloadItem.downloadPath)
                    } catch (_: Exception) {}
                }
                DownloadResult(downloadItem = downloadItem, error = null)
            },
            onFailure = { error ->
                DownloadResult(downloadItem = null, error = error.message ?: "Download failed")
            },
        )
    }
}
