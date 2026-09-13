package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.TrackWithAlbumFallback
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The JVM adapters over core:data's `AudioQueueFacade` and
 * `DownloadRepository` singles — the music VMs' play/enqueue/mix calls and
 * download reads delegate verbatim; the only added motion is the 1:1 outcome
 * and fallback-pair mapping between core:data's jvmShared result vocabulary
 * and the feature-local [MusicQueueOutcome]/[MusicTrackWithAlbumFallback]
 * mirrors (android/desktop behavior unchanged).
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
    ): MusicQueueOutcome = facade
        .playTracks(tracks, startIndex, shuffled, albumFallback, imageMaxWidth)
        .toMirror()

    override suspend fun playTracks(
        pairs: List<MusicTrackWithAlbumFallback>,
        startIndex: Int,
        shuffled: Boolean,
        imageMaxWidth: Int?,
    ): MusicQueueOutcome = facade
        .playTracks(
            pairs.map { TrackWithAlbumFallback(it.track, it.albumFallback) },
            startIndex,
            shuffled,
            imageMaxWidth,
        )
        .toMirror()

    override suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): MusicQueueOutcome = facade
        .enqueueTrack(track, albumFallback, imageMaxWidth)
        .toMirror()

    override suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String?,
        guard: () -> Boolean,
    ): MusicQueueOutcome = facade
        .startInstantMix(seedItemId, albumFallback, guard)
        .toMirror()

    override suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int): MusicQueueOutcome =
        facade.playPlaylist(items, startIndex).toMirror()

    override suspend fun enqueuePlaylistItem(item: PlaylistItem) {
        facade.enqueuePlaylistItem(item)
    }
}

internal class JvmMusicTrackDownloads(
    private val downloadRepository: DownloadRepository,
) : MusicTrackDownloads {
    override val isSupported: Boolean = true
    override fun downloadsForIds(mediaItemIds: List<String>): Flow<List<DownloadItem>> =
        downloadRepository.getDownloadsByMediaItemIdsFlow(mediaItemIds)
    override suspend fun remove(downloadId: String): Result<Unit> = downloadRepository.deleteDownload(downloadId)
    override fun activeDownloadCount(): Flow<Int> = downloadRepository.getActiveDownloadCount()
}

/** Field-identical 1:1 map — see the [MusicQueueOutcome] mirror KDoc. */
private fun AudioQueueOutcome.toMirror(): MusicQueueOutcome = when (this) {
    is AudioQueueOutcome.Started -> MusicQueueOutcome.Started(queue, startIndex)
    is AudioQueueOutcome.Empty -> MusicQueueOutcome.Empty
    is AudioQueueOutcome.Suppressed -> MusicQueueOutcome.Suppressed
    is AudioQueueOutcome.Failed -> MusicQueueOutcome.Failed(cause)
}
