package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.isEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Schedules the periodic background auto-download worker that fetches new
 * episodes of series the user has already downloaded. The schedule stays
 * active while the `autoDownloadMode` preference is enabled **or** any series
 * carries a per-series override (gh-181) — a global NEVER with one INSTANT
 * series must not cancel the pass that series rides; only when both are dead
 * is the periodic work cancelled.
 */
class AutoDownloadScheduler(
    private val context: Context,
    private val downloadsStore: DownloadsStore,
    private val policyStore: SeriesDownloadPolicyStore,
    private val applicationScope: CoroutineScope,
) {
    /**
     * The keep-alive predicate both triggers share: the global mode, or any
     * per-series override, keeps the machinery alive.
     */
    private suspend fun shouldStayScheduled(): Boolean {
        if (downloadsStore.downloads.first().autoDownloadMode.isEnabled) return true
        return runCatching { policyStore.hasSeriesPolicies() }.getOrDefault(false)
    }
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
            if (shouldStayScheduled()) {
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
            if (!shouldStayScheduled()) return@launch

            UniqueWorkSchedules.uniqueOnce<AutoDownloadWorker>(
                context = context,
                uniqueName = AutoDownloadWorker.UNIQUE_NOW_NAME,
                tag = AutoDownloadWorker.WORK_TAG,
                // The foreground trigger keeps the periodic's battery gate —
                // mirroring the drain one-shots' bare CONNECTED is a deliberate
                // future choice (see UniqueWorkSchedules).
                batteryNotLow = true,
            )
        }
    }

    private fun enqueue() {
        UniqueWorkSchedules.uniquePeriodic<AutoDownloadWorker>(
            context = context,
            uniqueName = AutoDownloadWorker.UNIQUE_PERIODIC_NAME,
            tag = AutoDownloadWorker.WORK_TAG,
            interval = CHECK_INTERVAL,
            flexInterval = CHECK_FLEX,
            batteryNotLow = true,
        )
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(
            AutoDownloadWorker.UNIQUE_PERIODIC_NAME,
        )
    }
}
