package com.raulshma.jellyplay.core.data.download

/**
 * The single read the series download sheet makes: which episodes of a series
 * already carry a completed local download, so the sheet pre-checks its rows.
 * A genuinely different read from [QuickDownloadActions] (item-scoped quick
 * actions over [MediaDownloadActions]) and from [TrackDownloadStatusWindow]
 * (row windows over a host's track list) — it is scoped by a PARENT series id
 * and returns episode ids, not download rows, which is why it did not fold
 * onto either seam when the download-actions seams were consolidated.
 *
 * The natural source — core:data's jvmShared `DownloadRepository`
 * ([`getDownloadedEpisodeIdsForSeries`][com.raulshma.jellyplay.core.data.repository.DownloadRepository.getDownloadedEpisodeIdsForSeries])
 * — is invisible to feature commonMain (its constructor closure is the JVM
 * download engine), which is exactly why this interface exists. Moved from
 * feature:home (where it shipped beside the now-deleted HomeDownloadActions
 * twin that DID fold onto [QuickDownloadActions]) so the wall-crossing seam
 * is declared, implemented and bound by core:data on both platforms.
 *
 * The JVM actual — [JvmSeriesEpisodeDownloads] in jvmShared, bound in
 * DataKoinModule — maps the repository read verbatim (android/desktop
 * behavior unchanged). The wasmJs actual — [WasmSeriesEpisodeDownloads] in
 * wasmJsMain, bound in dataWasmModule — is an honest empty read: nothing was
 * ever downloaded in this browser, so it never fabricates episode ids.
 */
interface SeriesEpisodeDownloads {

    /** The ids of [seriesId]'s episodes with a completed local download. */
    suspend fun downloadedEpisodeIds(seriesId: String): Set<String>
}
