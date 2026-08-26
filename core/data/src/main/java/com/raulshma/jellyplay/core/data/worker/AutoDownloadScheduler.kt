package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Schedules the periodic background auto-download worker that fetches new
 * episodes of series the user has already downloaded. The schedule is only
 * active while the
 * [com.raulshma.jellyplay.core.model.legacy.UserPreferences.autoDownloadNewEpisodes]
 * preference is enabled; disabling it cancels the periodic work.
 */
class AutoDownloadScheduler(
    private val context: Context,
    private val downloadsStore: DownloadsStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    companion object {
        private val CHECK_INTERVAL: Duration = Duration.ofHours(6)
        private val CHECK_FLEX: Duration = Duration.ofHours(1)
    }

    /**
     * Reads the current preference and either enqueues or cancels the
     * periodic auto-download work. Safe to call repeatedly (KEEP policy).
     *
     * Runs on the injected [applicationScope]'s dispatcher: the preference
     * read and WorkManager enqueue are non-blocking (DataStore manages its own
     * IO internally), so no explicit dispatcher hop is needed. This keeps the
     * scope fully replaceable for tests.
     */
    fun sync() {
        applicationScope.launch {
            val enabled = downloadsStore.downloads.first().autoDownloadNewEpisodes
            if (enabled) {
                enqueue()
            } else {
                cancel()
            }
        }
    }

    /**
     * Immediate one-shot trigger fired when the app returns to the foreground
     * or after a successful library scan. Mirrors [PlaybackSyncScheduler.enqueueNow]:
     * a [OneTimeWorkRequestBuilder] with [ExistingWorkPolicy.KEEP] under a
     * distinct unique name so the foreground trigger never duplicates an
     * already-queued run.
     *
     * Gated on the preference here (not just inside the worker) so a disabled
     * periodic schedule isn't resurrected by the foreground path — if the
     * periodic was cancelled because the pref is off, this is a no-op.
     */
    fun enqueueNow() {
        applicationScope.launch {
            val enabled = downloadsStore.downloads.first().autoDownloadNewEpisodes
            if (!enabled) return@launch

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
                .setConstraints(constraints)
                .addTag(AutoDownloadWorker.WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                AutoDownloadWorker.UNIQUE_NOW_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    private fun enqueue() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoDownloadWorker>(CHECK_INTERVAL, CHECK_FLEX)
            .setConstraints(constraints)
            .addTag(AutoDownloadWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoDownloadWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(
            AutoDownloadWorker.UNIQUE_PERIODIC_NAME,
        )
    }
}
