package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.HomeSectionQuery
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.HomeSectionType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Periodically refreshes the local caches against the server so favourites,
 * played status, and playback positions stay consistent across devices.
 *
 * The worker:
 *   1. Invalidates the in-memory home sections + per-item detail caches.
 *   2. Re-fetches the Continue Watching / Next Up rows to pull fresh user-data.
 *
 * It is a no-op when the [UserPreferencesStore.userDataSyncEnabled] preference
 * is disabled (default: enabled) and respects the user's active session only —
 * a missing active user short-circuits the run.
 *
 * Scheduled via [com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler].
 */
@HiltWorker
class UserDataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    // The concrete impl (same Gradle module) so the worker can reach the
    // module-internal wholesale invalidateCaches — plan 08 demoted it off the
    // public MediaRepository interface; the identity observer in the impl is
    // its only other caller.
    private val mediaRepository: MediaRepositoryImpl,
    private val playbackStore: PlaybackStore,
    private val serverIdentityStore: ServerIdentityStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = playbackStore.playback.firstOrNull() ?: return Result.success()
        if (!prefs.userDataSyncEnabled) return Result.success()
        // Don't run until the user has signed in at least once.
        val activeUserId = serverIdentityStore.activeUserId.firstOrNull()
        if (activeUserId.isNullOrBlank()) return Result.success()

        return runCatching {
            mediaRepository.invalidateCaches()
            // Re-fetch home sections to repopulate the cache with fresh user-data.
            // Only success/failure matters here; the sections themselves are
            // consumed elsewhere from the repopulated cache.
            mediaRepository.getHomeSections(
                HomeSectionQuery(
                    enabledSections = setOf(
                        HomeSectionType.CONTINUE_WATCHING,
                        HomeSectionType.NEXT_UP,
                    ),
                ),
            ).getOrThrow()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                // Report failure after exhausting retries so WorkManager
                // observability surfaces persistent problems. The next
                // periodic run (12 h, UserDataSyncSchedulerImpl.SYNC_INTERVAL)
                // still fires and will try again. Previously this returned
                // Result.success() after MAX_RETRIES, hiding persistent
                // failures from operators.
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Log.w(TAG, "UserDataSync exhausted $MAX_RETRIES retries", error)
                    Result.failure()
                }
            },
        )
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.user_data_sync"
        const val UNIQUE_NOW_NAME = "com.raulshma.jellyplay.work.user_data_sync_now"
        const val WORK_TAG = "sync"
        private const val TAG = "UserDataSyncWorker"
        private const val MAX_RETRIES = 3
    }
}
