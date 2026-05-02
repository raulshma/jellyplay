package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    fun getAllDownloads(): Flow<List<DownloadItem>>

    suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadItem?

    suspend fun startDownload(
        mediaItemId: String,
        name: String,
        mediaType: String,
        mediaSourceId: String?,
        downloadUrl: String,
        imageUrl: String?,
    ): Result<DownloadItem>

    suspend fun cancelDownload(id: String): Result<Unit>

    suspend fun pauseDownload(id: String): Result<Unit>

    suspend fun resumeDownload(id: String): Result<Unit>

    suspend fun deleteDownload(id: String): Result<Unit>

    suspend fun retryDownload(id: String): Result<Unit>

    suspend fun getTotalDownloadedBytes(): Long
}
