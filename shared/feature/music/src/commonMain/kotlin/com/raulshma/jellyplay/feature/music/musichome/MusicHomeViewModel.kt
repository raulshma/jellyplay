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
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.FetchMode
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
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
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val offlineModeManager: OfflineModeManager,
    private val userMessageBus: MusicMessageBus,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(MusicHomeUiState())
    val uiState = _uiState.flow

    /**
     * User-data changes while another screen is up (a favorite track flipped
     * elsewhere, outbox drain landing) only mark the sections stale — the
     * favorite artists/tracks rows re-load when the music home is next
     * entered (see [DeferredUserDataRefresher]) — never mid-scroll. The
     * whole load lifecycle lives in [DeferredFetchCoordinator]; this screen
     * adapts it with `Unit` (no subject id) and forces every loud entry —
     * its loud path is a plain refresh, so there is no identity to guard.
     */
    private val fetchCoordinator = DeferredFetchCoordinator<Unit>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = { _, mode, _ -> fetchSections(mode) },
        onLoudStart = {
            // The pull-to-refresh spinner keys off isLoading; the loud
            // error reset happens inside the fetch (past the offline gate,
            // so an offline retry keeps the error screen it retries from).
            _uiState.update { it.copy(isLoading = true) }
        },
        onLoudError = { e ->
            // A thrown repo path on a loud load (the deferred regeneration
            // never reaches this hook — it stays quiet, the user never asked
            // for it): keep showing cached sections if we have them; only
            // swap to the full ErrorScreen when there's nothing to show. A
            // failed refresh after data has loaded surfaces as a transient
            // toast instead of wiping the screen.
            val message = e.message ?: "Failed to load music"
            if (_uiState.value.sections.isEmpty()) {
                _uiState.update { it.copy(error = message) }
            } else {
                userMessageBus.error(message)
            }
            _uiState.update { it.copy(isLoading = false) }
        },
    )

    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

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
     * The loud load — pull-to-refresh, retry and the offline-mode
     * collector's return-online regeneration all land here, always forced
     * (see [fetchCoordinator]). Everything else about the load lifecycle
     * lives in [DeferredFetchCoordinator].
     */
    fun loadSections() {
        fetchCoordinator.load(Unit, force = true)
    }

    /**
     * The fetch behind both load paths, keyed on the coordinator's mode and
     * reporting plain success so [DeferredFetchCoordinator] owns the failure
     * re-arm.
     *
     * [FetchMode.SILENT] serves the deferred-refresh path: no loading state
     * (the pull-to-refresh spinner keys off [MusicHomeUiState.isLoading]),
     * and a failed fetch keeps the last sections on screen — serve-stale-
     * while-revalidate, same philosophy as the detail screens.
     */
    private suspend fun fetchSections(mode: FetchMode): Boolean {
        // Offline can't regenerate — report failure so the coordinator
        // re-arms (returning online fires a loud loadSections anyway, but
        // while the app stays offline the consumed flag must not strand the
        // change the refresh was armed for).
        if (_uiState.value.offlineMode != OfflineMode.ONLINE) {
            if (mode == FetchMode.LOUD) {
                _uiState.update { it.copy(isLoading = false) }
            }
            return false
        }
        if (mode == FetchMode.LOUD) {
            _uiState.update { it.copy(error = null) }
        }
        val sectionsList = mutableListOf<MusicHomeSection>()

        val ok = coroutineScope {
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
            // failed fetch keeps the last sections on screen instead of
            // silently dropping the rows that failed to re-fetch (a
            // loud load publishes what it got). A partial result
            // reports failure either way so the coordinator re-arms —
            // loud included, since the swallowed sub-fetch failures
            // dropped rows the next re-entry's silent refetch must
            // heal.
            val complete = results.all { it != null }
            if (mode != FetchMode.SILENT || complete) {
                _uiState.update { it.copy(sections = sectionsList) }
            }
            complete
        }
        // A thrown repo path never strands the loud spinner: the exception
        // escapes to [DeferredFetchCoordinator]'s error hook, which
        // publishes the loud failure (see the coordinator construction).
        if (mode == FetchMode.LOUD) {
            _uiState.update { it.copy(isLoading = false) }
        }
        return ok
    }

    fun refresh() {
        // No cache bypass needed (plan 08): every query this screen shows
        // (favorites + filtered items) is an uncached passthrough in the
        // repository, so the old global invalidateCaches() call was a
        // no-op for this screen's data.
        loadSections()
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
