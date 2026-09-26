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
 * is declared, implemented and bound by core:data on both platforms. Since
 * the promoted-interface pass the JVM actual is the repository itself —
 * jvmShared `DownloadRepositoryImpl` implements this interface directly and
 * dataJvmModule binds it over the repository single.
 */
interface SeriesEpisodeDownloads {

    /** The ids of [seriesId]'s episodes with a completed local download. */
    suspend fun downloadedEpisodeIds(seriesId: String): Set<String>
}
