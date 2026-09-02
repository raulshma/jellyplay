package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.DownloadStatus
import java.io.File

/**
 * The single owner of the download-vs-stream fork. See [PlaybackSourceResolver]
 * for the module contract and the disk-staleness / fallback semantics.
 *
 * Moved from the legacy `:core:data` shim (playback-flips wave): the sole
 * Android coupling (`android.net.Uri.fromFile`) became `File.toURI()`, so the
 * impl is platform-free and Koin-owned ([dataJvmModule][com.raulshma.jellyplay.core.data.di.dataJvmModule]
 * constructs it; the legacy DataModule bridges Hilt injectors to the single).
 *
 * ## Local URI shape delta
 *
 * `File.toURI().toString()` emits `file:/abs/path` (single slash) where
 * `Uri.fromFile` emitted `file:///abs/path`. Both forms are valid RFC 8089
 * file URIs and every consumer accepts them: `android.net.Uri.parse` and
 * media3's `MediaItem.Builder().setUri(String)` (the audio trio
 * `AudioCrossfader` / `AudioLibraryBrowser` / `AudioPlaybackManager`) parse
 * either. Desktop caveat for the future: `MpvDesktopEngine` mis-parses
 * single-slash `file:/C:/...` URIs on Windows — today's desktop consumers
 * only call [resolveUsableDownload] (which returns the raw [com.raulshma.jellyplay.core.model.DownloadItem],
 * never `Local.uri`), so nothing desktop-side reads the URI yet; a desktop
 * consumer of [resolveLocalSource] must normalize the URI before handing it
 * to mpv.
 */
class PlaybackSourceResolverImpl(
    private val downloadRepository: DownloadRepository,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val offlineRepository: OfflineRepository,
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
) : PlaybackSourceResolver {

    override suspend fun resolvePlaybackSource(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): ResolvedPlaybackSource? {
        // Local-first: a completed download with an existing file short-circuits
        // the server round-trip. Preserves MainViewModel's disk-staleness
        // behaviour — resolveUsableDownload returns null when the file is gone,
        // so we transparently fall through to the stream path below.
        resolveLocalSource(itemId)?.let { return it }

        val detail = mediaRepository.getMediaDetail(itemId).getOrNull() ?: return null
        val source = if (mediaSourceId != null) {
            detail.mediaSources.find { it.id == mediaSourceId }
        } else {
            detail.mediaSources.firstOrNull()
        }
        val sourceId = source?.id ?: ""
        val url = playbackRepository.getStreamUrl(itemId, sourceId, startPositionTicks)
        return ResolvedPlaybackSource.Stream(
            itemId = itemId,
            url = url,
            title = detail.item.name,
            mediaSourceId = source?.id,
        )
    }

    override suspend fun resolveUsableDownload(itemId: String): com.raulshma.jellyplay.core.model.DownloadItem? =
        downloadRepository.getDownloadByMediaItemId(itemId)?.takeIf { download ->
            download.status == DownloadStatus.COMPLETED && File(download.downloadPath).exists()
        }

    override suspend fun resolveLocalSource(itemId: String): ResolvedPlaybackSource.Local? {
        val download = resolveUsableDownload(itemId) ?: return null
        val file = File(download.downloadPath)
        val offlineItem = offlineRepository.getOfflineItem(itemId)
        val title = offlineItem?.name ?: download.name
        return ResolvedPlaybackSource.Local(
            itemId = itemId,
            filePath = download.downloadPath,
            // file:/ single-slash form — see the class KDoc's URI-shape note.
            uri = file.toURI().toString(),
            title = title,
            download = download,
            offlineItem = offlineItem,
        )
    }

    override suspend fun resolveStartPositionTicks(itemId: String, explicitTicks: Long): Long {
        // explicit > 0 wins; the offline resume lookup only fires for the
        // offline entry points that navigate with startPositionTicks = 0.
        if (explicitTicks != 0L) return explicitTicks
        return offlinePlaybackFacade.getResumePositionTicks(itemId)
    }
}
