package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.isEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The WorkManager arm of the [AutoDownloadScheduler] interface (the interface
 * itself moved to `:shared:core:data` commonMain — same package — so feature
 * and app consumers reference it; the TvWatchNextSchedulerImpl precedent):
 * schedules the periodic background auto-download worker that fetches new
 * episodes of series the user has already downloaded. The schedule stays
 * active while the `autoDownloadMode` preference is enabled, any series
 * carries a per-series override (gh-181), **or** the `autoArmFromArr` pref is
 * on (gh-225 follow-up) — a global NEVER with one INSTANT series, or with
 * zero arms but arr auto-arm opted in, must not cancel the pass those ride;
 * only when all three are dead is the periodic work cancelled.
 */
class AutoDownloadSchedulerImpl(
    private val context: Context,
    private val downloadsStore: DownloadsStore,
    private val policyStore: SeriesDownloadPolicyStore,
    private val applicationScope: CoroutineScope,
) : AutoDownloadScheduler {
    /**
     * The keep-alive predicate both triggers share: the global mode, any
     * per-series override, or the arr auto-arm opt-in keeps the machinery
     * alive. The pref rides the store's disk-backed one-shot read
     * ([DownloadsStore.readDownloadsOrNull] — the readIdentity precedent),
     * not the shared `downloads` StateFlow projection: this gate runs inside
     * the launch a `sync()` right after a preference write performs, and the
     * eagerly-shared projection has no happens-before edge against it — a
     * just-enabled `autoArmFromArr` under a global NEVER with zero arms could
     * read the pre-write slice and CANCEL the pass it must schedule.
     *
     * The read fails OPEN: a null answer is a transient DataStore read
     * failure, not "everything off" — a wrong cancel here is persisted
     * WorkManager state with no automatic later re-scheduling trigger (the
     * gate's pokes are all user actions), so the schedule survives and the
     * next gate re-reads disk.
     */
    private suspend fun shouldStayScheduled(): Boolean {
        // The wrap is the gate's own defense (the hasSeriesPolicies idiom
        // below): readDownloadsOrNull's contract is null-on-failure, and this
        // folds any contract-violating throw into the same fail-open arm.
        val prefs = runCatchingRethrowingCancellation { downloadsStore.readDownloadsOrNull() }
            .getOrNull() ?: return true
        if (prefs.autoDownloadMode.isEnabled) return true
        // gh-225 follow-up: with arr auto-arm opted in the next pass may arm
        // rows from nothing (global NEVER + zero policy rows included), so
        // the pass must stay scheduled to run that stage at all.
        if (prefs.autoArmFromArr) return true
        return runCatchingRethrowingCancellation { policyStore.hasSeriesPolicies() }.getOrDefault(false)
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
