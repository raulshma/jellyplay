package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.InstantMixOutcome
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

/**
 * Common seam over core:data's jvmShared `AudioQueueFacade` —
 * the build-a-queue-then-play/enqueue pipeline nine music ViewModels share
 * (play/enqueue tracks and playlists, instant-mix starts). The facade's
 * constructor closure reaches the JVM audio pipeline (the media3-backed
 * [com.raulshma.jellyplay.core.data.playback.AudioQueueManager] impl, the
 * `Dispatchers.Main` Looper contract), so commonMain cannot name the class —
 * nor the [AudioQueueOutcome]/[MusicTrackWithAlbumFallback] result vocabulary it
 * returns, which lives in the same jvmShared file. QuickDownloadActions
 * template: this interface carries exactly the host-facing surface, the
 * jvmShared actual delegates to the process-wide `AudioQueueFacade` single
 * and maps the outcome 1:1 onto the [MusicQueueOutcome] mirror
 * (android/desktop behavior unchanged).
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
    ): MusicQueueOutcome

    /**
     * Plays pre-built (track, album-fallback) pairs — the multi-album batch
     * path (MusicHomeViewModel.playAlbums/shuffleAlbums), the
     * [AudioQueueFacade.playTracks] pairs overload.
     */
    suspend fun playTracks(
        pairs: List<MusicTrackWithAlbumFallback>,
        startIndex: Int = 0,
        shuffled: Boolean = false,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): MusicQueueOutcome

    /**
     * Appends a single [track] to the current queue — the per-track "add to
     * queue" menu action.
     */
    suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String? = null,
        imageMaxWidth: Int? = ImageUrlProvider.DEFAULT_MAX_WIDTH,
    ): MusicQueueOutcome

    /**
     * Fetches a Jellyfin instant mix seeded off [seedItemId], builds the
     * queue, and plays it at index 0. [guard] vetoes a mix that resolved
     * after navigation drift ([MusicQueueOutcome.Suppressed], silent).
     */
    suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String? = null,
        guard: () -> Boolean = { true },
    ): MusicQueueOutcome

    /** Plays playlist items as a fresh queue (imageless mapper applies). */
    suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int = 0): MusicQueueOutcome

    /** Appends a single playlist item to the current queue. */
    suspend fun enqueuePlaylistItem(item: PlaylistItem)
}

/**
 * The feature-local mirror of core:data's jvmShared [AudioQueueOutcome] —
 * variants and payloads field-identical so [JvmMusicQueuePlayer]'s mapping
 * is mechanical ([Started] keeps the built queue for the mix
 * scroll-to-first-track side effect; [startIndex] is the play position, -1
 * for pure enqueues).
 */
sealed interface MusicQueueOutcome {
    /** Playback (or enqueue) happened. */
    data class Started(val queue: List<AudioQueueItem>, val startIndex: Int) : MusicQueueOutcome

    /** Nothing to play: empty input list, or instant mix came back empty. */
    data object Empty : MusicQueueOutcome

    /** Caller's guard vetoed the start (navigation drift). Silent by design. */
    data object Suppressed : MusicQueueOutcome

    /** Mix fetch or lookup failed. Callers map to their own message. */
    data class Failed(val cause: Throwable) : MusicQueueOutcome
}

/**
 * The feature-local mirror of core:data's jvmShared [MusicTrackWithAlbumFallback]
 * — a track paired with the album name to fall back to when the track's own
 * `album` is null (the multi-album batch path's per-track fallback).
 */
data class MusicTrackWithAlbumFallback(
    val track: MediaItem,
    val albumFallback: String?,
)

/**
 * Normalizes the seam outcome to the [InstantMixOutcome]'s pure shape — the
 * feature-local port of core:data's jvmShared
 * `AudioQueueOutcome.toInstantMixOutcome()` (same fold: [MusicQueueOutcome.Started]
 * keeps the queue head for the one-shot navigation, null on an empty queue),
 * so the album/artist mix-start call sites read identically to before.
 */
fun MusicQueueOutcome.toInstantMixOutcome(): InstantMixOutcome = when (this) {
    is MusicQueueOutcome.Started -> InstantMixOutcome.Started(queue.firstOrNull()?.id)
    MusicQueueOutcome.Empty -> InstantMixOutcome.EmptyMix
    MusicQueueOutcome.Suppressed -> InstantMixOutcome.Suppressed
    is MusicQueueOutcome.Failed -> InstantMixOutcome.Failed(cause)
}

