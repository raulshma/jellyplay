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
import kotlinx.coroutines.flow.first
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
 */
object WidgetWorkScheduler {

    private val REFRESH_PERIOD: Duration = Duration.ofHours(6)
    private val REFRESH_FLEX: Duration = Duration.ofMinutes(30)
    private const val COOLDOWN_MS = 5_000L

    private val LAST_LIBRARY_REFRESH_KEY = longPreferencesKey("widget_library_last_refresh_ms")
    private val LAST_SEERR_REFRESH_KEY = longPreferencesKey("widget_seerr_last_refresh_ms")

    private val Context.widgetCooldownStore: DataStore<Preferences> by preferencesDataStore(name = "widget_cooldown")

    fun enqueuePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val libraryRequest = PeriodicWorkRequestBuilder<LibraryRecommendationsWidgetWorker>(REFRESH_PERIOD, REFRESH_FLEX)
            .setConstraints(constraints)
            .build()

        val seerrRequest = PeriodicWorkRequestBuilder<SeerrRecommendationsWidgetWorker>(REFRESH_PERIOD, REFRESH_FLEX)
            .setConstraints(constraints)
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

    /**
     * Enqueue a one-shot refresh of the library widget. Returns `true` if the
     * request was accepted, `false` if it was suppressed by the manual-refresh
     * cooldown.
     */
    suspend fun refreshLibraryNow(context: Context): Boolean {
        if (!claimRefreshSlot(context, LAST_LIBRARY_REFRESH_KEY)) return false
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<LibraryRecommendationsWidgetWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LibraryRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return true
    }

    /**
     * Enqueue a one-shot refresh of the Seerr widget. Returns `true` if the
     * request was accepted, `false` if it was suppressed by the manual-refresh
     * cooldown.
     */
    suspend fun refreshSeerrNow(context: Context): Boolean {
        if (!claimRefreshSlot(context, LAST_SEERR_REFRESH_KEY)) return false
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SeerrRecommendationsWidgetWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SeerrRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return true
    }

    private suspend fun claimRefreshSlot(context: Context, key: Preferences.Key<Long>): Boolean {
        val now = System.currentTimeMillis()
        val store = context.widgetCooldownStore
        val last = store.data.first()[key] ?: 0L
        if (last > 0L && now - last < COOLDOWN_MS) return false
        store.edit { it[key] = now }
        return true
    }
}
