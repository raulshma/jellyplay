package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The wasmJs actual of the [AudioTrackDownloads] seam: an honest no-op. The
 * browser has no local download pipeline, so [AudioTrackDownloads.isSupported]
 * is false — the screen hides the download CTA — while the status flow stays
 * empty and [remove] is inert.
 */
internal object WasmAudioTrackDownloads : AudioTrackDownloads {
    override val isSupported: Boolean = false
    override fun trackStatus(itemId: String): Flow<DownloadItem?> = emptyFlow()
    override suspend fun remove(downloadId: String): Result<Unit> = Result.success(Unit)
}
