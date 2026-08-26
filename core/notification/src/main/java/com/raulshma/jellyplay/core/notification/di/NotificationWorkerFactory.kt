package com.raulshma.jellyplay.core.notification.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.notification.worker.NewMediaCheckWorker

/**
 * Wave 8A: NewMediaCheckWorker lost its HiltWorker annotation (Koin owns
 * its deps via [androidNotificationModule]); this factory constructs it for
 * WorkManager instead of HiltWorkerFactory. Registered alongside the app's
 * HiltWorkerFactory in a DelegatingWorkerFactory — unknown class names fall
 * through to the next delegate.
 */
class NotificationWorkerFactory : WorkerFactory() {

    override fun createWorker(
        context: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName.substringAfterLast('.')) {
            NewMediaCheckWorker::class.simpleName -> NewMediaCheckWorker(
                context, workerParameters,
                mediaRepository = koin().get(),
                seenMediaRepository = koin().get(),
                notificationStore = koin().get(),
                dispatcher = koin().get(),
                scheduler = koin().get(),
            )
            else -> null
        }
}
