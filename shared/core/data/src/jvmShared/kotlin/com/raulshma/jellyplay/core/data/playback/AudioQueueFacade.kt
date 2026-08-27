package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Outcome of a facade play/enqueue operation. The "one constant" callers map
 * to their own localized message — `core/data` carries no string resources by
 * design, so `Empty` / `Failed(cause)` are the typed concept each feature
 * resolves once at the edge.
 */
/**
 * A track paired with the album name to fall back to when the track's own
 * `album` is null. Travels as one type instead of a raw `Pair` so the
 * multi-album batch path ([AudioQueueFacade.playTracks] pairs overload) can't
 * mix up the two halves — the fallback is per-track there because each track
 * belongs to a different album.
 */
data class TrackWithAlbumFallback(
    val track: MediaItem,
    val albumFallback: String?,
)

sealed interface AudioQueueOutcome {
    /**
     * Playback (or enqueue) happened. Exposes the built queue so callers can
     * keep side effects like the mix scroll-to-first-track event
     * (`queue.first().id`). `startIndex` is the index playback starts at for
     * play operations; `-1` for pure enqueues (nothing starts playing).
     */
    data class Started(val queue: List<AudioQueueItem>, val startIndex: Int) : AudioQueueOutcome

    /** Nothing to play: empty input list, or instant mix came back empty. */
    data object Empty : AudioQueueOutcome

    /** Caller's guard vetoed the start (navigation drift). Silent by design. */
    data object Suppressed : AudioQueueOutcome

    /** Mix fetch or lookup failed. Callers map to their own message. */
    data class Failed(val cause: Throwable) : AudioQueueOutcome
}

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
 * The facade holds no mutable state — every method is a straight pipeline over
 * the same [AudioPlaybackManager] singleton, so it adds no lifetime and cannot
 * reorder against other queue mutations.
 */
interface AudioQueueFacade {

    /**
     * Plays [tracks] as a fresh queue starting at [startIndex].
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
     * Plays playlist items as a fresh queue. `PlaylistItem` carries no image
     * reference, so the existing imageless mapper (`imageUrl = null`) applies.
     */
    suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int = 0): AudioQueueOutcome

    /** Appends a single playlist item to the current queue. */
    suspend fun enqueuePlaylistItem(item: PlaylistItem)
}

/**
 * Stateless adapter over the narrow [AudioQueueManager] queue interface (never
 * the 1642-line concrete manager), plus the mix fetch and image-URL provider.
 */
class DefaultAudioQueueFacade(
    private val queueManager: AudioQueueManager,
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : AudioQueueFacade {

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
        val mix = withContext(Dispatchers.IO) { mediaRepository.getInstantMix(seedItemId) }
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
