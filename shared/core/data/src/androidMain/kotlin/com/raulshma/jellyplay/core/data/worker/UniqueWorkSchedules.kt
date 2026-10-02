package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/**
 * The ONE WorkManager enqueue ladder (I3 fold). The five scheduler shells —
 * [PlaybackSyncSchedulerImpl], [UserDataSyncSchedulerImpl],
 * [AutoDownloadScheduler], NotificationScheduler,
 * [TvWatchNextSchedulerImpl] — each used to restate this choreography by
 * hand: a CONNECTED constraint, a request builder carrying the worker's
 * WORK_TAG, and an enqueueUniquePeriodicWork / enqueueUniqueWork call under
 * KEEP. The restatements had drifted by copy, not design:
 * `setRequiresBatteryNotLow` sat on three of the four periodics (and on the
 * auto-download one-shot) but on none of the other one-shots, and only the
 * new-media periodic carried backoff criteria. The parameters below make
 * those choices deliberate and visible per call site instead of per copy;
 * converging them is a deliberate future choice, not something this fold
 * silently does — every shell keeps its exact current constraint set.
 *
 * The shells keep their unique-work names, tags, intervals, and flex windows;
 * TvWatchNext's REPLACE policy (and its no-throw catch) stay in that shell
 * because coalescing a refresh trigger's REPLACE with the drains' KEEP would
 * change behavior, which a fold must not.
 */
internal object UniqueWorkSchedules {

    /**
     * The backoff pair for [uniquePeriodic]; null (the default) keeps
     * WorkManager's default exponential criteria.
     */
    data class BackoffCriteria(val policy: BackoffPolicy, val delay: Duration)

    /**
     * The one-shot ladder: CONNECTED-constrained [W] tagged [tag], enqueued
     * under [uniqueName] with [existingPolicy] (KEEP unless a caller
     * deliberately passes otherwise). [batteryNotLow] defaults off — only the
     * auto-download foreground trigger sets it today.
     */
    inline fun <reified W : ListenableWorker> uniqueOnce(
        context: Context,
        uniqueName: String,
        tag: String,
        existingPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        batteryNotLow: Boolean = false,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(batteryNotLow)
            .build()
        val request = OneTimeWorkRequestBuilder<W>()
            .setConstraints(constraints)
            .addTag(tag)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, existingPolicy, request)
    }

    /**
     * The periodic ladder: same constraint/tag/KEEP choreography as
     * [uniqueOnce] over a [PeriodicWorkRequestBuilder] with [interval] and an
     * optional [flexInterval], plus optional [backoff] (only the new-media
     * check sets it today) and [batteryNotLow] (the three sync-backstop
     * periodics set it; the new-media periodic deliberately does not).
     */
    inline fun <reified W : ListenableWorker> uniquePeriodic(
        context: Context,
        uniqueName: String,
        tag: String,
        interval: Duration,
        flexInterval: Duration? = null,
        batteryNotLow: Boolean = false,
        backoff: BackoffCriteria? = null,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(batteryNotLow)
            .build()
        val request = if (flexInterval == null) {
            PeriodicWorkRequestBuilder<W>(interval)
        } else {
            PeriodicWorkRequestBuilder<W>(interval, flexInterval)
        }
            .setConstraints(constraints)
            .apply { backoff?.let { setBackoffCriteria(it.policy, it.delay) } }
            .addTag(tag)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
