package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

/**
 * The JVM adapter over core:data's `DownloadRepository` single — the
 * music-home transfer badge maps the repository's `getActiveDownloadCount`
 * flow verbatim; the adapter only bridges the wasm-safe seam type
 * (android/desktop behavior unchanged). Split out of the former feature-local
 * JvmMusicTrackDownloads when that seam folded onto
 * [TrackDownloadStatusWindow]; bound in DataKoinModule.
 */
internal class JvmActiveDownloadCount(
    private val downloadRepository: DownloadRepository,
) : ActiveDownloadCount {
    override fun activeDownloadCount(): Flow<Int> = downloadRepository.getActiveDownloadCount()
}
