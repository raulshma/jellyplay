package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The wasmJs actuals of the music web seams: honest unsupported
 * platform.
 *
 *  - [WasmMusicQueuePlayer]: the browser has no audio playback pipeline (the
 *    media3-backed queue manager is JVM-only and the web stack registers no
 *    player bindings), so every start/mix/queue-play fails with an explicit
 *    cause — never a fabricated [MusicQueueOutcome.Started] — while the
 *    inert enqueue drops silently. A user-visible message only surfaces if a
 *    web shell ever binds this object and routes music; today nothing does
 *    (web wiring stays with the orchestrator's shared-wiring pass).
 *
 * The former WasmMusicTrackDownloads no-op stub was deleted with the
 * download-actions seam consolidation: the music screens' download reads
 * resolve from core:data's own seams now (TrackDownloadStatusWindow for the
 * album rows, ActiveDownloadCount for the home badge — their honest web
 * no-op actuals are core:data's, bound in dataWasmModule).
 */
internal object WasmMusicQueuePlayer : MusicQueuePlayer {
    private val unsupported: MusicQueueOutcome =
        MusicQueueOutcome.Failed(UnsupportedOperationException("Music playback requires the Android or desktop app"))

    override suspend fun playTracks(
        tracks: List<MediaItem>,
        startIndex: Int,
        shuffled: Boolean,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): MusicQueueOutcome = unsupported

    override suspend fun playTracks(
        pairs: List<MusicTrackWithAlbumFallback>,
        startIndex: Int,
        shuffled: Boolean,
        imageMaxWidth: Int?,
    ): MusicQueueOutcome = unsupported

    override suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): MusicQueueOutcome = unsupported

    override suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String?,
        guard: () -> Boolean,
    ): MusicQueueOutcome = unsupported

    override suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int): MusicQueueOutcome = unsupported

    override suspend fun enqueuePlaylistItem(item: PlaylistItem) {
        // Inert: nothing to append to (see the seam KDoc).
    }
}

