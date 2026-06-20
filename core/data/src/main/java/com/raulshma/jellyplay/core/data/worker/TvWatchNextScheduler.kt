package com.raulshma.jellyplay.core.data.worker

import android.content.Context
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
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                TvWatchNextWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        } catch (_: Exception) {
            // WorkManager not initialised / unavailable — ignore. This mirrors
            // the previous inline try/catch in HomeViewModel so behaviour is
            // preserved exactly.
        }
    }
}
