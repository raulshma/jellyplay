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
 * The WorkManager arm of the [AutoDownloadScheduler] interface (the interface
 * itself moved to `:shared:core:data` commonMain — same package — so feature
 * and app consumers reference it; the TvWatchNextSchedulerImpl precedent):
 * schedules the periodic background auto-download worker that fetches new
 * episodes of series the user has already downloaded. The schedule stays
 * active while the `autoDownloadMode` preference is enabled **or** any series
 * carries a per-series override (gh-181) — a global NEVER with one INSTANT
 * series must not cancel the pass that series rides; only when both are dead
 * is the periodic work cancelled.
 */
class AutoDownloadSchedulerImpl(
    private val context: Context,
    private val downloadsStore: DownloadsStore,
    private val policyStore: SeriesDownloadPolicyStore,
    private val applicationScope: CoroutineScope,
) : AutoDownloadScheduler {
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
    override fun sync() {
        applicationScope.launch {
            if (shouldStayScheduled()) {
                enqueue()
            } else {
                cancel()
            }
        }
    }

    /**
     * The [AutoDownloadScheduler.checkNow] one-shot: fired by the storage
     * screen's "Check for new episodes now", when the app returns to the
     * foreground, and after a successful library scan. Mirrors
     * [PlaybackSyncSchedulerImpl.enqueueNow]: a KEEP one-shot under a distinct
     * unique name so the manual trigger never duplicates an already-queued
     * run.
     *
     * Gated on the preference here (not just inside the worker) so a disabled
     * periodic schedule isn't resurrected by the manual path — if the
     * periodic was cancelled because the pref is off, this is a no-op.
     */
    override fun checkNow() {
        applicationScope.launch {
            if (!shouldStayScheduled()) return@launch

            UniqueWorkSchedules.uniqueOnce<AutoDownloadWorker>(
                context = context,
                uniqueName = AutoDownloadWorker.UNIQUE_NOW_NAME,
                tag = AutoDownloadWorker.WORK_TAG,
                // The manual trigger keeps the periodic's battery gate —
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
