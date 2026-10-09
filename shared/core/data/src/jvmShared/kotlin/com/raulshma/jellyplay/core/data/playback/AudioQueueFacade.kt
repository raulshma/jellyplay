package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * The single seam for "build a queue of [AudioQueueItem]s, then play or
 * enqueue it, optionally seeding it from an instant mix" (plan 04).
 *
 * Owns the five concerns every former call site re-decided: image-URL
 * resolution (via [ImageUrlProvider], width explicit per call site), album
 * fallback naming (explicit per call site), the dispatcher hop (heavy queue
 * construction on `Dispatchers.Default`, the [AudioQueueManager] mutation on
 * `Dispatchers.Main`), instant-mix fetching, and the
 * `playQueue`-vs-`addToQueue` transport choice.
 *
 * **Threading contract.** [AudioQueueManager] methods must run on the
 * application main thread (ExoPlayer's Looper contract, enforced by an
 * always-on `assertMainThread` check). Callers may invoke the facade from any
 * dispatcher — the facade hops: fetch on IO, construction on Default, the
 * queue mutation on Main. This is the structural fix for the former
 * `Dispatchers.Default` → `playQueue` violations in `DetailViewModel.playAlbum`
 * and `InstantMixActions.startInstantMix`.
 *
 * The facade holds no mutable queue state — every play/enqueue method is a
 * straight pipeline over the same [AudioPlaybackManager] singleton, so it adds
 * no lifetime and cannot reorder against other queue mutations. The one
 * stateful adjunct is the lazily-created [AudioRadioController] (endless
 * radio), which observes the queue through the manager's flows and appends
 * via [enqueueTracks] — it introduces no new queue-mutation path.
 */
interface AudioQueueFacade {

    /**
     * Plays [tracks] as a fresh queue starting at [startIndex].
     *
     * A fresh queue implicitly disarms any live endless radio (see
     * [stopRadio]) — only [startRadio] leaves one armed.
     *
     * @param shuffled pre-shuffles the list (`List.shuffled()`) before mapping
     *   — the "pre-shuffled list" MusicHome shuffle semantics, NOT the
     *   player-mode reshuffle of [AudioQueueManager.setShuffleMode].
     * @param albumFallback value used for [AudioQueueItem.album] when a
     *   track's own `album` is null (per-call-site fact: album/artist detail
     *   screens pass the detail item's name; screens without one pass nothing).
     * @param imageMaxWidth artwork width requested from [ImageUrlProvider]
     *   (`DEFAULT_MAX_WIDTH` for detail surfaces, `MUSIC_MAX_WIDTH` for
     *   dense music lists).
     */
    suspend fun playTracks(
        tracks: List<MediaItem>,
        startIndex: Int = 0,
        shuffled: Boolean = false,
        albumFallback: String? = null,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): AudioQueueOutcome

    /**
     * Plays pre-built (track, album-fallback) pairs — the multi-album batch
     * path (`MusicHomeViewModel.playAlbums` / `shuffleAlbums`) where the
     * fallback varies per track because each track belongs to a different
     * album. The flat list is played as ONE queue, reproducing the former
     * concatenated one-shot `playQueue` ordering exactly.
     */
    suspend fun playTracks(
        pairs: List<TrackWithAlbumFallback>,
        startIndex: Int = 0,
        shuffled: Boolean = false,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): AudioQueueOutcome

    /**
     * Appends [tracks] to the current queue (no playback position change).
     * Same mapping parameters as [playTracks].
     */
    suspend fun enqueueTracks(
        tracks: List<MediaItem>,
        albumFallback: String? = null,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): AudioQueueOutcome

    /**
     * Appends a single [track] to the current queue. Convenience for the
     * per-track "add to queue" menu action the list screens expose — the
     * single-item case of [enqueueTracks] without the `listOf()` wrapper.
     */
    suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String? = null,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): AudioQueueOutcome

    /**
     * Fetches a Jellyfin instant mix seeded off [seedItemId], builds the
     * queue, and plays it at index 0.
     *
     * @param guard runs on the main thread before the mutation so callers can
     *   veto a mix that resolved after navigation drift (the former
     *   `InstantMixActions` behavior). A `false` return yields
     *   [AudioQueueOutcome.Suppressed] with no playback and no message.
     */
    suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String? = null,
        guard: () -> Boolean = { true },
    ): AudioQueueOutcome

    /**
     * Starts an endless radio: plays an instant mix seeded off [seedItemId]
     * and keeps refilling the queue as it drains (the [AudioRadioController]
     * refill rule) until [stopRadio]. Same outcome vocabulary as
     * [startInstantMix]; the radio arms only on a [AudioQueueOutcome.Started].
     */
    suspend fun startRadio(
        seedItemId: String,
        albumFallback: String? = null,
        guard: () -> Boolean = { true },
    ): AudioQueueOutcome

    /**
     * Deactivates the radio (queue + playback keep playing as they are).
     * Every fresh-queue play method disarms the radio implicitly too — a
     * radio never survives into a queue it didn't seed.
     */
    fun stopRadio()

    /** UI-visible radio status (active/seed/refilling) for now-playing surfaces. */
    val radioState: StateFlow<RadioState>

    /**
     * Plays playlist items as a fresh queue (disarming any live radio, like
     * [playTracks]). `PlaylistItem` carries no image reference, so the
     * existing imageless mapper (`imageUrl = null`) applies.
     */
    suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int = 0): AudioQueueOutcome

    /** Appends a single playlist item to the current queue. */
    suspend fun enqueuePlaylistItem(item: PlaylistItem)
}

/**
 * Stateless adapter over the narrow [AudioQueueManager] queue interface (never
 * the 1642-line concrete manager), plus the mix fetch and image-URL provider.
 * The mix fetch rides the [LibraryApiClient] catalogue family (the
 * getInstantMix read — the only client member this facade touches).
 */
class DefaultAudioQueueFacade(
    private val queueManager: AudioQueueManager,
    private val libraryApiClient: LibraryApiClient,
    private val imageUrlProvider: ImageUrlProvider,
    /** Application-lifetime scope backing the radio observer (singleton scope). */
    private val radioScope: CoroutineScope,
) : AudioQueueFacade {

    /**
     * The radio state machine, created lazily — in practice on the first
     * [radioState] collection (the queue sheet's radio chip is always
     * composed), which arms the queue observer; a facade whose radio state is
     * never collected registers no observer. The scope is the injected
     * application scope — the observer only reads flows; the enqueue lambda
     * owns the main-thread hop.
     */
    private val radio: AudioRadioController by lazy {
        AudioRadioController(
            scope = radioScope,
            queueFlow = queueManager.queue,
            currentIndexFlow = queueManager.currentIndex,
            fetchMix = { seed -> withContext(Dispatchers.IO) { libraryApiClient.getInstantMix(seed, limit = 100) } },
            enqueue = { tracks -> enqueueTracks(tracks) },
        )
    }

    /**
     * True only while a radio [radio.start] armed is still live, so
     * [stopRadio] and the fresh-queue play paths can deactivate it without
     * forcing the lazy [radio] (and its queue observer) into existence for
     * radio-free sessions. Set wherever the controller is armed; cleared
     * under every disarm path.
     */
    @Volatile
    private var radioArmed = false

    override val radioState: StateFlow<RadioState>
        get() = radio.state

    override suspend fun startRadio(
        seedItemId: String,
        albumFallback: String?,
        guard: () -> Boolean,
    ): AudioQueueOutcome {
        val outcome = startInstantMix(seedItemId, albumFallback, guard)
        if (outcome is AudioQueueOutcome.Started) {
            radioArmed = true
            radio.start(seedItemId)
        }
        return outcome
    }

    override fun stopRadio() {
        stopRadioIfArmed()
    }

    override suspend fun playTracks(
        tracks: List<MediaItem>,
        startIndex: Int,
        shuffled: Boolean,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = playItems(
        // The fallback is uniform across the batch; pair it per track once.
        source = if (shuffled) tracks.shuffled() else tracks,
        startIndex = startIndex,
    ) { it.toQueueItem(albumFallback, imageMaxWidth) }

    override suspend fun playTracks(
        pairs: List<TrackWithAlbumFallback>,
        startIndex: Int,
        shuffled: Boolean,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = playItems(
        source = if (shuffled) pairs.shuffled() else pairs,
        startIndex = startIndex,
    ) { (track, fallback) -> track.toQueueItem(fallback, imageMaxWidth) }

    override suspend fun enqueueTracks(
        tracks: List<MediaItem>,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome {
        if (tracks.isEmpty()) return AudioQueueOutcome.Empty
        val items = withContext(Dispatchers.Default) {
            tracks.map { it.toQueueItem(albumFallback, imageMaxWidth) }
        }
        return withContext(Dispatchers.Main) {
            queueManager.addToQueueAll(items)
            AudioQueueOutcome.Started(items, startIndex = -1)
        }
    }

    override suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome {
        val item = withContext(Dispatchers.Default) { track.toQueueItem(albumFallback, imageMaxWidth) }
        return withContext(Dispatchers.Main) {
            queueManager.addToQueue(item)
            AudioQueueOutcome.Started(listOf(item), startIndex = -1)
        }
    }

    override suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String?,
        guard: () -> Boolean,
    ): AudioQueueOutcome {
        val mix = withContext(Dispatchers.IO) { libraryApiClient.getInstantMix(seedItemId, limit = 100) }
        return mix.fold(
            onSuccess = { tracks ->
                when {
                    tracks.isEmpty() -> AudioQueueOutcome.Empty
                    withContext(Dispatchers.Main) { !guard() } -> AudioQueueOutcome.Suppressed
                    else -> playTracks(tracks, startIndex = 0, albumFallback = albumFallback)
                }
            },
            onFailure = AudioQueueOutcome::Failed,
        )
    }

    override suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int): AudioQueueOutcome {
        if (items.isEmpty()) return AudioQueueOutcome.Empty
        stopRadioIfArmed()
        val queueItems = withContext(Dispatchers.Default) { items.map { it.toAudioQueueItem() } }
        return withContext(Dispatchers.Main) {
            queueManager.playQueue(queueItems, startIndex)
            AudioQueueOutcome.Started(queueItems, startIndex)
        }
    }

    override suspend fun enqueuePlaylistItem(item: PlaylistItem) {
        withContext(Dispatchers.Main) { queueManager.addToQueue(item.toAudioQueueItem()) }
    }

    /**
     * A fresh queue replaces whatever was playing, so a live radio's old seed
     * must not keep refilling it ([startRadio] re-arms on its own Started).
     * Runs before the queue mutation so an in-flight refill's pre-append
     * claim check sees an inactive radio against the new queue.
     */
    private fun stopRadioIfArmed() {
        if (radioArmed) {
            radioArmed = false
            radio.stop()
        }
    }

    /**
     * Shared play pipeline: shuffle already applied by the caller (so the
     * permutation happens over the caller's list shape), heavy mapping on
     * `Dispatchers.Default`, the `playQueue` mutation on `Dispatchers.Main`.
     */
    private suspend fun <T> playItems(
        source: List<T>,
        startIndex: Int,
        mapper: (T) -> AudioQueueItem,
    ): AudioQueueOutcome {
        if (source.isEmpty()) return AudioQueueOutcome.Empty
        stopRadioIfArmed()
        val items = withContext(Dispatchers.Default) { source.map(mapper) }
        return withContext(Dispatchers.Main) {
            queueManager.playQueue(items, startIndex)
            AudioQueueOutcome.Started(items, startIndex)
        }
    }

    /** Resolves the artwork URL at [imageMaxWidth] and applies [albumFallback]. */
    private fun MediaItem.toQueueItem(albumFallback: String?, imageMaxWidth: Int?): AudioQueueItem =
        toAudioQueueItem(
            imageUrl = imageUrlProvider.getImageUrl(id, maxWidth = imageMaxWidth),
            albumFallback = albumFallback,
        )
}
