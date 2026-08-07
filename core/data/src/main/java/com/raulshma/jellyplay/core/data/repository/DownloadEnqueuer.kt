package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the WorkManager enqueue recipe used to (re)start
 * a [DownloadWorker].
 *
 * Extracted out of `DownloadRepositoryImpl` (runtime enqueue) and
 * `DownloadRecoveryInitializer` (cold-start re-enqueue), which had each
 * hand-rolled the same `OneTimeWorkRequestBuilder` + backoff + tag + unique-work
 * recipe. The two copies had silently drifted: the runtime path applied the
 * user's wifi-only and download-schedule-window constraints, while the
 * cold-start recovery path skipped both. Consolidating here makes that
 * difference a deliberate, documented choice via [honorScheduleAndNetwork]
 * instead of a divergence hiding behind duplicated code.
 *
 * The shared shape — unique-work name, backoff (30 s exponential, matching
 * [DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS]), input-data, work tag,
 * `ExistingWorkPolicy.KEEP` — lives here so it cannot drift again.
 */
@Singleton
class DownloadEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadsStore: DownloadsStore,
) {

    /**
     * Enqueue (or keep) the worker for [downloadId].
     *
     * @param honorScheduleAndNetwork when `true` (the runtime path), the worker
     *   is constrained to the user's metered-network preference and delayed
     *   until the next [UserPreferencesStore.downloadScheduleWindow] opening.
     *   When `false` (cold-start recovery of rows that were already PENDING
     *   under those very constraints), the worker is enqueued unconstrained so
     *   a process restart does not strand downloads that had already cleared
     *   the gate. `KEEP` ensures an in-flight worker is never cancelled.
     */
    fun enqueue(
        downloadId: String,
        honorScheduleAndNetwork: Boolean = true,
    ) {
        val prefs = if (honorScheduleAndNetwork) downloadsStore.downloads.value else null

        val constraintsBuilder = Constraints.Builder()
        var initialDelayMs = 0L

        if (prefs != null) {
            val wifiOnly = prefs.wifiOnlyDownloads
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            constraintsBuilder.setRequiredNetworkType(networkType)

            if (prefs.downloadScheduleEnabled) {
                val now = java.util.Calendar.getInstance()
                val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                val window = prefs.downloadScheduleWindow
                val start = window.startHour
                val end = window.endHour
                val inWindow = if (start <= end) {
                    currentHour in start until end
                } else {
                    currentHour >= start || currentHour < end
                }
                if (!inWindow) {
                    val target = (now.clone() as java.util.Calendar).apply {
                        set(java.util.Calendar.HOUR_OF_DAY, start)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                        if (before(now)) add(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                    initialDelayMs = target.timeInMillis - now.timeInMillis
                }
                if (window.wifiOnly) {
                    constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
                }
            }
        }

        val workRequestBuilder = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraintsBuilder.build())
            // Explicit exponential backoff so a flaky server returning 503/429
            // is not hammered by all concurrent downloads retrying as fast as
            // WorkManager allows. 30 s base multiplies load far less than the
            // implicit default while still recovering promptly.
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS,
                TimeUnit.MILLISECONDS,
            )
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build(),
            )
            .addTag(DownloadWorker.WORK_TAG)
        if (initialDelayMs > 0) {
            workRequestBuilder.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadWorker.workName(downloadId),
            ExistingWorkPolicy.KEEP,
            workRequestBuilder.build(),
        )
    }
}
