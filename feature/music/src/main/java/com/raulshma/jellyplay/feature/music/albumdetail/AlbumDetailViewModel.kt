package com.raulshma.jellyplay.feature.music.albumdetail

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.toMixErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
) : JellyPlayViewModel() {

    private val _detail = composeState<MediaDetail?>(null)
    val detail: MediaDetail? get() = _detail.value

    // StateFlow (not composeState) so `trackDownloads` below can observe the
    // loaded track ids and scope its downloads query to them.
    private val _tracks = stateFlow<List<MediaItem>>(emptyList())
    val tracks: List<MediaItem> get() = _tracks.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _isStartingMix = composeState(false)
    val isStartingMix: Boolean get() = _isStartingMix.value

    private val _mixFirstTrackId = composeState<String?>(null)
    val mixFirstTrackId: String? get() = _mixFirstTrackId.value

    fun loadAlbum(albumId: String, force: Boolean = false) {
        launch {
            _isLoading.value = true
            _error.value = null
            mediaRepository.getMediaDetail(albumId, force = force)
                .onSuccess { _detail.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load album" }
            mediaRepository.getAlbumTracks(albumId)
                .onSuccess { _tracks.set(it) }
                .onFailure { _error.value = it.message ?: "Failed to load tracks" }
            _isLoading.value = false
        }
    }

    fun refreshAlbum(albumId: String) {
        launch {
            loadAlbum(albumId, force = true)
        }
    }

    fun playAlbum(tracks: List<MediaItem>, startIndex: Int = 0) {
        launch {
            audioQueueFacade.playTracks(tracks, startIndex = startIndex, albumFallback = detail?.item?.name)
        }
    }

    fun addToQueue(track: MediaItem) {
        launch {
            audioQueueFacade.enqueueTrack(track, albumFallback = detail?.item?.name)
        }
    }

    fun startInstantMix(albumId: String) {
        launch {
            _isStartingMix.value = true
            _error.value = null
            val outcome = audioQueueFacade.startInstantMix(albumId, albumFallback = detail?.item?.name)
            if (outcome is AudioQueueOutcome.Started) {
                _mixFirstTrackId.value = outcome.queue.first().id
            } else {
                outcome.toMixErrorMessage(context)?.let { _error.value = it }
            }
            _isStartingMix.value = false
        }
    }

    fun consumeMixEvent() {
        _mixFirstTrackId.value = null
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    // Scoped to the loaded tracks' ids instead of observing the full downloads
    // window: Room re-emits on every 2 s progress tick, and this screen only
    // ever looks up its ~10-20 tracks, so the IN-scoped query re-reads a
    // handful of rows per tick rather than the whole table.
    @OptIn(ExperimentalCoroutinesApi::class)
    val trackDownloads: StateFlow<Map<String, DownloadItem>> = _tracks.flow
        .flatMapLatest { tracks ->
            if (tracks.isEmpty()) {
                flowOf(emptyMap())
            } else {
                downloadRepository.getDownloadsByMediaItemIdsFlow(tracks.map { it.id })
                    .map { downloads -> downloads.associateBy { it.mediaItemId } }
            }
        }
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
                // Intake seam owns the artifact bundle; previously this path
                // wrote only remote image URLs, so offline cards fell back to
                // blurHash. Local poster/backdrop are now persisted.
                downloadIntake.start(detail)
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
                            downloadIntake.start(detail)
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
