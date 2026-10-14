package com.raulshma.jellyplay.core.notification.scheduler

import android.content.Context
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.worker.UniqueWorkSchedules
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.worker.NewMediaCheckWorker
import kotlinx.coroutines.flow.first
import java.time.Duration

class NotificationScheduler(
    private val context: Context,
    private val notificationStore: NotificationStore,
) {

    suspend fun scheduleOrUpdate() {
        val prefs = notificationStore.notification.first().notificationPreferences
        if (prefs.enabled) {
            enqueue(prefs.checkFrequency.intervalMinutes)
        } else {
            cancel()
        }
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun enqueueNow() {
        UniqueWorkSchedules.uniqueOnce<NewMediaCheckWorker>(
            context = context,
            uniqueName = UNIQUE_NOW_NAME,
            tag = NewMediaCheckWorker.WORK_TAG,
        )
    }

    /**
     * D12's *arr air-time accelerator: one deferred one-shot series check at
     * [initialDelay] = airTime + buffer, keyed idempotently per
     * (series, episode) — a re-schedule of the same name is a KEEP no-op, so
     * the poller can re-run the accelerator pass every cycle without stacking
     * duplicate runs. The name is built by
     * [seriesEpisodeCheckName] (pure — pinned by the scheduler test).
     */
    fun enqueueSeriesEpisodeCheck(seriesId: String, episodeKey: String, initialDelay: Duration) {
        UniqueWorkSchedules.uniqueOnce<NewMediaCheckWorker>(
            context = context,
            uniqueName = seriesEpisodeCheckName(seriesId, episodeKey),
            tag = SERIES_CHECK_WORK_TAG,
            existingPolicy = androidx.work.ExistingWorkPolicy.KEEP,
            initialDelay = initialDelay,
        )
    }

    private fun enqueue(intervalMinutes: Long) {
        UniqueWorkSchedules.uniquePeriodic<NewMediaCheckWorker>(
            context = context,
            uniqueName = WORK_NAME,
            tag = NewMediaCheckWorker.WORK_TAG,
            interval = Duration.ofMinutes(intervalMinutes.coerceAtLeast(15)),
            // The only periodic on the exponential backoff; battery-not-low is
            // deliberately absent here (presence varied by copy before the
            // UniqueWorkSchedules fold — convergence is a future choice).
            backoff = UniqueWorkSchedules.BackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                Duration.ofMinutes(5),
            ),
        )
    }

    companion object {
        const val WORK_NAME = "jellyplay_new_media_check"
        const val UNIQUE_NOW_NAME = "jellyplay_new_media_check_now"

        /**
         * The tag the *arr accelerator's one-shots carry (the periodic and
         * enqueue-now runs keep [NewMediaCheckWorker.WORK_TAG]; the one-shots
         * carry this instead) — the worker reads it to apply the one-shot
         * retry cap (the TvWatchNextWorker pattern) without changing the
         * periodic run's retry behavior. Cancellation is unique-name-keyed,
         * so the differing tag set never couples the two families.
         */
        const val SERIES_CHECK_WORK_TAG = "series_episode_check"

        /**
         * The idempotent per-(series, episode) one-shot name. [episodeKey] is
         * the accelerator's per-episode identity (tvdb id + air-date stamp —
         * calendar rows carry no Jellyfin episode id); pure so tests can pin
         * the exact name the KEEP-idempotency relies on.
         */
        fun seriesEpisodeCheckName(seriesId: String, episodeKey: String): String =
            "jellyplay_series_episode_check_${seriesId}_$episodeKey"
    }
}
