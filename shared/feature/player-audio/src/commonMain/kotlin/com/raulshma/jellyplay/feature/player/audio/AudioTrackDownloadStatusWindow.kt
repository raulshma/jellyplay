package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * The audio player's [TrackDownloadStatusWindow] adapter over this feature's
 * [AudioTrackDownloads] seam — a pure passthrough so the shared
 * TrackDownloadActions choreography can run on the player's existing
 * collaborators (no DI change; the wasm no-op actual keeps fail-closing
 * through the same seam). The player's window is the single now-playing
 * track today, but the read is honest for any id count (per-id flows
 * combined) so a bulk admission through this window can't silently misread.
 */
internal class AudioTrackDownloadStatusWindow(
    private val downloads: AudioTrackDownloads,
) : TrackDownloadStatusWindow {
    override val isSupported: Boolean get() = downloads.isSupported
    override fun downloadsFor(ids: List<String>): Flow<List<DownloadItem>> =
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ids.map { downloads.trackStatus(it) }) { rows -> rows.filterNotNull() }
        }
    override suspend fun remove(downloadId: String) {
        downloads.remove(downloadId)
    }
}
