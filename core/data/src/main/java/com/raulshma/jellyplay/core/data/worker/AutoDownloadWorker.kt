package com.raulshma.jellyplay.core.data.worker

import android.content.Context
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

        for (seriesId in seriesIds) {
            val alreadyDownloaded = downloadRepository.getDownloadedEpisodeIdsForSeries(seriesId)
            val seasons = mediaRepository.getSeasons(seriesId).getOrElse { emptyList() }
            for (season in seasons) {
                val episodes = mediaRepository.getEpisodes(seriesId, season.id).getOrElse { emptyList() }
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

        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "com.raulshma.jellyplay.work.auto_download"
    }
}
