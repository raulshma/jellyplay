package com.raulshma.jellyplay.feature.music.artistdetail

import com.raulshma.jellyplay.core.data.playback.InstantMixState
import com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.MixErrorMessage
import com.raulshma.jellyplay.feature.music.toMixErrorMessage
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.feature.music.toInstantMixOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow

/** The artist screen's all-or-nothing content aggregate (the artist name + albums row). */
private data class ArtistContent(
    val name: String,
    val albums: List<MediaItem>,
)

class ArtistDetailViewModel(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: MusicQueuePlayer,
) : JellyPlayViewModel() {

    /**
     * User-data changes while another screen is up (a favorite flipped
     * elsewhere, outbox drain landing) only mark the artist stale; the single
     * silent forced reload fires when the artist screen is next entered (see
     * [DeferredUserDataRefresher]) — never mid-scroll. The whole load
     * lifecycle — publish policy, back-stack re-entry guard and its
     * reload-after-failure re-arm included — lives in
     * [DeferredFetchCoordinator], same chassis as the album host.
     */
    private val fetchCoordinator = DeferredFetchCoordinator<String, ArtistContent>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = ::fetchArtistData,
    )

    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

    private val _artistName = composeState("")
    val artistName: String get() = _artistName.value

    private val _albums = composeState<List<MediaItem>>(emptyList())
    val albums: List<MediaItem> get() = _albums.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    // The load half of the screen's one error field, projected from the
    // coordinator state. The mix half lives beside it: a mix error can only
    // land over successfully loaded content (the mix button is unreachable
    // from the error screen), and a load error never coexists with a mix
    // error — so the fold never has to choose (the album host's split).
    private val _loadError = composeState<MixErrorMessage?>(null)
    private val _mixError = composeState<MixErrorMessage?>(null)
    val error: MixErrorMessage? get() = _mixError.value ?: _loadError.value

    // Instant-mix choreography (isStarting flag + first-track one-shot +
    // outcome → error mapping) lives in the shared holder; the VM only adapts
    // the facade call (no album fallback — see the note in startInstantMix)
    // and folds holder errors into the screen's one `error` field.
    private val instantMix = InstantMixStateHolder(
        scope = scope,
        startMix = { seedItemId, _ ->
            audioQueueFacade.startInstantMix(seedItemId).toInstantMixOutcome()
        },
    )

    val mixState: StateFlow<InstantMixState> = instantMix.state

    val isStartingMix: Boolean get() = instantMix.state.value.isStarting
    val mixFirstTrackId: String? get() = instantMix.state.value.firstTrackId

    init {
        // The load-state projection: this screen reads plain getters backed
        // by Compose state, so the coordinator's state flow is mirrored into
        // them (spinner, content pair, load error) — mechanically, with no
        // per-host publish decisions left here.
        launch {
            fetchCoordinator.state.collect { st ->
                _artistName.value = st.value?.name ?: ""
                _albums.value = st.value?.albums ?: emptyList()
                _isLoading.value = st.isLoading
                _loadError.value = st.error?.let { MixErrorMessage.Raw(it.message ?: "Failed to load artist") }
            }
        }
        launch {
            instantMix.errorFlow.collect { mixError -> _mixError.value = mixError.toMixErrorMessage() }
        }
    }

    /**
     * The loud entry — the screen's `LaunchedEffect`, the error screen's
     * retry. The back-stack re-entry guard (an already-loaded artist with no
     * failed loud load behind it no-ops; a failed one re-arms) lives in
     * [DeferredFetchCoordinator.load]; [force] is pull-to-refresh.
     */
    fun loadArtist(artistId: String, force: Boolean = false) {
        fetchCoordinator.load(artistId, force)
    }

    fun refreshArtist(artistId: String) {
        loadArtist(artistId, force = true)
    }

    /**
     * The fetch behind both load paths, returning the whole name+albums
     * aggregate and throwing on any failed half so [DeferredFetchCoordinator]
     * owns the publish policy and failure re-arm: all-or-nothing holds for
     * BOTH modes (never one fresh half beside one stale half), and the
     * deferred silent regeneration keeps the last pair on failure
     * (serve-stale-while-revalidate, same philosophy as the album host).
     *
     * Behavior call (the ladder fold): the old ladder published a half on a
     * half-failure — the artist name without albums — but the screen never
     * rendered a partial state beside an error: its `when` collapses to
     * Loading / ErrorScreen / content, and only the detail half's failure set
     * an error (a failed albums half stayed silently empty — an artist that
     * looked to have no albums, with no retry path). All-or-nothing keeps the
     * error screen as the one failure surface and makes the albums failure
     * retryable; the detail half's error wins when both fail (the half whose
     * failure the screen used to surface). `getArtistAlbums` has no force
     * flag, so [force] reaches the detail read only, exactly as before.
     */
    private suspend fun fetchArtistData(artistId: String, force: Boolean): ArtistContent {
        return coroutineScope {
            val detailDeferred = async { mediaRepository.getMediaDetail(artistId, force = force) }
            val albumsDeferred = async { mediaRepository.getArtistAlbums(artistId) }
            val detailResult = detailDeferred.await()
            val albumsResult = albumsDeferred.await()

            // The artist-name error wins when both halves fail (the half
            // whose failure the screen used to surface).
            (detailResult.exceptionOrNull() ?: albumsResult.exceptionOrNull())?.let { throw it }
            ArtistContent(
                name = detailResult.getOrThrow().item.name,
                albums = albumsResult.getOrThrow(),
            )
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)

    fun startInstantMix(artistId: String) {
        // No album fallback: the former `track.album` fallback was a no-op
        // (the mapper keeps the track's own album whenever it is set).
        instantMix.start(artistId, fallbackName = null)
    }

    fun consumeMixEvent() {
        instantMix.consumeStartedEvent()
    }
}
