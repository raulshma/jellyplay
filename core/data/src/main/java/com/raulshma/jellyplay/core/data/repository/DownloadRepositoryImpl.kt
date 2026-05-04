package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.os.Environment
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { entities ->
            entities.map { it.toDownloadItem() }
        }

    override suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadItem? =
        downloadDao.getDownloadByMediaItemId(mediaItemId)?.toDownloadItem()

    override suspend fun startDownload(
        mediaItemId: String,
        name: String,
        mediaType: String,
        mediaSourceId: String?,
        downloadUrl: String,
        imageUrl: String?,
        imageBlurHash: String?,
    ): Result<DownloadItem> = runCatching {
        val existing = downloadDao.getDownloadByMediaItemId(mediaItemId)
        if (existing != null && existing.status != DownloadStatus.FAILED.name && existing.status != DownloadStatus.CANCELLED.name) {
            return@runCatching existing.toDownloadItem()
        }

        val id = UUID.randomUUID().toString()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        val safeName = name.replace(Regex("[^a-zA-Z0-9.\\-]"), "_")
        val extension = if (mediaType == MediaType.AUDIO.name) "mp3" else "mp4"
        val filePath = File(dir, "${safeName}_${id.take(8)}.$extension").absolutePath

        val entity = DownloadEntity(
            id = id,
            mediaItemId = mediaItemId,
            name = name,
            mediaType = mediaType,
            downloadPath = filePath,
            downloadUrl = downloadUrl,
            totalSizeBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.PENDING.name,
            mediaSourceId = mediaSourceId,
            imageUrl = imageUrl,
            imageBlurHash = imageBlurHash,
        )
        downloadDao.insertDownload(entity)
        entity.toDownloadItem()
    }

    override suspend fun cancelDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.CANCELLED.name)
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (entity.status == DownloadStatus.DOWNLOADING.name || entity.status == DownloadStatus.PENDING.name) {
            downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.PAUSED.name)
        }
    }

    override suspend fun resumeDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (entity.status == DownloadStatus.PAUSED.name || entity.status == DownloadStatus.FAILED.name) {
            downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.PENDING.name)
        }
    }

    override suspend fun deleteDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        val file = File(entity.downloadPath)
        if (file.exists()) file.delete()
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun retryDownload(id: String): Result<Unit> = runCatching {
        downloadDao.updateProgress(id, 0L, DownloadStatus.PENDING.name)
    }

    override suspend fun getTotalDownloadedBytes(): Long =
        downloadDao.getTotalDownloadedBytes()

    private fun DownloadEntity.toDownloadItem() = DownloadItem(
        id = id,
        mediaItemId = mediaItemId,
        name = name,
        mediaType = try { MediaType.valueOf(mediaType) } catch (_: Exception) { MediaType.UNKNOWN },
        downloadPath = downloadPath,
        downloadUrl = downloadUrl,
        totalSizeBytes = totalSizeBytes,
        downloadedBytes = downloadedBytes,
        status = try { DownloadStatus.valueOf(status) } catch (_: Exception) { DownloadStatus.FAILED },
        mediaSourceId = mediaSourceId,
        imageUrl = imageUrl,
        imageBlurHash = imageBlurHash,
    )
}
