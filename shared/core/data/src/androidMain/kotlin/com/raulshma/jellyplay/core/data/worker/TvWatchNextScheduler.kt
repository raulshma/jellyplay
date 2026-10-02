package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy

/**
 * The [TvWatchNextScheduler] interface itself moved to `:shared:core:data`
 * commonMain (same package — home conveyor, PlaybackSyncScheduler
 * precedent) so the shared feature can reference it; only the WorkManager
 * implementation lives here.
 */
class TvWatchNextSchedulerImpl(
    private val context: Context,
) : TvWatchNextScheduler {
    override fun scheduleRefresh() {
        try {
            UniqueWorkSchedules.uniqueOnce<TvWatchNextWorker>(
                context = context,
                uniqueName = TvWatchNextWorker.UNIQUE_WORK_NAME,
                tag = TvWatchNextWorker.WORK_TAG,
                // REPLACE, not the drains' KEEP: a re-requested refresh
                // supersedes the queued one instead of being dropped.
                existingPolicy = ExistingWorkPolicy.REPLACE,
            )
        } catch (e: Exception) {
            // WorkManager not initialised / unavailable — keep the no-throw
            // contract but surface the failure for diagnostics instead of
            // swallowing silently.
            Log.w(TAG, "Failed to schedule TvWatchNext refresh", e)
        }
    }

    companion object {
        private const val TAG = "TvWatchNextScheduler"
    }
}
