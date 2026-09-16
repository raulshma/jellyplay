package com.raulshma.jellyplay.core.data.download

/**
 * The wasmJs actual of the series-episode download read: an honest empty set.
 * Nothing was ever downloaded in this browser (there is no local download
 * pipeline), so the sheet pre-checks no rows — never fabricated episode ids.
 * Moved verbatim from feature:home's WasmSeriesEpisodeDownloads with the seam
 * consolidation; bound in dataWasmModule.
 */
internal object WasmSeriesEpisodeDownloads : SeriesEpisodeDownloads {
    override suspend fun downloadedEpisodeIds(seriesId: String): Set<String> = emptySet()
}
