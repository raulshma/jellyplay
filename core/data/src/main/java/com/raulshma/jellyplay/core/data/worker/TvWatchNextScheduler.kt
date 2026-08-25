package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [TvWatchNextScheduler] interface itself moved to `:shared:core:data`
 * commonMain (same package — home conveyor, PlaybackSyncScheduler
 * precedent) so the shared feature can reference it; only the WorkManager
 * implementation lives here.
 */
@Singleton
class TvWatchNextSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TvWatchNextScheduler {
    override fun scheduleRefresh() {
        try {
            val request = OneTimeWorkRequestBuilder<TvWatchNextWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .addTag(TvWatchNextWorker.WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                TvWatchNextWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
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
