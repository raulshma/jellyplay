package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * The JVM adapter over core:data's `DownloadRepository` single — the audio
 * player's status read + delete consume the repository verbatim; the adapter
 * only bridges the wasm-safe seam type (android/desktop behavior unchanged).
 */
internal class JvmAudioTrackDownloads(
    private val downloadRepository: DownloadRepository,
) : AudioTrackDownloads {
    override val isSupported: Boolean = true
    override fun trackStatus(itemId: String): Flow<DownloadItem?> =
        downloadRepository.getDownloadByMediaItemIdFlow(itemId)
    override suspend fun remove(downloadId: String): Result<Unit> = downloadRepository.deleteDownload(downloadId)
}
