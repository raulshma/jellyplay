package com.raulshma.jellyplay

import android.app.Application
import android.content.Context
import android.util.Log
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

    companion object {
        private const val TAG = "JellyPlayApp"
    }

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
        SentryAndroid.init(this) { options ->
            // Strip query strings from HTTP breadcrumb URLs that may carry the
            // Jellyfin access token (e.g. ".../stream?api_key=…"). The previous
            // implementation was case-sensitive and only inspected the "url"
            // data key, missing "ApiKey" variants and breadcrumbs whose message
            // contained a logged request line.
            options.setBeforeBreadcrumb { breadcrumb, _ ->
                val data = breadcrumb.data
                val url = data["url"] as? String
                if (url != null && url.contains("?")) {
                    val query = url.substringAfter("?")
                    // Match token-bearing query params case-insensitively.
                    val tokenParamNames = setOf(
                        "api_key", "apikey", "token", "x-emby-token", "accesstoken",
                    )
                    val carriesToken = query.split("&").any { kv ->
                        val key = kv.substringBefore("=").lowercase()
                        key in tokenParamNames
                    }
                    if (carriesToken) {
                        data["url"] = url.substringBefore("?")
                    }
                }
                // Drop breadcrumbs whose message contains a literal token
                // pattern (e.g. an OkHttp log line of
                // "GET .../stream?api_key=abc123"). Returning null drops the
                // breadcrumb entirely rather than redacting in place, which is
                // safer because we can't know where in the message the token is.
                val message = breadcrumb.message
                if (message != null) {
                    val lower = message.lowercase()
                    val tokenPatterns = listOf(
                        "api_key=", "apikey=", "x-emby-token:", "x-emby-token=",
                        "accesstoken=",
                    )
                    if (tokenPatterns.any { lower.contains(it) }) {
                        return@setBeforeBreadcrumb null
                    }
                }
                breadcrumb
            }
            options.dsn?.let { dsn ->
                if (dsn.isNotBlank()) {
                    configureSentryUserContext()
                }
            }
        }
        applicationScope.launch {
            audioPlaybackManager.start()
            nowPlayingWidgetUpdater.start()
            com.raulshma.jellyplay.widget.WidgetWorkScheduler.enqueuePeriodic(this@JellyPlayApplication)
            com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler.enqueuePeriodic(this@JellyPlayApplication)
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
            // KEEP (not REPLACE): the unique-work name is stable across process
            // restarts, so if the previous worker is still in-flight WorkManager
            // will keep it. Replacing here would cancel an active download and
            // risk orphaned partial bytes between the cancel and the new
            // worker's setForeground call.
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
                    ExistingWorkPolicy.KEEP,
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
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to recover pending downloads", e)
        }
    }

    private suspend fun cleanupStuckDownloads() {
        try {
            downloadDao.resetStuckDownloading()
            val failed = downloadDao.getFailedDownloads()
            for (download in failed) {
                if (download.downloadPath.isNotBlank()) {
                    val file = java.io.File(download.downloadPath)
                    // Delete partial files unconditionally. Multi-connection
                    // downloads use RandomAccessFile scattered writes that
                    // cannot be resumed (DownloadWorker deletes the partial
                    // file on cancel/failure for the same reason), so a non-
                    // zero FAILED file is wasted storage. The DB row stays
                    // FAILED so the user sees the failure in the UI and can
                    // retry manually. Previously only 0-byte files were
                    // deleted, which left e.g. a 50 MB file that failed at
                    // 80 % sitting on disk forever.
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup stuck downloads", e)
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
