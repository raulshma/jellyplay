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
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JellyPlayApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var userPreferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore
    @Inject lateinit var mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository
    @Inject lateinit var offlineRepository: com.raulshma.jellyplay.core.data.repository.OfflineRepository
    @Inject lateinit var audioPlaybackManager: com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
    @Inject lateinit var nowPlayingWidgetUpdater: com.raulshma.jellyplay.widget.NowPlayingWidgetUpdater
    @Inject lateinit var notificationScheduler: NotificationScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            SentryAndroid.init(this@JellyPlayApplication) { options ->
                options.dsn?.let { dsn ->
                    if (dsn.isNotBlank()) {
                        configureSentryUserContext()
                    }
                }
            }
            audioPlaybackManager.start()
            nowPlayingWidgetUpdater.start()
            com.raulshma.jellyplay.widget.WidgetWorkScheduler.enqueuePeriodic(this@JellyPlayApplication)
            recoverPendingDownloads()
            cleanupStuckDownloads()
            notificationScheduler.scheduleOrUpdate()
        }
        applicationScope.launch {
            kotlinx.coroutines.delay(10_000)
            mediaRepository.cleanupLyricsCache()
            offlineRepository.cleanupOrphans()
        }
    }

    private fun configureSentryUserContext() {
        val user = io.sentry.protocol.User().apply {
            username = "jellyplay-user"
        }
        Sentry.setUser(user)
        Sentry.setTag("player.engine", userPreferencesStore.preferences.value.preferredPlayer.name)
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

    private suspend fun cleanupStuckDownloads() {
        try {
            downloadDao.resetStuckDownloading()
            val failed = downloadDao.getFailedDownloads()
            for (download in failed) {
                if (download.downloadPath.isNotBlank()) {
                    val file = java.io.File(download.downloadPath)
                    if (file.exists() && file.length() == 0L) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private val imageClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val imageLoader by lazy {
        val cacheMb = userPreferencesStore.preferences.value.maxCacheSizeMb
        val cacheSize = if (cacheMb > 0) cacheMb * 1024L * 1024L else 256L * 1024 * 1024

        ImageLoader.Builder(this)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this@JellyPlayApplication, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(cacheSize)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader = imageLoader
}
