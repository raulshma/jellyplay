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
 * Schedules the playback-progress offline outbox drain. Defined as an
 * interface (and consumed via that interface) so callers don't reach into the
 * concrete [PlaybackSyncWorker], mirroring the [UserDataSyncScheduler]
 * DI-clean pattern.
 *
 * Two entry points:
 *   - [enqueuePeriodic]: long-interval backstop so queued progress eventually
 *     flushes even if the reconnect signal is missed.
 *   - [enqueueNow]: immediate one-shot drain, called on the Offline→Online
 *     transition and at app start.
 *
 * Both use KEEP policies so reconnect + periodic never enqueue duplicate runs.
 */
// C4 part 2: the PlaybackSyncScheduler interface moved verbatim to
// :shared:core:data commonMain worker/PlaybackSyncScheduler.kt (same package).

class PlaybackSyncSchedulerImpl(
    private val context: Context,
) : PlaybackSyncScheduler {
    override fun enqueuePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PlaybackSyncWorker>(SYNC_INTERVAL, SYNC_FLEX)
            .setConstraints(constraints)
            .addTag(PlaybackSyncWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PlaybackSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun enqueueNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PlaybackSyncWorker>()
            .setConstraints(constraints)
            .addTag(PlaybackSyncWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PlaybackSyncWorker.UNIQUE_NOW_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        // Backstop cadence; the reconnect listener handles the immediate case.
        private val SYNC_INTERVAL: Duration = Duration.ofHours(4)
        private val SYNC_FLEX: Duration = Duration.ofMinutes(30)
    }
}
