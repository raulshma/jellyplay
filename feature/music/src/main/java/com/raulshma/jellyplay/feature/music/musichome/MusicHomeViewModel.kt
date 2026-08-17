package com.raulshma.jellyplay.feature.music.musichome

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.TrackWithAlbumFallback
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class MusicHomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
    private val downloadRepository: DownloadRepository,
    private val homeDiscoveryStore: com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore,
    private val offlineModeManager: OfflineModeManager,
    private val userMessageBus: UserMessageBus,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(MusicHomeUiState())
    val uiState = _uiState.flow

    val activeDownloadCount = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        launch {
            homeDiscoveryStore.homeDiscovery.collect { prefs ->
                _uiState.update { it.copy(homeMode = prefs.homeMode) }
            }
        }
        launch {
            offlineModeManager.offlineMode.collect { mode ->
                _uiState.update { it.copy(offlineMode = mode) }
                if (mode != OfflineMode.ONLINE) {
                    _uiState.update { it.copy(sections = emptyList()) }
                } else {
                    loadSections()
                }
            }
        }
    }

    fun toggleOfflineMode() {
        offlineModeManager.toggleManualOffline()
    }

    fun loadSections() {
        launch {
            if (_uiState.value.offlineMode != OfflineMode.ONLINE) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val sectionsList = mutableListOf<MusicHomeSection>()

                coroutineScope {
                    val favArtists = async {
                        mediaRepository.getFavorites(
                            mediaTypes = listOf(MediaType.ARTIST),
                            limit = 20,
                        ).getOrNull()?.items
                    }
                    val latestAlbums = async {
                        mediaRepository.getMediaItems(
                            filters = LibraryFilters(
                                mediaTypes = listOf(MediaType.ALBUM),
                                sortBy = SortOption.DATE_ADDED,
                            ),
                            limit = 20,
                        ).getOrNull()?.items
                    }
                    val recentlyPlayed = async {
                        mediaRepository.getMediaItems(
                            filters = LibraryFilters(
                                mediaTypes = listOf(MediaType.AUDIO),
                                sortBy = SortOption.DATE_PLAYED,
                            ),
                            limit = 20,
                        ).getOrNull()?.items
                    }
                    val topRatedAlbums = async {
                        mediaRepository.getMediaItems(
                            filters = LibraryFilters(
                                mediaTypes = listOf(MediaType.ALBUM),
                                sortBy = SortOption.RATING,
                            ),
                            limit = 20,
                        ).getOrNull()?.items
                    }
                    val favTracks = async {
                        mediaRepository.getFavorites(
                            mediaTypes = listOf(MediaType.AUDIO),
                            limit = 20,
                        ).getOrNull()?.items
                    }

                    val results = awaitAll(favArtists, latestAlbums, recentlyPlayed, topRatedAlbums, favTracks)

                    fun section(type: MusicHomeSectionType, items: List<MediaItem>?) =
                        items?.takeIf { it.isNotEmpty() }?.let { MusicHomeSection(type, it) }

                    section(MusicHomeSectionType.FAVORITE_ARTISTS, results[0])?.let(sectionsList::add)
                    section(MusicHomeSectionType.LATEST_ALBUMS, results[1])?.let(sectionsList::add)
                    section(MusicHomeSectionType.RECENTLY_PLAYED, results[2])?.let(sectionsList::add)
                    section(MusicHomeSectionType.TOP_RATED_ALBUMS, results[3])?.let(sectionsList::add)
                    section(MusicHomeSectionType.FAVORITE_TRACKS, results[4])?.let(sectionsList::add)
                }

                _uiState.update { it.copy(sections = sectionsList) }
            } catch (e: Exception) {
                val message = e.message ?: "Failed to load music"
                // Keep showing cached sections if we have them; only swap to the full
                // ErrorScreen when there's nothing to show. A failed refresh after data
                // has loaded surfaces as a transient toast instead of wiping the screen.
                if (_uiState.value.sections.isEmpty()) {
                    _uiState.update { it.copy(error = message) }
                } else {
                    userMessageBus.error(message)
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refresh() {
        launch {
            // No cache bypass needed (plan 08): every query this screen shows
            // (favorites + filtered items) is an uncached passthrough in the
            // repository, so the old global invalidateCaches() call was a
            // no-op for this screen's data.
            loadSections()
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    fun surpriseMe(callback: (String) -> Unit) {
        launch {
            mediaRepository.getMediaItems(
                filters = LibraryFilters(
                    mediaTypes = listOf(MediaType.AUDIO),
                    sortBy = SortOption.RANDOM,
                ),
                limit = 1,
            ).onSuccess { result ->
                result.items.firstOrNull()?.let { callback(it.id) }
            }
        }
    }

    fun playAll(tracks: List<MediaItem>, startIndex: Int = 0) {
        launch {
            audioQueueFacade.playTracks(tracks, startIndex = startIndex)
        }
    }

    fun shufflePlay(tracks: List<MediaItem>) {
        launch {
            audioQueueFacade.playTracks(tracks, shuffled = true)
        }
    }

    fun playAlbum(albumId: String) {
        launch {
            mediaRepository.getAlbumTracks(albumId)
                .onSuccess { tracks -> audioQueueFacade.playTracks(tracks) }
        }
    }

    fun playArtist(artistId: String) {
        launch {
            mediaRepository.getArtistAlbums(artistId)
                .onSuccess { albums ->
                    if (albums.isNotEmpty()) {
                        playAlbums(albums)
                    }
                }
        }
    }

    fun playAlbums(albums: List<MediaItem>) {
        launch {
            // One playTracks over the concatenated, per-album-mapped list —
            // byte-for-byte the former single one-shot playQueue ordering.
            audioQueueFacade.playTracks(fetchAlbumTracksParallel(albums))
        }
    }

    fun shuffleAlbums(albums: List<MediaItem>) {
        launch {
            audioQueueFacade.playTracks(fetchAlbumTracksParallel(albums), shuffled = true)
        }
    }

    private val fetchSemaphore = Semaphore(4)

    /**
     * Parallel (Semaphore(4)) album-track fetch. Returns each track paired with
     * its own album fallback (the source album's name) so the facade can map
     * per-album naming before concatenation — plan 04 risk 2.
     */
    private suspend fun fetchAlbumTracksParallel(albums: List<MediaItem>): List<TrackWithAlbumFallback> {
        return coroutineScope {
            albums.map { album ->
                async {
                    fetchSemaphore.withPermit {
                        mediaRepository.getAlbumTracks(album.id)
                        .getOrNull()
                        .orEmpty()
                        .map { track -> TrackWithAlbumFallback(track, album.name) }
                    }
                }
            }.awaitAll().flatten()
        }
    }
}
