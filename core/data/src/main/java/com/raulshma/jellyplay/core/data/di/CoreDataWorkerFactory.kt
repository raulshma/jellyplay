package com.raulshma.jellyplay.core.data.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.worker.AutoDownloadWorker
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncWorker
import com.raulshma.jellyplay.core.data.worker.StaleMediaScanWorker
import com.raulshma.jellyplay.core.data.worker.TvWatchNextWorker
import com.raulshma.jellyplay.core.data.worker.UserDataSyncWorker
import com.raulshma.jellyplay.core.data.worker.WatchedMediaScanWorker
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import okhttp3.OkHttpClient

/**
 * Wave 8A: the seven legacy :core:data workers lost their HiltWorker
 * annotations (Koin owns their deps via [androidCoreDataModule]); this
 * factory constructs them for WorkManager instead of HiltWorkerFactory.
 * Registered alongside the app's HiltWorkerFactory in a
 * DelegatingWorkerFactory — the first factory that recognizes the class
 * name wins, unknown names fall through to the next delegate.
 */
class CoreDataWorkerFactory : WorkerFactory() {

    override fun createWorker(
        context: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        // Normalize to the simple name — the marshalled class name is the
        // FQN, but comparing the tail keeps this robust to proguard-prefixed
        // names in release builds.
        val simpleName = workerClassName.substringAfterLast('.')
        return when (simpleName) {
            AutoDownloadWorker::class.simpleName -> AutoDownloadWorker(
                context, workerParameters,
                episodeCatalogue = koin().get(),
                downloadRepository = koin().get(),
                downloadIntake = koin().get(),
                downloadsStore = koin().get(),
            )
            UserDataSyncWorker::class.simpleName -> UserDataSyncWorker(
                context, workerParameters,
                mediaRepository = koin().get(),
                playbackStore = koin().get(),
                serverIdentityStore = koin().get(),
            )
            StaleMediaScanWorker::class.simpleName -> StaleMediaScanWorker(
                context, workerParameters,
                apiClient = koin().get(),
                scanStateDao = koin().get(),
            )
            WatchedMediaScanWorker::class.simpleName -> WatchedMediaScanWorker(
                context, workerParameters,
                apiClient = koin().get(),
                scanStateDao = koin().get(),
            )
            TvWatchNextWorker::class.simpleName -> TvWatchNextWorker(
                context, workerParameters,
                mediaRepository = koin().get(),
                playbackRepository = koin().get(),
                playbackStore = koin().get(),
            )
            PlaybackSyncWorker::class.simpleName -> PlaybackSyncWorker(
                context, workerParameters,
                outbox = koin().get(),
                playbackRepository = koin().get(),
                offlineModeManager = koin().get(),
                playedStateSync = koin().get(),
                offlineRepository = koin().get(),
                userDataSyncScheduler = koin().get(),
            )
            DownloadWorker::class.simpleName -> DownloadWorker(
                context, workerParameters,
                dao = koin().get(),
                userDao = koin().get(),
                downloadsStore = koin().get(),
                serverIdentityStore = koin().get(),
                tokenCipher = koin().get(),
                concurrencyLimiter = koin().get(),
                transferClient = koin().get(),
                okHttpClient = koin().get(NetworkQualifiers.downloadHttpClient),
            )
            else -> null
        }
    }
}
