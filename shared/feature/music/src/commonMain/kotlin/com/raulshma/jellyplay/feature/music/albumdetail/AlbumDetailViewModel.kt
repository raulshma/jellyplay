package com.raulshma.jellyplay.feature.music.albumdetail

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.toInstantMixOutcome
import com.raulshma.jellyplay.core.data.playback.InstantMixState
import com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.MixErrorMessage
import com.raulshma.jellyplay.feature.music.toMixErrorMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The album screen's all-or-nothing content aggregate (the detail+tracks pair). */
private data class AlbumContent(
    val detail: MediaDetail,
    val tracks: List<MediaItem>,
)

class AlbumDetailViewModel(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
) : JellyPlayViewModel() {

    /**
     * User-data changes while another screen is up (a track favorite flipped
     * elsewhere, outbox drain landing) only mark the track list stale; the
     * single silent forced reload fires when the album screen is next entered
     * (see [DeferredUserDataRefresher]) — never mid-scroll. The whole load
     * lifecycle — publish policy, back-stack re-entry guard and its
     * reload-after-failure re-arm included — lives in
     * [DeferredFetchCoordinator].
     */
    private val fetchCoordinator = DeferredFetchCoordinator<String, AlbumContent>(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        fetch = ::fetchAlbumData,
    )

    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

    private val _detail = composeState<MediaDetail?>(null)
    val detail: MediaDetail? get() = _detail.value

    // StateFlow (not composeState) so `trackDownloads` below can observe the
    // loaded track ids and scope its downloads query to them.
    private val _tracks = stateFlow<List<MediaItem>>(emptyList())
    val tracks: List<MediaItem> get() = _tracks.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    // The load half of the screen's one error field, projected from the
    // coordinator state. The mix half lives beside it: a mix error can only
    // land over successfully loaded content (the mix button is unreachable
    // from the error screen), and a load error never coexists with a mix
    // error — so the fold never has to choose.
    private val _loadError = composeState<MixErrorMessage?>(null)
    private val _mixError = composeState<MixErrorMessage?>(null)
    val error: MixErrorMessage? get() = _mixError.value ?: _loadError.value

    // Instant-mix choreography (isStarting flag + first-track one-shot +
    // outcome → error mapping) lives in the shared holder; the VM only adapts
    // the facade call to the holder's pure outcome shape and folds holder
    // errors into the screen's one `error` field (load and mix errors share
    // it, exactly as before the fold).
    private val instantMix = InstantMixStateHolder(
        scope = scope,
        startMix = { seedItemId, fallbackName ->
            audioQueueFacade.startInstantMix(seedItemId, albumFallback = fallbackName).toInstantMixOutcome()
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
                _detail.value = st.value?.detail
                _tracks.set(st.value?.tracks ?: emptyList())
                _isLoading.value = st.isLoading
                _loadError.value = st.error?.let { MixErrorMessage.Raw(it.message ?: "Failed to load album") }
            }
        }
        launch {
            instantMix.errorFlow.collect { mixError -> _mixError.value = mixError.toMixErrorMessage() }
        }
    }

    /**
     * The loud entry — the screen's `LaunchedEffect`, the error screen's
     * retry. The back-stack re-entry guard (an already-loaded album with no
     * failed loud load behind it no-ops; a failed one re-arms) lives in
     * [DeferredFetchCoordinator.load]; [force] is pull-to-refresh.
     */
    fun loadAlbum(albumId: String, force: Boolean = false) {
        fetchCoordinator.load(albumId, force)
    }

    fun refreshAlbum(albumId: String) {
        loadAlbum(albumId, force = true)
    }

    /**
     * The fetch behind both load paths, returning the whole detail+tracks
     * pair and throwing on any failed half so [DeferredFetchCoordinator]
     * owns the publish policy and failure re-arm: all-or-nothing holds for
     * BOTH modes (never one fresh half beside one stale half), and the
     * deferred silent regeneration keeps the last pair on failure
     * (serve-stale-while-revalidate, same philosophy as the detail screens).
     */
    private suspend fun fetchAlbumData(albumId: String, force: Boolean): AlbumContent {
        return coroutineScope {
            val detailDeferred = async { mediaRepository.getMediaDetail(albumId, force = force) }
            val tracksDeferred = async { mediaRepository.getAlbumTracks(albumId, force = force) }
            val detailResult = detailDeferred.await()
            val tracksResult = tracksDeferred.await()

            // The tracks error wins when both halves fail (the half whose
            // failure the screen used to surface last).
            (tracksResult.exceptionOrNull() ?: detailResult.exceptionOrNull())?.let { throw it }
            AlbumContent(
                detail = detailResult.getOrThrow(),
                tracks = tracksResult.getOrThrow(),
            )
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
        instantMix.start(albumId, detail?.item?.name)
    }

    fun consumeMixEvent() {
        instantMix.consumeStartedEvent()
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
