package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository

/**
 * The JVM adapter over core:data's `DownloadRepository` single — the series
 * download sheet's episode-id read maps the repository's
 * `getDownloadedEpisodeIdsForSeries` verbatim; the adapter only bridges the
 * wasm-safe seam type (android/desktop behavior unchanged). Moved from
 * feature:home's JvmSeriesEpisodeDownloads with the seam consolidation; bound
 * in DataKoinModule next to the repository single it wraps.
 */
internal class JvmSeriesEpisodeDownloads(
    private val downloadRepository: DownloadRepository,
) : SeriesEpisodeDownloads {
    override suspend fun downloadedEpisodeIds(seriesId: String): Set<String> =
        downloadRepository.getDownloadedEpisodeIdsForSeries(seriesId)
}
