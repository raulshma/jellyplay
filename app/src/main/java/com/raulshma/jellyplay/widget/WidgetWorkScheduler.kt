package com.raulshma.jellyplay.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class WidgetWorkSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetWorkScheduler {

    override fun enqueuePeriodic() {
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
            enqueueUniquePeriodicWork(
                LibraryRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                libraryRequest,
            )
            enqueueUniquePeriodicWork(
                SeerrRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                seerrRequest,
            )
        }
    }

    override suspend fun refreshLibraryNow(): Boolean {
        if (!claimRefreshSlot(LAST_LIBRARY_REFRESH_KEY)) return false
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
        if (!claimRefreshSlot(LAST_SEERR_REFRESH_KEY)) return false
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

    private suspend fun claimRefreshSlot(key: Preferences.Key<Long>): Boolean {
        val now = System.currentTimeMillis()
        val store = context.widgetCooldownStore
        val last = store.data.first()[key] ?: 0L
        if (last > 0L && now - last < COOLDOWN_MS) return false
        store.edit { it[key] = now }
        return true
    }

    companion object {
        private val REFRESH_PERIOD: Duration = Duration.ofHours(6)
        private val REFRESH_FLEX: Duration = Duration.ofMinutes(30)
        private const val COOLDOWN_MS = 5_000L

        private val LAST_LIBRARY_REFRESH_KEY = longPreferencesKey("widget_library_last_refresh_ms")
        private val LAST_SEERR_REFRESH_KEY = longPreferencesKey("widget_seerr_last_refresh_ms")
    }
}

private val Context.widgetCooldownStore: DataStore<Preferences> by preferencesDataStore(name = "widget_cooldown")
