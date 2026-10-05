package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.TrackWithAlbumFallback
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem

/**
 * Common seam over core:data's `AudioQueueFacade` —
 * the build-a-queue-then-play/enqueue pipeline nine music ViewModels share
 * (play/enqueue tracks and playlists, instant-mix starts). The facade's
 * constructor closure reaches the JVM audio pipeline (the media3-backed
 * [com.raulshma.jellyplay.core.data.playback.AudioQueueManager] impl, the
 * `Dispatchers.Main` Looper contract), so commonMain cannot name the class —
 * its result vocabulary ([AudioQueueOutcome]/[TrackWithAlbumFallback]) is
 * commonMain core:data. QuickDownloadActions
 * template: this interface carries exactly the host-facing surface, the
 * jvmShared actual delegates verbatim to the process-wide `AudioQueueFacade`
 * single (android/desktop behavior unchanged).
 */
interface MusicQueuePlayer {

    /**
     * Plays [tracks] as a fresh queue starting at [startIndex] — the
     * [AudioQueueFacade.playTracks] list overload.
     *
     * @param shuffled pre-shuffles the list before mapping (MusicHome shuffle
     *   semantics, NOT player-mode reshuffle).
     * @param albumFallback value used for [AudioQueueItem.album] when a
     *   track's own `album` is null (detail screens pass the detail item's
     *   name; screens without one pass nothing).
     * @param imageMaxWidth artwork width requested from [ImageUrlProvider].
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
     * path (MusicHomeViewModel.playAlbums/shuffleAlbums), the
     * [AudioQueueFacade.playTracks] pairs overload.
     */
    suspend fun playTracks(
        pairs: List<TrackWithAlbumFallback>,
        startIndex: Int = 0,
        shuffled: Boolean = false,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): AudioQueueOutcome

    /**
     * Appends a single [track] to the current queue — the per-track "add to
     * queue" menu action.
     */
    suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String? = null,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): AudioQueueOutcome

    /**
     * Fetches a Jellyfin instant mix seeded off [seedItemId], builds the
     * queue, and plays it at index 0. [guard] vetoes a mix that resolved
     * after navigation drift ([AudioQueueOutcome.Suppressed], silent).
     */
    suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String? = null,
        guard: () -> Boolean = { true },
    ): AudioQueueOutcome

    /** Plays playlist items as a fresh queue (imageless mapper applies). */
    suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int = 0): AudioQueueOutcome

    /** Appends a single playlist item to the current queue. */
    suspend fun enqueuePlaylistItem(item: PlaylistItem)
}
