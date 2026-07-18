package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
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
    // Full server detail for the item being downloaded. Persisted into the
    // offline store so the redesigned offline detail screens can show cast,
    // studios, ratings, overview, etc. — instead of the bare stub used before.
    val detail: MediaDetail? = null,
    // Original container format from the Jellyfin MediaSource ("mkv", "mp4",
    // "ts", ...). Used to derive the on-disk file extension and to attach the
    // correct MIME type to the player engine at playback time.
    val container: String? = null,
)

data class DownloadResult(
    val downloadItem: DownloadItem?,
    val error: String?,
)

@Singleton
class DownloadDelegate @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
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
            detail = detail,
            container = source.container,
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
            container = request.container,
        )
        return result.fold(
            onSuccess = { downloadItem ->
                if (downloadItem.status == com.raulshma.jellyplay.core.model.DownloadStatus.PENDING) {
                    downloadRepository.enqueueDownload(downloadItem.id)
                    try {
                        val backdropUrl = playbackRepository.getBackdropUrl(request.mediaItemId, maxWidth = 1280)
                        // Download poster + backdrop to local files so they render
                        // offline; fall back to the remote URL if a download fails.
                        // Filenames are keyed by mediaItemId so items sharing the
                        // flat downloads dir don't overwrite each other.
                        val parentDir = java.io.File(downloadItem.downloadPath).parentFile
                        val localPoster = if (parentDir != null) {
                            downloadRepository.downloadOfflineImage(
                                request.mediaItemId, "Primary", 300, parentDir,
                                com.raulshma.jellyplay.core.data.repository.DownloadArtifacts.posterFile(request.mediaItemId),
                            ) ?: request.imageUrl
                        } else {
                            request.imageUrl
                        }
                        val localBackdrop = if (parentDir != null) {
                            downloadRepository.downloadOfflineImage(
                                request.mediaItemId, "Backdrop", 1280, parentDir,
                                com.raulshma.jellyplay.core.data.repository.DownloadArtifacts.backdropFile(request.mediaItemId),
                            ) ?: backdropUrl
                        } else {
                            backdropUrl
                        }
                        // Persist full metadata when the originating MediaDetail
                        // is available (overview, cast, studios, ratings, …);
                        // otherwise fall back to the minimal item path so we
                        // never leave the download without an offline row.
                        val detail = request.detail
                        if (detail != null) {
                            downloadRepository.saveOfflineMediaDetail(
                                detail,
                                localPoster,
                                localBackdrop,
                            )
                        } else {
                            val minimalItem = com.raulshma.jellyplay.core.model.MediaItem(
                                id = request.mediaItemId,
                                name = request.name,
                                mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE,
                            )
                            downloadRepository.saveOfflineMediaItem(
                                minimalItem,
                                localPoster,
                                localBackdrop,
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("DownloadDelegate", "Failed to persist offline images for ${request.mediaItemId}", e)
                    }
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
