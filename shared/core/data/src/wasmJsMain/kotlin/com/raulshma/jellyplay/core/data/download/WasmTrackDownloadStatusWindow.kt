package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The wasmJs actual of the track-download status window: an honest no-op. The
 * browser has no local download pipeline, so [TrackDownloadStatusWindow.isSupported]
 * is false — hosts hide the download CTAs — while `downloadsFor` stays an
 * empty list and [remove] is inert. Consolidates the former feature-local
 * WasmAudioTrackDownloads / WasmMusicTrackDownloads stubs (same empty read,
 * same inert remove); bound in dataWasmModule.
 */
internal object WasmTrackDownloadStatusWindow : TrackDownloadStatusWindow {
    override val isSupported: Boolean = false
    override fun downloadsFor(ids: List<String>): Flow<List<DownloadItem>> = flowOf(emptyList())
    override suspend fun remove(downloadId: String) = Unit
}
