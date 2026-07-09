package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Periodically checks for new episodes of series the user has already
 * downloaded from and auto-downloads them when the
 * [com.raulshma.jellyplay.core.model.UserPreferences.autoDownloadNewEpisodes]
 * preference is enabled. The worker respects the WiFi-only and storage-limit
 * constraints enforced inside [DownloadRepository].
 *
 * Failures are observable: a per-series fetch error does not abort the run
 * (partial success is preserved via `getOrElse`), but it is recorded so the
 * worker can escalate retry → failure after [MAX_RETRIES]. Previously this
 * worker returned `Result.success()` unconditionally, leaving a persistently
 * broken server path invisible to operators.
 */
@HiltWorker
class AutoDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesStore: UserPreferencesStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesStore.preferences.firstOrNull() ?: return Result.success()
        if (!prefs.autoDownloadNewEpisodes) return Result.success()

        val seriesIds = downloadRepository.getDownloadedSeriesIds()
        if (seriesIds.isEmpty()) return Result.success()

        // Fetch every series' downloaded episode ids in a single 2-column query
        // (grouped by seriesId) instead of issuing one full-row query per series
        // inside the loop below. A 100-episode series previously decoded ~100
        // rows × 23 columns per iteration; this reads 2 columns, once.
        val downloadedEpisodeIdsBySeries = downloadRepository.getDownloadedEpisodeIdsBySeries()

        var hadTransientFailure = false
        for (seriesId in seriesIds) {
            if (isStopped) break
            val alreadyDownloaded = downloadedEpisodeIdsBySeries[seriesId].orEmpty()
            val seasons = mediaRepository.getSeasons(seriesId).getOrElse {
                hadTransientFailure = true
                emptyList()
            }
            for (season in seasons) {
                if (isStopped) break
                val episodes = mediaRepository.getEpisodes(seriesId, season.id).getOrElse {
                    hadTransientFailure = true
                    emptyList()
                }
                val newEpisodeIds = episodes
                    .filter { it.id !in alreadyDownloaded }
                    .map { it.id }
                if (newEpisodeIds.isNotEmpty()) {
                    downloadRepository.downloadSeries(
                        seriesId = seriesId,
                        episodeIds = mapOf(season.id to newEpisodeIds),
                    )
                }
            }
        }

        return if (hadTransientFailure) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Log.w(TAG, "AutoDownload exhausted $MAX_RETRIES retries")
                Result.failure()
            }
        } else {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.auto_download"
        const val WORK_TAG = "auto_download"
        private const val TAG = "AutoDownloadWorker"
        private const val MAX_RETRIES = 3
    }
}
