package com.raulshma.jellyplay.core.notification.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.worker.NewMediaCheckWorker
import kotlinx.coroutines.flow.first

class NotificationScheduler(
    private val context: Context,
    private val notificationStore: NotificationStore,
) {

    suspend fun scheduleOrUpdate() {
        val prefs = notificationStore.notification.first().notificationPreferences
        if (prefs.enabled) {
            enqueue(prefs.checkFrequency.intervalMinutes)
        } else {
            cancel()
        }
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun enqueueNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<NewMediaCheckWorker>()
            .setConstraints(constraints)
            .addTag(NewMediaCheckWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NOW_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun enqueue(intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<NewMediaCheckWorker>(
            java.time.Duration.ofMinutes(intervalMinutes.coerceAtLeast(15)),
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                java.time.Duration.ofMinutes(5),
            )
            .addTag(NewMediaCheckWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "jellyplay_new_media_check"
        const val UNIQUE_NOW_NAME = "jellyplay_new_media_check_now"
    }
}
