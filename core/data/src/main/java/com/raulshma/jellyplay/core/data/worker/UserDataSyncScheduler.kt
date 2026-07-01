package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic background user-data sync. Defined as an interface
 * (and consumed via that interface) so callers don't reach into the
 * `core.data.worker` package's concrete [UserDataSyncWorker] class, mirroring
 * the [TvWatchNextScheduler] DI-clean pattern. KEEP policy so app restarts
 * don't reset the existing cadence.
 */
interface UserDataSyncScheduler {
    fun enqueuePeriodic()
}

@Singleton
class UserDataSyncSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserDataSyncScheduler {
    override fun enqueuePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UserDataSyncWorker>(SYNC_INTERVAL, SYNC_FLEX)
            .setConstraints(constraints)
            .addTag(UserDataSyncWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UserDataSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private val SYNC_INTERVAL: Duration = Duration.ofHours(12)
        private val SYNC_FLEX: Duration = Duration.ofHours(1)
    }
}
