package com.raulshma.jellyplay.feature.music.musichome

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
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
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class MusicHomeViewModel(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
    private val downloadRepository: DownloadRepository,
    private val homeDiscoveryStore: com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore,
    private val offlineModeManager: OfflineModeManager,
    private val userMessageBus: MusicMessageBus,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(MusicHomeUiState())
    val uiState = _uiState.flow

    /**
     * The one load in flight, loud or silent — a loud load cancels a silent
     * one (it regenerates the same data loudly), and the deferred refresh
     * skips itself while one is active, so two fetches never race into a
     * last-writer-wins swap.
     */
    private var loadJob: Job? = null

    /**
     * User-data changes while another screen is up (a favorite track flipped
     * elsewhere, outbox drain landing) only mark the sections stale — the
     * favorite artists/tracks rows re-load when the music home is next
     * entered (see [DeferredUserDataRefresher]) — never mid-scroll. The
     * deferred regeneration runs [loadSections] silently: no loading spinner,
     * no toast.
     */
    val deferredRefresher = DeferredUserDataRefresher(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        onRefresh = { loadSections(silent = true) },
    )

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

    /**
     * [silent] serves the deferred-refresh path: no loading state (the
     * pull-to-refresh spinner keys off [MusicHomeUiState.isLoading]), and a
     * failed fetch keeps the last sections on screen — serve-stale-while-
     * revalidate, same philosophy as the detail screens — and re-arms the
     * deferred refresh so the next re-entry retries the failed regeneration.
     * A silent load skips itself while any load is active (that load
     * regenerates the same data); a loud load cancels an in-flight silent
     * one (see [loadJob]).
     */
    fun loadSections(silent: Boolean = false) {
        if (silent && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = launch {
            if (_uiState.value.offlineMode != OfflineMode.ONLINE) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
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

                    // A silent refresh publishes only complete results: any
                    // failed fetch keeps the last sections on screen instead
                    // of silently dropping the rows that failed to re-fetch —
                    // and re-arms the deferred refresh, since the change that
                    // triggered it was not regenerated.
                    if (!silent || results.all { it != null }) {
                        _uiState.update { it.copy(sections = sectionsList) }
                    } else {
                        deferredRefresher.rearm()
                    }
                }
            } catch (e: CancellationException) {
                // Superseded by the loud load that cancelled this one (or VM
                // teardown) — never masked as a fetch failure, or a cancelled
                // loud load would leave its spinner stuck on.
                throw e
            } catch (e: Exception) {
                // A silent (deferred) regeneration stays quiet — the user
                // never asked for this fetch, so the stale sections stay and
                // no toast fires; the change re-arms for the next re-entry.
                if (silent) {
                    deferredRefresher.rearm()
                } else {
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
            }
            if (!silent) {
                _uiState.update { it.copy(isLoading = false) }
            }
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
        return fetchSemaphore.mapConcurrent(albums) { album ->
            mediaRepository.getAlbumTracks(album.id)
                .getOrNull()
                .orEmpty()
                .map { track -> TrackWithAlbumFallback(track, album.name) }
        }.flatten()
    }
}
