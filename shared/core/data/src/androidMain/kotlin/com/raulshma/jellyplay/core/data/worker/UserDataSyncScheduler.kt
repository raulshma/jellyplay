package com.raulshma.jellyplay.core.data.worker

import android.content.Context
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
        UniqueWorkSchedules.uniquePeriodic<UserDataSyncWorker>(
            context = context,
            uniqueName = UserDataSyncWorker.UNIQUE_PERIODIC_NAME,
            tag = UserDataSyncWorker.WORK_TAG,
            interval = SYNC_INTERVAL,
            flexInterval = SYNC_FLEX,
            batteryNotLow = true,
        )
    }

    override fun enqueueNow() {
        UniqueWorkSchedules.uniqueOnce<UserDataSyncWorker>(
            context = context,
            uniqueName = UserDataSyncWorker.UNIQUE_NOW_NAME,
            tag = UserDataSyncWorker.WORK_TAG,
        )
    }

    companion object {
        private val SYNC_INTERVAL: Duration = Duration.ofHours(12)
        private val SYNC_FLEX: Duration = Duration.ofHours(1)
    }
}
