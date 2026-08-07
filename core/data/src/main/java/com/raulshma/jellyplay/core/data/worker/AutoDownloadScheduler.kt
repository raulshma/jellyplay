package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic background auto-download worker that fetches new
 * episodes of series the user has already downloaded. The schedule is only
 * active while the
 * [com.raulshma.jellyplay.core.model.legacy.UserPreferences.autoDownloadNewEpisodes]
 * preference is enabled; disabling it cancels the periodic work.
 */
@Singleton
class AutoDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
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
