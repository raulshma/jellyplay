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
 * Schedules a one-shot refresh of the Android TV "Watch Next" OS row.
 *
 * Defined as an interface (and consumed by feature modules via that
 * interface) so feature code doesn't reach into the `core.data.worker`
 * package's concrete [TvWatchNextWorker] class. The worker remains an
 * internal scheduling detail of `core:data`.
 *
 * TV-only behaviour lives inside the worker itself: it's a no-op on
 * phones and respects the `androidTvWatchNextEnabled` preference.
 *
 * One-shot by design (startup-and-workers-architecture §7.14): there is no
 * periodic schedule; see [TvWatchNextWorker] for the rationale.
 */
interface TvWatchNextScheduler {
    fun scheduleRefresh()
}

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
            // swallowing silently (startup-and-workers-architecture §7.12).
            Log.w(TAG, "Failed to schedule TvWatchNext refresh", e)
        }
    }

    companion object {
        private const val TAG = "TvWatchNextScheduler"
    }
}
