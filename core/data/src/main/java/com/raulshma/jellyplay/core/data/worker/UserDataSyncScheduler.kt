package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/**
 * Schedules the periodic background user-data sync. Mirrors the pattern used by
 * [com.raulshma.jellyplay.widget.WidgetWorkScheduler] — KEEP policy so app
 * restarts don't reset the existing cadence.
 */
object UserDataSyncScheduler {

    private val SYNC_INTERVAL: Duration = Duration.ofHours(12)
    private val SYNC_FLEX: Duration = Duration.ofHours(1)

    fun enqueuePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UserDataSyncWorker>(SYNC_INTERVAL, SYNC_FLEX)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UserDataSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
