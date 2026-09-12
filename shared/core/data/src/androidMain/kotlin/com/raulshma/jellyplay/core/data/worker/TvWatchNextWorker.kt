package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.tv.TvWatchNextPublisher
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import kotlinx.coroutines.flow.firstOrNull

/**
 * Refreshes the Android TV "Watch Next" OS row from the Continue Watching +
 * Next Up lists. The worker is TV-only — see [TvWatchNextPublisher] for the
 * publishing logic — and is a no-op when the `androidTvWatchNextEnabled`
 * preference is disabled.
 *
 * Scheduling design: this worker is
 * **one-shot only** — it is triggered on demand via [TvWatchNextScheduler] when
 * the user toggles the Watch Next setting, rather than on a periodic cadence
 * like UserDataSync/AutoDownload. This is intentional: the Watch Next row is
 * an action-driven OS surface, and the Continue Watching / Next Up lists it is
 * built from are already kept fresh by the periodic `UserDataSyncWorker`.
 * Adding a dedicated periodic schedule would duplicate that freshness signal
 * and add TV-specific background work for no user-visible benefit.
 */
class TvWatchNextWorker(
    context: Context,
    params: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackStore: PlaybackStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = playbackStore.playback.firstOrNull() ?: return Result.success()
        val publisher = TvWatchNextPublisher(applicationContext, mediaRepository, playbackRepository)

        if (!prefs.androidTvWatchNextEnabled) {
            return publisher.clear().fold(
                onSuccess = { Result.success() },
                onFailure = { Result.failure() },
            )
        }

        return publisher.publish().fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "com.raulshma.jellyplay.work.tv_watch_next"
        const val WORK_TAG = "tv_watch_next"
        private const val MAX_RETRIES = 3
    }
}
