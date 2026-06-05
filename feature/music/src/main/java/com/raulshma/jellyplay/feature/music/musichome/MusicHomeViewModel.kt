package com.raulshma.jellyplay.feature.music.musichome

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MusicHomeSection(
    val title: String,
    val items: List<MediaItem>,
)

@HiltViewModel
class MusicHomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val downloadRepository: DownloadRepository,
    private val preferencesStore: UserPreferencesStore,
    private val offlineRepository: OfflineRepository,
    private val offlineModeManager: OfflineModeManager,
) : JellyPlayViewModel() {

    private val _sections = composeState<List<MusicHomeSection>>(emptyList())
    val sections: List<MusicHomeSection> get() = _sections.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _homeMode = composeState(HomeMode.VIDEO)
    val homeMode: HomeMode get() = _homeMode.value

    private val _offlineMode = composeState(OfflineMode.ONLINE)
    val offlineMode: OfflineMode get() = _offlineMode.value

    private val _offlineLibrary = composeState<List<OfflineMediaItem>>(emptyList())
    val offlineLibrary: List<OfflineMediaItem> get() = _offlineLibrary.value

    val activeDownloadCount = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        launch {
            preferencesStore.preferences.collect { prefs ->
                _homeMode.value = prefs.homeMode
            }
        }
        launch {
            offlineModeManager.offlineMode.collect { mode ->
                _offlineMode.value = mode
                if (mode != OfflineMode.ONLINE) {
                    _sections.value = emptyList()
                } else {
                    loadSections()
                }
            }
        }
        launch {
            offlineRepository.getOfflineLibrary().collect { items ->
                _offlineLibrary.value = items
            }
        }
        launch { loadSections() }
    }

    fun toggleOfflineMode() {
        offlineModeManager.toggleManualOffline()
    }

    suspend fun loadSections() {
        launch {
            if (offlineMode != OfflineMode.ONLINE) {
                _isLoading.value = false
                return@launch
            }
            _isLoading.value = true
            _error.value = null
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
                            mediaTypes = listOf(MediaType.ALBUM),
                            sortBy = "DateCreated",
                            sortOrder = "Descending",
                            limit = 20,
                        ).getOrNull()?.items
                    }
                    val recentlyPlayed = async {
                        mediaRepository.getMediaItems(
                            mediaTypes = listOf(MediaType.AUDIO),
                            sortBy = "DatePlayed",
                            sortOrder = "Descending",
                            limit = 20,
                        ).getOrNull()?.items
                    }
                    val topRatedAlbums = async {
                        mediaRepository.getMediaItems(
                            mediaTypes = listOf(MediaType.ALBUM),
                            sortBy = "CommunityRating",
                            sortOrder = "Descending",
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

                    results[0]?.takeIf { it.isNotEmpty() }?.let {
                        sectionsList.add(MusicHomeSection("Favorite Artists", it))
                    }
                    results[1]?.takeIf { it.isNotEmpty() }?.let {
                        sectionsList.add(MusicHomeSection("Latest Albums", it))
                    }
                    results[2]?.takeIf { it.isNotEmpty() }?.let {
                        sectionsList.add(MusicHomeSection("Recently Played", it))
                    }
                    results[3]?.takeIf { it.isNotEmpty() }?.let {
                        sectionsList.add(MusicHomeSection("Top Rated Albums", it))
                    }
                    results[4]?.takeIf { it.isNotEmpty() }?.let {
                        sectionsList.add(MusicHomeSection("Favorite Tracks", it))
                    }
                }

                _sections.value = sectionsList
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load music"
            }
            _isLoading.value = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    fun surpriseMe(callback: (String) -> Unit) {
        launch {
            mediaRepository.getMediaItems(
                mediaTypes = listOf(MediaType.AUDIO),
                sortBy = "Random",
                limit = 1,
            ).onSuccess { result ->
                result.items.firstOrNull()?.let { callback(it.id) }
            }
        }
    }

    fun playAll(tracks: List<MediaItem>, startIndex: Int = 0) {
        launch {
            val queueItems = tracks.map { track ->
                AudioQueueItem(
                    id = track.id,
                    name = track.name,
                    artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
                    album = track.album,
                    imageUrl = getImageUrl(track.id),
                    mediaSourceId = null,
                    durationMs = track.runTimeTicks?.let { it / 10_000 } ?: 0L,
                    normalizationGain = track.normalizationGain,
                )
            }
            audioPlaybackManager.playQueue(queueItems, startIndex)
        }
    }

    fun shufflePlay(tracks: List<MediaItem>) {
        val shuffled = tracks.shuffled()
        playAll(shuffled, startIndex = 0)
    }

    fun playAlbum(albumId: String) {
        launch {
            mediaRepository.getAlbumTracks(albumId)
                .onSuccess { tracks ->
                    if (tracks.isEmpty()) return@launch
                    val queueItems = tracks.map { track ->
                        AudioQueueItem(
                            id = track.id,
                            name = track.name,
                            artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
                            album = track.album,
                            imageUrl = getImageUrl(track.id),
                            mediaSourceId = null,
                            durationMs = track.runTimeTicks?.let { it / 10_000 } ?: 0L,
                            normalizationGain = track.normalizationGain,
                        )
                    }
                    audioPlaybackManager.playQueue(queueItems, 0)
                }
        }
    }

    fun playAlbums(albums: List<MediaItem>) {
        launch {
            val allTracks = fetchAlbumTracksParallel(albums)
            if (allTracks.isNotEmpty()) {
                audioPlaybackManager.playQueue(allTracks, 0)
            }
        }
    }

    fun shuffleAlbums(albums: List<MediaItem>) {
        launch {
            val allTracks = fetchAlbumTracksParallel(albums).shuffled()
            if (allTracks.isNotEmpty()) {
                audioPlaybackManager.playQueue(allTracks, 0)
            }
        }
    }

    private suspend fun fetchAlbumTracksParallel(albums: List<MediaItem>): List<AudioQueueItem> {
        return coroutineScope {
            albums.map { album ->
                async {
                    mediaRepository.getAlbumTracks(album.id)
                        .getOrNull()
                        .orEmpty()
                        .map { track ->
                            AudioQueueItem(
                                id = track.id,
                                name = track.name,
                                artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
                                album = track.album ?: album.name,
                                imageUrl = getImageUrl(track.id),
                                mediaSourceId = null,
                                durationMs = track.runTimeTicks?.let { it / 10_000 } ?: 0L,
                                normalizationGain = track.normalizationGain,
                            )
                        }
                }
            }.awaitAll().flatten()
        }
    }
}
