package com.raulshma.jellyplay.core.notification.scheduler

import android.content.Context
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.worker.UniqueWorkSchedules
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.worker.NewMediaCheckWorker
import kotlinx.coroutines.flow.first
import java.time.Duration

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
        UniqueWorkSchedules.uniqueOnce<NewMediaCheckWorker>(
            context = context,
            uniqueName = UNIQUE_NOW_NAME,
            tag = NewMediaCheckWorker.WORK_TAG,
        )
    }

    private fun enqueue(intervalMinutes: Long) {
        UniqueWorkSchedules.uniquePeriodic<NewMediaCheckWorker>(
            context = context,
            uniqueName = WORK_NAME,
            tag = NewMediaCheckWorker.WORK_TAG,
            interval = Duration.ofMinutes(intervalMinutes.coerceAtLeast(15)),
            // The only periodic on the exponential backoff; battery-not-low is
            // deliberately absent here (presence varied by copy before the
            // UniqueWorkSchedules fold — convergence is a future choice).
            backoff = UniqueWorkSchedules.BackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                Duration.ofMinutes(5),
            ),
        )
    }

    companion object {
        const val WORK_NAME = "jellyplay_new_media_check"
        const val UNIQUE_NOW_NAME = "jellyplay_new_media_check_now"
    }
}
