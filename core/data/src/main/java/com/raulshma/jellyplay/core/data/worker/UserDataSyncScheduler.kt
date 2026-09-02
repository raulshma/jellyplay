package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/**
 * Schedules the background user-data sync. Defined as an interface (and
 * consumed via that interface) so callers don't reach into the
 * `core.data.worker` package's concrete [UserDataSyncWorker] class, mirroring
 * the [TvWatchNextScheduler] DI-clean pattern.
 *
 * Two entry points:
 *   - [enqueuePeriodic]: 12h backstop, KEEP policy so app restarts don't reset
 *     the existing cadence.
 *   - [enqueueNow]: immediate one-shot refresh. Triggered after a playback
 *     outbox drain (offline → online) so the Continue Watching / Next Up rows
 *     and detail caches reflect the just-pushed server state instead of
 *     waiting up to 12h for the periodic tick or for the 60s/2min cache TTLs.
 */
interface UserDataSyncScheduler {
    fun enqueuePeriodic()
    fun enqueueNow()
}

class UserDataSyncSchedulerImpl(
    private val context: Context,
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

    override fun enqueueNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UserDataSyncWorker>()
            .setConstraints(constraints)
            .addTag(UserDataSyncWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UserDataSyncWorker.UNIQUE_NOW_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private val SYNC_INTERVAL: Duration = Duration.ofHours(12)
        private val SYNC_FLEX: Duration = Duration.ofHours(1)
    }
}
