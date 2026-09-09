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

class AlbumDetailViewModel(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val audioQueueFacade: AudioQueueFacade,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
) : JellyPlayViewModel() {

    private val _detail = composeState<MediaDetail?>(null)
    val detail: MediaDetail? get() = _detail.value

    /** The loaded album — the deferred refresh reloads it silently. */
    private var currentAlbumId: String? = null

    /**
     * User-data changes while another screen is up (a track favorite flipped
     * elsewhere, outbox drain landing) only mark the track list stale; the
     * single silent forced reload fires when the album screen is next entered
     * (see [DeferredUserDataRefresher]) — never mid-scroll. The single-flight
     * load slot and the skip/re-arm choreography live in
     * [DeferredFetchCoordinator].
     */
    private val fetchCoordinator = DeferredFetchCoordinator(
        userDataChanges = mediaRepository.userDataChanges,
        scope = scope,
        silentFetch = {
            currentAlbumId?.let { fetchAlbumData(it, force = true, silent = true) } ?: true
        },
        onFetchError = { e, silent ->
            // Same contract as a `false` from a loud [fetchAlbumData]: a
            // thrown repo path must not strand the spinner published there —
            // clear it, surface the error, and arm the re-entry reload
            // (loud only; silent keeps the last detail+tracks pair).
            if (!silent) {
                _error.value = MixErrorMessage.Raw(e.message ?: "Failed to load album")
                needsLoudReloadOnReentry = true
                _isLoading.value = false
            }
        },
    )

    val deferredRefresher: DeferredUserDataRefresher get() = fetchCoordinator.deferredRefresher

    // StateFlow (not composeState) so `trackDownloads` below can observe the
    // loaded track ids and scope its downloads query to them.
    private val _tracks = stateFlow<List<MediaItem>>(emptyList())
    val tracks: List<MediaItem> get() = _tracks.value

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<MixErrorMessage?>(null)
    val error: MixErrorMessage? get() = _error.value

    // _error is shared with instant-mix failures, but the re-entry guard
    // below must only react to LOAD failures — a failed mix must not make
    // re-entering the album flash a loud reload over loaded content.
    private var needsLoudReloadOnReentry = false

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
        launch {
            instantMix.errorFlow.collect { mixError -> _error.value = mixError.toMixErrorMessage() }
        }
    }

    /**
     * Skips an already-loaded album unless [force]: back-stack re-entry re-runs
     * the screen's `LaunchedEffect`, and a second loud load there would race
     * the deferred refresh's silent regeneration. An album whose loud load
     * failed (or a fresh VM) loads; a failed instant mix does not count —
     * its error shares [_error] but the content stays loaded.
     */
    fun loadAlbum(albumId: String, force: Boolean = false) {
        if (!force && currentAlbumId == albumId && _detail.value != null && !needsLoudReloadOnReentry) return
        currentAlbumId = albumId
        fetchCoordinator.load { fetchAlbumData(albumId, force = force, silent = false) }
    }

    fun refreshAlbum(albumId: String) {
        loadAlbum(albumId, force = true)
    }

    /**
     * The fetch behind both load paths, reporting plain success so
     * [DeferredFetchCoordinator] owns the failure re-arm. [silent] serves
     * the deferred-refresh path: no loading state, no error reset, and an
     * all-or-nothing publish — a failed half keeps the LAST detail+tracks
     * PAIR on screen (never one fresh half beside one stale half)
     * (serve-stale-while-revalidate, same philosophy as the detail screens).
     */
    private suspend fun fetchAlbumData(albumId: String, force: Boolean, silent: Boolean): Boolean {
        if (!silent) {
            _isLoading.value = true
            _error.value = null
        }
        val ok = coroutineScope {
            val detailDeferred = async { mediaRepository.getMediaDetail(albumId, force = force) }
            val tracksDeferred = async { mediaRepository.getAlbumTracks(albumId, force = force) }
            val detailResult = detailDeferred.await()
            val tracksResult = tracksDeferred.await()
            if (silent) {
                val detail = detailResult.getOrNull()
                val tracks = tracksResult.getOrNull()
                if (detail != null && tracks != null) {
                    _detail.value = detail
                    _tracks.set(tracks)
                    // A silent success heals a failed loud load: clear its error
                    // (never a mix error — the mix button is unreachable from
                    // the error screen) and the guard flag, or re-entry would
                    // flash-reload healed content.
                    if (needsLoudReloadOnReentry) {
                        needsLoudReloadOnReentry = false
                        _error.value = null
                    }
                    true
                } else {
                    false
                }
            } else {
                detailResult
                    .onSuccess { _detail.value = it }
                    .onFailure { _error.value = MixErrorMessage.Raw(it.message ?: "Failed to load album") }
                tracksResult
                    .onSuccess { _tracks.set(it) }
                    .onFailure { _error.value = MixErrorMessage.Raw(it.message ?: "Failed to load tracks") }
                needsLoudReloadOnReentry = !(detailResult.isSuccess && tracksResult.isSuccess)
                detailResult.isSuccess && tracksResult.isSuccess
            }
        }
        if (!silent) {
            _isLoading.value = false
        }
        return ok
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
