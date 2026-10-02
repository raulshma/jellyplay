package com.raulshma.jellyplay.feature.music.musichome

import com.raulshma.jellyplay.core.concurrency.DEFAULT_FANOUT_PARALLELISM
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.data.download.ActiveDownloadCount
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaCollectionReads
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MusicCatalogue
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
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.feature.music.MusicTrackWithAlbumFallback
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class MusicHomeViewModel(
    /** The detail/feed members only; the album/artist catalogue reads ride [musicCatalogue]. */
    private val mediaRepository: MediaRepository,
    private val musicCatalogue: MusicCatalogue,
    /** The SearchResult-shaped reads (favorites + browse queries — off the union). */
    private val mediaCollectionReads: MediaCollectionReads,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: MusicQueuePlayer,
    private val activeDownloads: ActiveDownloadCount,
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
     * entered (see [DeferredUserDataRefresher]) — never mid-scroll. The whole
     * load lifecycle — the single-flight slot, the skip/conservative re-arm
     * table and the deferred refresher — lives in [DeferredFetchCoordinator];
     * this screen adapts it with `Unit` (no subject id) and forces every
     * loud entry — its loud path is a plain refresh, so there is no identity
     * to guard.
     *
     * The state-container coordinator with the screen's multi-field uiState
     * kept hand-rolled beside it (`Unit` content — the sections aggregate
     * stays in [_uiState], published by the fetch body): this screen needs
     * the three decisions the generic interface deliberately removed the
     * mode parameter for — the loud fetch publishes PARTIAL sections (a
     * dropped sub-fetch fails the load for the re-arm while publishing what
     * it got), its failures stay quiet past the offline gate (which fails
     * by re-arm alone, never an error surface), and the loud error reset
     * happens past that gate so an offline retry keeps the error screen it
     * retries from. [fetchSections] recovers the loud/silent split from the
     * phase the loud entry publishes (see there).
     */
    private val fetchCoordinator = DeferredFetchCoordinator<Unit, Unit>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = { _, _ -> fetchSections() },
    )

    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

    val activeDownloadCount = activeDownloads.getActiveDownloadCount()
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
     *
     * The isLoading publish is synchronous, BEFORE
     * [DeferredFetchCoordinator.load]'s launch: the pull-to-refresh spinner
     * keys off isLoading, so the screen must read Loading the moment this
     * returns — not after the scope dispatches. The fetch body keys its
     * loud/silent decisions off the same phase (see [fetchSections]).
     */
    fun loadSections() {
        _uiState.update { it.copy(isLoading = true) }
        fetchCoordinator.load(Unit, force = true)
    }

    /**
     * The fetch behind both load paths — the loud entry (which published
     * `isLoading = true` synchronously in [loadSections]) and the
     * coordinator's silent deferred regeneration (which only ever starts
     * with no loud load in flight, so isLoading is false). The state
     * container's interface carries no mode, so the body reads the phase at
     * entry as the loud marker: single-flight guarantees a silent body
     * cannot start behind a loud load's spinner, and every loud terminal —
     * this body's own paths included — clears it. It is the one state bit
     * that differs between the two paths at fetch time.
     *
     * Loud-only decisions keyed off that marker: the spinner clear (and the
     * error reset — PAST the offline gate, so an offline retry keeps the
     * error screen it retries from), and the partial publish: a loud load
     * publishes what it got, while a silent refresh publishes only complete
     * results (a failed sub-fetch keeps the last sections on screen instead
     * of silently dropping the rows that failed to re-fetch — serve-stale-
     * while-revalidate, same philosophy as the detail screens). A partial
     * result reports failure either way so the deferred refresh re-arms —
     * loud included, since the swallowed sub-fetch failures dropped rows
     * the next re-entry's silent refetch must heal. The coordinator only
     * re-arms what throws, so these quiet failures re-arm through
     * [DeferredUserDataRefresher.rearm] themselves.
     *
     * A thrown repo path is the other failure shape: it escapes the
     * coroutineScope, the loud path publishes it (cold error state with
     * nothing cached, transient bus toast with cached sections) exactly as
     * the pre-migration loud error hook did, and the rethrow hands it to
     * [DeferredFetchCoordinator]'s error arm — which re-arms and keeps a
     * silent throw quiet. Cancellation rethrows ahead of the Exception arm:
     * a cancelled load must not mask as failure and strand the spinner it
     * published (a non-[Exception] throwable is not a fetch failure either
     * — it skips the catch and surfaces as it always did).
     */
    private suspend fun fetchSections() {
        val loud = _uiState.value.isLoading
        // Offline can't regenerate — re-arm either way (returning online
        // fires a loud loadSections anyway, but while the app stays offline
        // the consumed flag must not strand the change the refresh was
        // armed for).
        if (_uiState.value.offlineMode != OfflineMode.ONLINE) {
            if (loud) {
                _uiState.update { it.copy(isLoading = false) }
            }
            fetchCoordinator.deferredRefresher.rearm()
            return
        }
        if (loud) {
            _uiState.update { it.copy(error = null) }
        }
        val sectionsList = mutableListOf<MusicHomeSection>()

        val ok = try {
            coroutineScope {
                val favArtists = async {
                    mediaCollectionReads.getFavorites(
                        mediaTypes = listOf(MediaType.ARTIST),
                        limit = 20,
                    ).getOrNull()?.items
                }
                val latestAlbums = async {
                    mediaCollectionReads.getMediaItems(
                        filters = LibraryFilters(
                            mediaTypes = listOf(MediaType.ALBUM),
                            sortBy = SortOption.DATE_ADDED,
                        ),
                        limit = 20,
                    ).getOrNull()?.items
                }
                val recentlyPlayed = async {
                    mediaCollectionReads.getMediaItems(
                        filters = LibraryFilters(
                            mediaTypes = listOf(MediaType.AUDIO),
                            sortBy = SortOption.DATE_PLAYED,
                        ),
                        limit = 20,
                    ).getOrNull()?.items
                }
                val topRatedAlbums = async {
                    mediaCollectionReads.getMediaItems(
                        filters = LibraryFilters(
                            mediaTypes = listOf(MediaType.ALBUM),
                            sortBy = SortOption.RATING,
                        ),
                        limit = 20,
                    ).getOrNull()?.items
                }
                val favTracks = async {
                    mediaCollectionReads.getFavorites(
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

                val complete = results.all { it != null }
                if (loud || complete) {
                    _uiState.update { it.copy(sections = sectionsList) }
                }
                complete
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (loud) {
                // A thrown repo path on a loud load (the deferred
                // regeneration never surfaces it — it stays quiet, the user
                // never asked for it): keep showing cached sections if we
                // have them; only swap to the full ErrorScreen when there's
                // nothing to show. A failed refresh after data has loaded
                // surfaces as a transient toast instead of wiping the
                // screen.
                val message = UserErrorMessages.resolve(e, "Failed to load music")
                if (_uiState.value.sections.isEmpty()) {
                    _uiState.update { it.copy(error = message) }
                } else {
                    userMessageBus.error(message)
                }
                _uiState.update { it.copy(isLoading = false) }
            }
            throw e
        }
        // The loud spinner never strands: the success path clears it here
        // (a thrown path cleared it in the catch, before rethrowing into
        // the coordinator's error arm).
        if (loud) {
            _uiState.update { it.copy(isLoading = false) }
        }
        if (!ok) {
            fetchCoordinator.deferredRefresher.rearm()
        }
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
            mediaCollectionReads.getMediaItems(
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
            musicCatalogue.getAlbumTracks(albumId, force = false)
                .onSuccess { tracks -> audioQueueFacade.playTracks(tracks) }
        }
    }

    fun playArtist(artistId: String) {
        launch {
            musicCatalogue.getArtistAlbums(artistId, limit = 50)
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

    private val fetchSemaphore = Semaphore(DEFAULT_FANOUT_PARALLELISM)

    /**
     * Parallel ([DEFAULT_FANOUT_PARALLELISM]) album-track fetch. Returns each track paired with
     * its own album fallback (the source album's name) so the facade can map
     * per-album naming before concatenation — plan 04 risk 2.
     */
    private suspend fun fetchAlbumTracksParallel(albums: List<MediaItem>): List<MusicTrackWithAlbumFallback> {
        return fetchSemaphore.mapConcurrent(albums) { album ->
            musicCatalogue.getAlbumTracks(album.id, force = false)
                .getOrNull()
                .orEmpty()
                .map { track -> MusicTrackWithAlbumFallback(track, album.name) }
        }.flatten()
    }
}
