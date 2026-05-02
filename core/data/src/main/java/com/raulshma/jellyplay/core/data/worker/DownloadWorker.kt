package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadWorkerEntryPoint {
        fun downloadDao(): DownloadDao
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java,
        )
        val dao = entryPoint.downloadDao()

        val entity = dao.getDownloadById(downloadId) ?: return Result.failure()

        if (entity.status == DownloadStatus.PAUSED.name || entity.status == DownloadStatus.CANCELLED.name) {
            return Result.success()
        }

        val existingBytes = entity.downloadedBytes
        dao.updateProgress(downloadId, existingBytes, DownloadStatus.DOWNLOADING.name)

        return try {
            val url = URL(entity.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "JellyPlay/1.0.0")

            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_PARTIAL &&
                responseCode != 206
            ) {
                if (entity.status != DownloadStatus.PAUSED.name) {
                    dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
                }
                connection.disconnect()
                return Result.failure()
            }

            val totalSize = if (existingBytes > 0) {
                val contentRange = connection.getHeaderField("Content-Range")
                if (contentRange != null) {
                    val totalPart = contentRange.substringAfter("/")
                    totalPart.toLongOrNull() ?: (existingBytes + connection.contentLengthLong.coerceAtLeast(0L))
                } else {
                    existingBytes + connection.contentLengthLong.coerceAtLeast(0L)
                }
            } else {
                connection.contentLengthLong.coerceAtLeast(0L)
            }

            if (totalSize > 0 && existingBytes == 0L) {
                dao.updateTotalSize(downloadId, totalSize)
            }

            val file = File(entity.downloadPath)
            file.parentFile?.mkdirs()

            val buffer = ByteArray(8192)
            var downloadedBytes = existingBytes

            val inputStream = if (responseCode == HttpURLConnection.HTTP_PARTIAL || responseCode == 206) {
                connection.inputStream
            } else {
                downloadedBytes = 0
                file.outputStream().close()
                connection.inputStream
            }

            inputStream.buffered().use { input ->
                val outputStream = if (responseCode == HttpURLConnection.HTTP_PARTIAL || responseCode == 206) {
                    file.appendOutputStream()
                } else {
                    file.outputStream()
                }
                outputStream.buffered().use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            dao.updateProgress(downloadId, downloadedBytes, DownloadStatus.PAUSED.name)
                            connection.disconnect()
                            return Result.success()
                        }

                        val currentEntity = dao.getDownloadById(downloadId)
                        if (currentEntity?.status == DownloadStatus.PAUSED.name || currentEntity?.status == DownloadStatus.CANCELLED.name) {
                            connection.disconnect()
                            return Result.success()
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        dao.updateProgress(downloadId, downloadedBytes, DownloadStatus.DOWNLOADING.name)
                    }
                }
            }

            connection.disconnect()

            if (totalSize > 0L && downloadedBytes < totalSize) {
                dao.updateProgress(downloadId, downloadedBytes, DownloadStatus.FAILED.name)
                return Result.failure()
            }

            dao.updateProgress(downloadId, downloadedBytes, DownloadStatus.COMPLETED.name)
            Result.success()
        } catch (e: Exception) {
            val currentEntity = dao.getDownloadById(downloadId)
            if (currentEntity?.status != DownloadStatus.PAUSED.name) {
                dao.updateProgress(downloadId, existingBytes, DownloadStatus.FAILED.name)
            }
            Result.failure()
        }
    }

    private fun File.appendOutputStream() = java.io.FileOutputStream(this, true)

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val UNIQUE_WORK_PREFIX = "download_"
    }
}
