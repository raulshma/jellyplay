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
}
