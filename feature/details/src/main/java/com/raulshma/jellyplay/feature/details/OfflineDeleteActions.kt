package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Offline-deletion actions extracted from [DetailViewModel]. Fire-and-forget:
 * holds no state of its own — the UI observes the offline repository's
 * reactivity (and the provider snapshot) for the post-delete refresh.
 *
 * [deleteOfflineEpisodes] reads the current seasons/episodes off the injected
 * providers to classify whole-season vs partial selections, mirroring the
 * former VM logic verbatim.
 */
internal class OfflineDeleteActions(
    private val scope: CoroutineScope,
    private val offlineRepository: OfflineRepository,
    private val episodesProvider: () -> Map<String, List<MediaItem>>,
    private val seasonsProvider: () -> List<MediaItem>,
) {
    /** Deletes a single downloaded item by id. */
    fun deleteOfflineItem(id: String) {
        scope.launch { offlineRepository.deleteOfflineItem(id) }
    }

    /** Deletes a single downloaded episode by id. */
    fun deleteOfflineEpisode(episodeId: String) {
        scope.launch { offlineRepository.deleteOfflineItem(episodeId) }
    }

    /**
     * Deletes a batch of downloaded episodes. Ports
     * `OfflineSeriesViewModel.deleteEpisodes`: if the selection covers every
     * downloaded episode of a season, the whole season is dropped in a single
     * [offlineRepository.deleteOfflineSeason] transaction; the remaining
     * partial-season selections fall back to per-episode
     * [offlineRepository.deleteOfflineItem]. Any selected id not seen under a
     * known season is deleted directly so the selection is honored even if the
     * seasons list changed between the sheet snapshot and this call.
     */
    fun deleteOfflineEpisodes(episodeIds: List<String>) {
        if (episodeIds.isEmpty()) return
        val targets = episodeIds.toSet()
        // Snapshot the current series content once to classify whole-season vs
        // partial selections.
        val currentEpisodes = episodesProvider()
        val currentSeasons = seasonsProvider()
        scope.launch {
            val remainingEpisodeIds = mutableSetOf<String>()
            currentSeasons.forEach { season ->
                val seasonEpisodeIds = currentEpisodes[season.id].orEmpty().map { it.id }.toSet()
                if (seasonEpisodeIds.isNotEmpty() && seasonEpisodeIds.all { it in targets }) {
                    offlineRepository.deleteOfflineSeason(season.id)
                } else {
                    seasonEpisodeIds.filter { it in targets }.forEach { remainingEpisodeIds.add(it) }
                }
            }
            // Defensive: any selected id not present under a known season.
            targets
                .filter { it !in currentEpisodes.values.flatten().map { e -> e.id } }
                .forEach { remainingEpisodeIds.add(it) }
            remainingEpisodeIds.forEach { offlineRepository.deleteOfflineItem(it) }
        }
    }

    /** Drops an entire downloaded season (one DB transaction + artifact cleanup). */
    fun deleteOfflineSeason(seasonId: String) {
        scope.launch { offlineRepository.deleteOfflineSeason(seasonId) }
    }

    /** Drops an entire downloaded series and all its seasons/episodes. */
    fun deleteOfflineSeries(seriesId: String) {
        scope.launch { offlineRepository.deleteOfflineSeries(seriesId) }
    }
}
