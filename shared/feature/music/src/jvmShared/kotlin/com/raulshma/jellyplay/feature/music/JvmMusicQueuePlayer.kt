package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.TrackWithAlbumFallback
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem

/**
 * The JVM adapter over core:data's `AudioQueueFacade` single — the music VMs'
 * play/enqueue/mix calls delegate verbatim, outcome vocabulary and all (the
 * promoted-commonMain `AudioQueueOutcome`/`TrackWithAlbumFallback` types need
 * no mapping — android/desktop behavior unchanged).
 *
 * The former JvmMusicTrackDownloads adapter (over the `DownloadRepository`
 * single) was deleted with the download-actions seam consolidation: the
 * music screens' download reads resolve from core:data's own seams now
 * (TrackDownloadStatusWindow for the album rows, ActiveDownloadCount for the
 * home badge — both bound in dataJvmModule).
 */
internal class JvmMusicQueuePlayer(
    private val facade: AudioQueueFacade,
) : MusicQueuePlayer {

    override suspend fun playTracks(
        tracks: List<MediaItem>,
        startIndex: Int,
        shuffled: Boolean,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = facade.playTracks(tracks, startIndex, shuffled, albumFallback, imageMaxWidth)

    override suspend fun playTracks(
        pairs: List<TrackWithAlbumFallback>,
        startIndex: Int,
        shuffled: Boolean,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = facade.playTracks(pairs, startIndex, shuffled, imageMaxWidth)

    override suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = facade.enqueueTrack(track, albumFallback, imageMaxWidth)

    override suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String?,
        guard: () -> Boolean,
    ): AudioQueueOutcome = facade.startInstantMix(seedItemId, albumFallback, guard)

    override suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int): AudioQueueOutcome =
        facade.playPlaylist(items, startIndex)

    override suspend fun enqueuePlaylistItem(item: PlaylistItem) {
        facade.enqueuePlaylistItem(item)
    }
}
