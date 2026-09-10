package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetProvider
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.raulshma.jellyplay.widget.skeleton.widgetIdsFor
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Enqueue helpers for the home-screen recommendations widget workers.
 *
 * Each widget kind has:
 *   * a periodic schedule (refresh every 6 hours when network is available)
 *   * a one-shot immediate refresh (triggered by user tapping the refresh
 *     button or by the configuration activity on save)
 *
 * Manual refresh is throttled via a lightweight in-DataStore timestamp
 * (see [COOLDOWN_MS]) so rapid taps don't hammer the upstream API.
 *
 * Defined as an interface (and consumed via that interface) so callers don't
 * reach into the concrete worker classes, mirroring the `TvWatchNextScheduler`
 * DI-clean pattern.
 */
interface WidgetWorkScheduler {
    fun enqueuePeriodic()

    /** Returns `true` if the request was accepted, `false` if suppressed by cooldown. */
    suspend fun refreshLibraryNow(): Boolean

    /** Returns `true` if the request was accepted, `false` if suppressed by cooldown. */
    suspend fun refreshSeerrNow(): Boolean
}

class WidgetWorkSchedulerImpl (
    private val context: Context,
) : WidgetWorkScheduler {

    /**
     * One widget kind's scheduling identity: the provider whose bound
     * instances gate the periodic schedule, the worker that refreshes the
     * cache, its unique-work names + tag (stable — the unique names are
     * WorkManager's match/cancel key), and the in-process cooldown slot for
     * the manual one-shot.
     *
     * In-process cooldowns only (the rest of the app uses a single
     * UserPreferences DataStore, and a second persisted file doubled the
     * DataStore actor/IO machinery for two Long timestamps). The 5-second
     * cooldown only matters within the live process — losing it across
     * process death just means one extra refresh is allowed, which is
     * acceptable.
     */
    private class Flavour(
        val providerClass: Class<out AppWidgetProvider>,
        val workerClass: Class<out ListenableWorker>,
        val uniquePeriodicName: String,
        val uniqueOneshotName: String,
        val workTag: String,
        val cooldownSlot: AtomicLong,
    )

    private val libraryFlavour = Flavour(
        providerClass = LibraryRecommendationsWidget::class.java,
        workerClass = LibraryRecommendationsWidgetWorker::class.java,
        uniquePeriodicName = LibraryRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME,
        uniqueOneshotName = LibraryRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME,
        workTag = LibraryRecommendationsWidgetWorker.WORK_TAG,
        cooldownSlot = AtomicLong(0L),
    )

    private val seerrFlavour = Flavour(
        providerClass = SeerrRecommendationsWidget::class.java,
        workerClass = SeerrRecommendationsWidgetWorker::class.java,
        uniquePeriodicName = SeerrRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME,
        uniqueOneshotName = SeerrRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME,
        workTag = SeerrRecommendationsWidgetWorker.WORK_TAG,
        cooldownSlot = AtomicLong(0L),
    )

    private val flavours = listOf(libraryFlavour, seerrFlavour)

    override fun enqueuePeriodic() {
        // Skip the periodic schedule when no widget of either kind is bound.
        // With ExistingPeriodicWorkPolicy.KEEP the schedule never changes
        // after the first run, so re-enqueueing on every cold start is pure
        // overhead (2 WorkManager DB writes + 2 scheduler reads). On devices
        // with no widgets installed this avoids scheduling two periodic
        // workers that would otherwise fire every 6 h for nothing.
        val bound = flavours.map { flavour ->
            flavour to widgetIdsFor(context, flavour.providerClass).isNotEmpty()
        }
        if (bound.none { it.second }) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        WorkManager.getInstance(context).apply {
            for ((flavour, present) in bound) {
                if (!present) continue
                enqueueUniquePeriodicWork(
                    flavour.uniquePeriodicName,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequest.Builder(flavour.workerClass, REFRESH_PERIOD, REFRESH_FLEX)
                        .setConstraints(constraints)
                        .addTag(flavour.workTag)
                        .build(),
                )
            }
        }
    }

    override suspend fun refreshLibraryNow(): Boolean = refreshNow(libraryFlavour)

    override suspend fun refreshSeerrNow(): Boolean = refreshNow(seerrFlavour)

    private suspend fun refreshNow(flavour: Flavour): Boolean {
        if (!claimRefreshSlot(flavour.cooldownSlot)) return false
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest.Builder(flavour.workerClass)
            .setConstraints(constraints)
            .addTag(flavour.workTag)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            flavour.uniqueOneshotName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return true
    }

    companion object {
        private val REFRESH_PERIOD: Duration = Duration.ofHours(6)
        private val REFRESH_FLEX: Duration = Duration.ofMinutes(30)
    }
}

/**
 * Manual-refresh cooldown window, in-process only — two triggers inside it
 * collapse to one enqueued one-shot (see the Flavour KDoc above).
 */
internal const val COOLDOWN_MS = 5_000L

/**
 * Atomically claims [lastRefreshAt]'s cooldown slot as of [nowMs] (a test
 * seam; production passes the wall clock). The former get()-then-set() let
 * two triggers inside the window both pass: both read the same stale stamp,
 * then both stamped. The `compareAndSet` closes that gap — only the thread
 * whose CAS from the observed stamp lands wins and stamps; the loser's CAS
 * fails, its re-read finds the fresh stamp inside [COOLDOWN_MS], and it is
 * suppressed without restamping.
 */
internal fun claimRefreshSlot(
    lastRefreshAt: AtomicLong,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    while (true) {
        val last = lastRefreshAt.get()
        if (last > 0L && nowMs - last < COOLDOWN_MS) return false
        if (lastRefreshAt.compareAndSet(last, nowMs)) return true
    }
}
