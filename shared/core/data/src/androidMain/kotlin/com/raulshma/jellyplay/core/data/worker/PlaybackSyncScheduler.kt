package com.raulshma.jellyplay.core.data.worker

import android.content.Context
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
// shared:core:data commonMain worker/PlaybackSyncScheduler.kt (same package).

class PlaybackSyncSchedulerImpl(
    private val context: Context,
) : PlaybackSyncScheduler {
    override fun enqueuePeriodic() {
        UniqueWorkSchedules.uniquePeriodic<PlaybackSyncWorker>(
            context = context,
            uniqueName = PlaybackSyncWorker.UNIQUE_PERIODIC_NAME,
            tag = PlaybackSyncWorker.WORK_TAG,
            interval = SYNC_INTERVAL,
            flexInterval = SYNC_FLEX,
            batteryNotLow = true,
        )
    }

    override fun enqueueNow() {
        UniqueWorkSchedules.uniqueOnce<PlaybackSyncWorker>(
            context = context,
            uniqueName = PlaybackSyncWorker.UNIQUE_NOW_NAME,
            tag = PlaybackSyncWorker.WORK_TAG,
        )
    }

    companion object {
        // Backstop cadence; the reconnect listener handles the immediate case.
        private val SYNC_INTERVAL: Duration = Duration.ofHours(4)
        private val SYNC_FLEX: Duration = Duration.ofMinutes(30)
    }
}
