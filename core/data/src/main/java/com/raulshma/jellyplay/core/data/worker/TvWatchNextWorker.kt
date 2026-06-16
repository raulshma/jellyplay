package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.tv.TvWatchNextPublisher
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Periodically refreshes the Android TV "Watch Next" OS row from the
 * Continue Watching + Next Up lists. The worker is TV-only — see
 * [TvWatchNextPublisher] for the publishing logic — and is a no-op when the
 * `androidTvWatchNextEnabled` preference is disabled.
 */
@HiltWorker
class TvWatchNextWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val preferencesStore: UserPreferencesStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesStore.preferences.firstOrNull() ?: return Result.success()
        if (!prefs.androidTvWatchNextEnabled) return Result.success()

        val publisher = TvWatchNextPublisher(applicationContext, mediaRepository, playbackRepository)
        return publisher.publish().fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "com.raulshma.jellyplay.work.tv_watch_next"
        private const val MAX_RETRIES = 3
    }
}
