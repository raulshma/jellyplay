package com.raulshma.jellyplay.feature.music.albumdetail

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val downloadRepository: DownloadRepository,
) : JellyPlayViewModel() {

    private val _detail = composeState<MediaDetail?>(null)
    val detail: MediaDetail? get() = _detail.value

    private val _tracks = composeState<List<MediaItem>>(emptyList())
    val tracks: List<MediaItem> get() = _tracks.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _isStartingMix = composeState(false)
    val isStartingMix: Boolean get() = _isStartingMix.value

    private val _mixFirstTrackId = composeState<String?>(null)
    val mixFirstTrackId: String? get() = _mixFirstTrackId.value

    fun loadAlbum(albumId: String) {
        launch {
            _isLoading.value = true
            _error.value = null
            mediaRepository.getMediaDetail(albumId)
                .onSuccess { _detail.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load album" }
            mediaRepository.getAlbumTracks(albumId)
                .onSuccess { _tracks.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load tracks" }
            _isLoading.value = false
        }
    }

    fun refreshAlbum(albumId: String) {
        launch {
            mediaRepository.invalidateDetailCache(albumId)
            loadAlbum(albumId)
        }
    }

    fun playAlbum(tracks: List<MediaItem>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val queueItems = tracks.map { track ->
            track.toAudioQueueItem(
                imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                albumFallback = detail?.item?.name,
            )
        }
        audioPlaybackManager.playQueue(queueItems, startIndex)
    }

    fun addToQueue(track: MediaItem) {
        val queueItem = track.toAudioQueueItem(
            imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
            albumFallback = detail?.item?.name,
        )
        audioPlaybackManager.addToQueue(queueItem)
    }

    fun startInstantMix(albumId: String) {
        launch {
            _isStartingMix.value = true
            _error.value = null
            mediaRepository.getInstantMix(albumId)
                .onSuccess { mix ->
                    if (mix.isEmpty()) {
                        _error.value = "No mix tracks available for this album"
                    } else {
                        val queueItems = mix.map { track ->
                            track.toAudioQueueItem(
                                imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                                albumFallback = detail?.item?.name,
                            )
                        }
                        audioPlaybackManager.playQueue(queueItems, 0)
                        _mixFirstTrackId.value = mix.first().id
                    }
                }
                .onFailure { _error.value = it.message ?: "Failed to start Instant Mix" }
            _isStartingMix.value = false
        }
    }

    fun consumeMixEvent() {
        _mixFirstTrackId.value = null
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    val trackDownloads: StateFlow<Map<String, DownloadItem>> = downloadRepository.getAllDownloads()
        .map { downloads -> downloads.associateBy { it.mediaItemId } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun downloadTrack(track: MediaItem) {
        val currentDownloads = trackDownloads.value
        val existing = currentDownloads[track.id]
        if (existing != null && existing.status == DownloadStatus.COMPLETED) {
            launch {
                downloadRepository.deleteDownload(existing.id)
            }
            return
        }

        launch {
            try {
                val detail = mediaRepository.getMediaDetail(track.id).getOrNull() ?: return@launch
                val source = detail.mediaSources.firstOrNull() ?: return@launch
                val streamUrl = playbackRepository.getStreamUrl(track.id, source.id)
                if (streamUrl.isBlank()) return@launch
                val imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 300)
                val mediaType = com.raulshma.jellyplay.core.model.MediaType.AUDIO.name

                downloadRepository.startDownload(
                    mediaItemId = track.id,
                    name = track.name,
                    mediaType = mediaType,
                    mediaSourceId = source.id,
                    downloadUrl = streamUrl,
                    imageUrl = imageUrl,
                    imageBlurHash = track.blurHashes.primary,
                ).onSuccess { downloadItem ->
                    if (downloadItem.status == DownloadStatus.PENDING) {
                        downloadRepository.enqueueDownload(downloadItem.id)
                        try {
                            val backdropUrl = playbackRepository.getBackdropUrl(track.id, maxWidth = 1280)
                            downloadRepository.saveOfflineMediaItem(track, imageUrl, backdropUrl)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(3)

    fun downloadAlbum() {
        val albumTracks = tracks
        if (albumTracks.isEmpty()) return
        val currentDownloads = trackDownloads.value
        launch {
            albumTracks.forEach { track ->
                val existing = currentDownloads[track.id]
                if (existing == null || existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.CANCELLED) {
                    launch {
                        downloadSemaphore.acquire()
                        try {
                            val detail = mediaRepository.getMediaDetail(track.id).getOrNull() ?: return@launch
                            val source = detail.mediaSources.firstOrNull() ?: return@launch
                            val streamUrl = playbackRepository.getStreamUrl(track.id, source.id)
                            if (streamUrl.isBlank()) return@launch
                            val imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 300)
                            val mediaType = com.raulshma.jellyplay.core.model.MediaType.AUDIO.name

                            downloadRepository.startDownload(
                                mediaItemId = track.id,
                                name = track.name,
                                mediaType = mediaType,
                                mediaSourceId = source.id,
                                downloadUrl = streamUrl,
                                imageUrl = imageUrl,
                                imageBlurHash = track.blurHashes.primary,
                            ).onSuccess { downloadItem ->
                                if (downloadItem.status == DownloadStatus.PENDING) {
                                    downloadRepository.enqueueDownload(downloadItem.id)
                                    try {
                                        val backdropUrl = playbackRepository.getBackdropUrl(track.id, maxWidth = 1280)
                                        downloadRepository.saveOfflineMediaItem(track, imageUrl, backdropUrl)
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            downloadSemaphore.release()
                        }
                    }
                }
            }
        }
    }

    fun deleteAlbumDownloads() {
        val albumTracks = tracks
        if (albumTracks.isEmpty()) return
        val currentDownloads = trackDownloads.value
        launch {
            albumTracks.forEach { track ->
                val existing = currentDownloads[track.id]
                if (existing != null) {
                    launch {
                        downloadRepository.deleteDownload(existing.id)
                    }
                }
            }
        }
    }
}
