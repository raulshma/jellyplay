package com.raulshma.jellyplay.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

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

    // In-process cooldown timestamps for the manual refresh entry points.
    // Previously these were persisted in a second DataStore file
    // (`widget_cooldown`); the rest of the app uses a single UserPreferences
    // DataStore, so the second file doubled the DataStore actor/IO machinery
    // for what amounts to two Long timestamps. The 5-second cooldown only
    // matters within the live process — losing it across process death just
    // means one extra refresh is allowed, which is acceptable.
    private val lastLibraryRefreshAt = java.util.concurrent.atomic.AtomicLong(0L)
    private val lastSeerrRefreshAt = java.util.concurrent.atomic.AtomicLong(0L)

    override fun enqueuePeriodic() {
        // Skip the periodic schedule when no widget of either kind is bound.
        // With ExistingPeriodicWorkPolicy.KEEP the schedule never changes
        // after the first run, so re-enqueueing on every cold start is pure
        // overhead (2 WorkManager DB writes + 2 scheduler reads). On devices
        // with no widgets installed this avoids scheduling two periodic
        // workers that would otherwise fire every 6 h for nothing.
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val hasLibraryWidget = appWidgetManager
            .getAppWidgetIds(android.content.ComponentName(context, LibraryRecommendationsWidget::class.java))
            .isNotEmpty()
        val hasSeerrWidget = appWidgetManager
            .getAppWidgetIds(android.content.ComponentName(context, SeerrRecommendationsWidget::class.java))
            .isNotEmpty()
        if (!hasLibraryWidget && !hasSeerrWidget) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val libraryRequest = PeriodicWorkRequestBuilder<LibraryRecommendationsWidgetWorker>(REFRESH_PERIOD, REFRESH_FLEX)
            .setConstraints(constraints)
            .addTag(LibraryRecommendationsWidgetWorker.WORK_TAG)
            .build()

        val seerrRequest = PeriodicWorkRequestBuilder<SeerrRecommendationsWidgetWorker>(REFRESH_PERIOD, REFRESH_FLEX)
            .setConstraints(constraints)
            .addTag(SeerrRecommendationsWidgetWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).apply {
            if (hasLibraryWidget) {
                enqueueUniquePeriodicWork(
                    LibraryRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    libraryRequest,
                )
            }
            if (hasSeerrWidget) {
                enqueueUniquePeriodicWork(
                    SeerrRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    seerrRequest,
                )
            }
        }
    }

    override suspend fun refreshLibraryNow(): Boolean {
        if (!claimRefreshSlot(lastLibraryRefreshAt)) return false
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<LibraryRecommendationsWidgetWorker>()
            .setConstraints(constraints)
            .addTag(LibraryRecommendationsWidgetWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LibraryRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return true
    }

    override suspend fun refreshSeerrNow(): Boolean {
        if (!claimRefreshSlot(lastSeerrRefreshAt)) return false
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SeerrRecommendationsWidgetWorker>()
            .setConstraints(constraints)
            .addTag(SeerrRecommendationsWidgetWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SeerrRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return true
    }

    private suspend fun claimRefreshSlot(lastRefreshAt: java.util.concurrent.atomic.AtomicLong): Boolean {
        val now = System.currentTimeMillis()
        val last = lastRefreshAt.get()
        if (last > 0L && now - last < COOLDOWN_MS) return false
        lastRefreshAt.set(now)
        return true
    }

    companion object {
        private val REFRESH_PERIOD: Duration = Duration.ofHours(6)
        private val REFRESH_FLEX: Duration = Duration.ofMinutes(30)
        private const val COOLDOWN_MS = 5_000L
    }
}
