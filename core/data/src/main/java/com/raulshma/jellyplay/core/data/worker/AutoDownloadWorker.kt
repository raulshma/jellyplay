package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Periodically checks for new episodes of series the user has already
 * downloaded from and auto-downloads them when the
 * [com.raulshma.jellyplay.core.model.legacy.UserPreferences.autoDownloadNewEpisodes]
 * preference is enabled. The worker respects the WiFi-only and storage-limit
 * constraints enforced inside [DownloadRepository].
 *
 * Seasons + episodes come from [EpisodeCatalogue.loadSeriesEpisodes] — a single
 * consolidated snapshot per series instead of a separate `getSeasons` +
 * per-season `getEpisodes` fan-out. This path runs online-only, so `offline`
 * defaults to `false`.
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
    private val episodeCatalogue: EpisodeCatalogue,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
    private val downloadsStore: DownloadsStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = downloadsStore.downloads.firstOrNull() ?: return Result.success()
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
            // One consolidated load per series: seasons + every season's episodes
            // in a single snapshot, replacing the prior getSeasons + per-season
            // getEpisodes fan-out. A catalogue failure is a transient error — map
            // it to an empty snapshot so this series is skipped but the run
            // continues and escalates retry → failure below.
            val snapshot = episodeCatalogue.loadSeriesEpisodes(seriesId).getOrElse {
                hadTransientFailure = true
                continue
            }
            for (season in snapshot.seasons) {
                if (isStopped) break
                val newEpisodeIds = snapshot.seasonEpisodes(season.id)
                    .filter { it.id !in alreadyDownloaded }
                    .map { it.id }
                if (newEpisodeIds.isNotEmpty()) {
                    downloadIntake.startSeries(
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
