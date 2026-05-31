package com.raulshma.jellyplay

import android.app.Application
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.size.Size
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.model.DownloadStatus
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JellyPlayApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var downloadDao: DownloadDao

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { recoverPendingDownloads() }
    }

    private suspend fun recoverPendingDownloads() {
        try {
            val pending = downloadDao.getDownloadsByStatus(DownloadStatus.PENDING.name)
            for (download in pending) {
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(DownloadWorker.KEY_DOWNLOAD_ID, download.id)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(this).enqueueUniqueWork(
                    "${DownloadWorker.UNIQUE_WORK_PREFIX}${download.id}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest,
                )
            }
            val stale = downloadDao.getDownloadsByStatus(DownloadStatus.DOWNLOADING.name)
            for (download in stale) {
                downloadDao.updateProgress(download.id, download.downloadedBytes, DownloadStatus.PENDING.name)
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(DownloadWorker.KEY_DOWNLOAD_ID, download.id)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(this).enqueueUniqueWork(
                    "${DownloadWorker.UNIQUE_WORK_PREFIX}${download.id}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest,
                )
            }
        } catch (_: Exception) {
        }
    }

    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this@JellyPlayApplication, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader = imageLoader
}
