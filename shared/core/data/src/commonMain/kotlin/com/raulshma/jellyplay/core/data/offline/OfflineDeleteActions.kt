package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Offline-deletion actions shared by every surface that can delete downloads
 * (media detail, offline home, downloads library). Fire-and-forget: holds no
 * state of its own. Lifted from `feature/details` so the
 * whole-season-collapse algorithm exists exactly once.
 *
 * Constructed per consumer with that consumer's scope and content providers —
 * not a DI bean. After each delete transaction lands, [onContentMutated] fires
 * so consumers whose content is NOT reactively refreshed (the detail screen's
 * catalogue snapshot) can re-resolve; consumers backed by the reactive offline
 * Room flow (home, downloads) leave it at the default no-op.
 *
 * [deleteOfflineEpisodes] reads the current seasons/episodes off the injected
 * providers to classify whole-season vs partial selections:
 *
 * - A selection covering **every** downloaded episode of a season collapses
 *   into a single [OfflineRepository.deleteOfflineSeason] transaction (one DB
 *   write + artifact cleanup instead of N).
 * - Partial-season selections fall back to per-episode
 *   [OfflineRepository.deleteOfflineItem].
 * - A season with zero downloaded episodes never collapses (the empty guard).
 * - Any selected id not seen under a known season is deleted directly so the
 *   selection is honored even if the content snapshot changed between the
 *   sheet and this call (unknown-id defense).
 */
class OfflineDeleteActions(
    private val scope: CoroutineScope,
    private val offlineRepository: OfflineRepository,
    private val episodesProvider: () -> Map<String, List<MediaItem>> = { emptyMap() },
    private val seasonsProvider: () -> List<MediaItem> = { emptyList() },
    private val onContentMutated: () -> Unit = {},
) {
    /**
     * Quick-action routing: a series card deletes the whole series download;
     * anything else deletes the single item. This is the one entry point for
     * quick-action delete sheets (offline home, downloads library).
     */
    fun deleteDownload(item: MediaItem) {
        if (item.mediaType == MediaType.SERIES) {
            deleteOfflineSeries(item.id)
        } else {
            deleteOfflineItem(item.id)
        }
    }

    /** Deletes a single downloaded item by id. */
    fun deleteOfflineItem(id: String) = deleteAndNotify { offlineRepository.deleteOfflineItem(id) }

    /** Deletes a single downloaded episode by id. */
    fun deleteOfflineEpisode(episodeId: String) =
        deleteAndNotify { offlineRepository.deleteOfflineItem(episodeId) }

    /**
     * Deletes a batch of downloaded episodes. Ports the former
     * `OfflineSeriesViewModel.deleteEpisodes` / HomeViewModel
     * `deleteOfflineEpisodes` algorithm; see the class KDoc for the collapse
     * rules. Accepts any [Collection] (the detail sheet selects a `List`, the
     * home sheet a `Set`).
     *
     * [episodes]/[seasons] override the constructor providers per call, read
     * synchronously at entry — the pattern for snapshot-before-dismiss
     * consumers (Home's sheet clears its state while the deletes run, so the
     * constructor providers would read empty after dismissal and the collapse
     * would degrade to per-episode deletes). Both default to whatever the
     * constructor-injected providers return, so provider-wired consumers are
     * unchanged.
     */
    fun deleteOfflineEpisodes(
        episodeIds: Collection<String>,
        episodes: Map<String, List<MediaItem>> = episodesProvider(),
        seasons: List<MediaItem> = seasonsProvider(),
    ) {
        if (episodeIds.isEmpty()) return
        val targets = episodeIds.toSet()
        deleteAndNotify {
            val remainingEpisodeIds = mutableSetOf<String>()
            seasons.forEach { season ->
                val seasonEpisodeIds = episodes[season.id].orEmpty().map { it.id }.toSet()
                if (seasonEpisodeIds.isNotEmpty() && seasonEpisodeIds.all { it in targets }) {
                    offlineRepository.deleteOfflineSeason(season.id)
                } else {
                    seasonEpisodeIds.filter { it in targets }.forEach { remainingEpisodeIds.add(it) }
                }
            }
            // Defensive: any selected id not present under a known season.
            targets
                .filter { it !in episodes.values.flatten().map { e -> e.id } }
                .forEach { remainingEpisodeIds.add(it) }
            remainingEpisodeIds.forEach { offlineRepository.deleteOfflineItem(it) }
        }
    }

    /** Drops an entire downloaded season (one DB transaction + artifact cleanup). */
    fun deleteOfflineSeason(seasonId: String) =
        deleteAndNotify { offlineRepository.deleteOfflineSeason(seasonId) }

    /** Drops an entire downloaded series and all its seasons/episodes. */
    fun deleteOfflineSeries(seriesId: String) =
        deleteAndNotify { offlineRepository.deleteOfflineSeries(seriesId) }

    /**
     * Runs [delete] on [scope], then signals [onContentMutated] exactly once so
     * the consumer can re-resolve now-stale content. Centralizing the
     * launch+notify keeps the per-method bodies at the delete logic only.
     */
    private fun deleteAndNotify(delete: suspend () -> Unit) {
        scope.launch {
            delete()
            onContentMutated()
        }
    }
}
