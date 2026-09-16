package com.raulshma.jellyplay.feature.music

import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * The music feature's [TrackDownloadStatusWindow] adapter over the
 * [MusicTrackDownloads] seam — a pure passthrough so the shared
 * TrackDownloadActions choreography (AlbumDetail's download flip/bulk) runs
 * on the screen's existing collaborators (no DI change; the wasm no-op
 * actual keeps fail-closing through the same seam).
 */
internal class MusicTrackDownloadStatusWindow(
    private val downloads: MusicTrackDownloads,
) : TrackDownloadStatusWindow {
    override val isSupported: Boolean get() = downloads.isSupported
    override fun downloadsFor(ids: List<String>): Flow<List<DownloadItem>> =
        downloads.downloadsForIds(ids)
    override suspend fun remove(downloadId: String) {
        downloads.remove(downloadId)
    }
}
