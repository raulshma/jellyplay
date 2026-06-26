package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    fun getAllDownloads(): Flow<List<DownloadItem>>

    fun getDownloadByMediaItemIdFlow(mediaItemId: String): Flow<DownloadItem?>

    fun getActiveDownloadCount(): Flow<Int>

    suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadItem?

    suspend fun startDownload(
        mediaItemId: String,
        name: String,
        mediaType: String,
        mediaSourceId: String?,
        downloadUrl: String,
        imageUrl: String?,
        imageBlurHash: String? = null,
        seriesId: String? = null,
        seasonId: String? = null,
        seriesName: String? = null,
        seasonName: String? = null,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
    ): Result<DownloadItem>

    suspend fun cancelDownload(id: String): Result<Unit>

    suspend fun pauseDownload(id: String): Result<Unit>

    suspend fun resumeDownload(id: String): Result<Unit>

    suspend fun deleteDownload(id: String): Result<Unit>

    suspend fun retryDownload(id: String): Result<Unit>

    suspend fun getTotalDownloadedBytes(): Long

    suspend fun saveOfflineMediaItem(item: com.raulshma.jellyplay.core.model.MediaItem, imageUrl: String?, backdropUrl: String?)

    suspend fun downloadSeries(
        seriesId: String,
        episodeIds: Map<String, List<String>>? = null,
    ): Result<List<String>>

    suspend fun getDownloadedEpisodeIdsForSeries(seriesId: String): Set<String>

    suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        downloadPath: String,
    )

    /**
     * Downloads every external/deliverable subtitle stream in [mediaStreams] for
     * offline playback, storing them under `<video-dir>/subtitles/` alongside a
     * [com.raulshma.jellyplay.core.model.OfflineSubtitleManifest]. Failures for
     * individual streams are tolerated (best-effort) and never abort the download.
     */
    suspend fun downloadExternalSubtitles(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<com.raulshma.jellyplay.core.model.MediaStream>,
        downloadPath: String,
    )

    /**
     * Fetches media segments (intro/outro/recap/…) for [itemId] and persists them
     * to `<video-dir>/segments.json` so skip controls work for offline playback.
     */
    suspend fun downloadMediaSegments(itemId: String, downloadPath: String)

    /** Returns the locally-cached subtitle manifest for a downloaded item, if any. */
    suspend fun loadLocalSubtitleManifest(downloadPath: String): com.raulshma.jellyplay.core.model.OfflineSubtitleManifest?

    /** Returns locally-cached media segments for a downloaded item, if any. */
    suspend fun loadLocalSegments(itemId: String): List<com.raulshma.jellyplay.core.model.MediaSegment>?

    fun enqueueDownload(downloadId: String)

    suspend fun setDownloadPriority(id: String, priority: Int): Result<Unit>
}
