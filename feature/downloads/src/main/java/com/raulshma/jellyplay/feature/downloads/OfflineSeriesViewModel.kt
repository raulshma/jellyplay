package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OfflineSeriesViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    private val mediaRepository: MediaRepository,
    private val libraryStore: LibraryStore,
    private val editor: PreferencesEditor,
    @Suppress("unused") savedStateHandle: SavedStateHandle,
) : JellyPlayViewModel() {

    private val _seriesId = MutableStateFlow<String?>(null)

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    val seriesItem: StateFlow<OfflineMediaItem?> =
        _seriesId.flatMapLatest { id -> if (id == null) flowOf(null) else offlineRepository.getOfflineDetail(id) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    val seasons: StateFlow<List<OfflineMediaItem>> =
        _seriesId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else offlineRepository.getSeasonsForSeries(id)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Episodes keyed by season id. Each season's Room flow is subscribed
     * reactively (not snapshotted), so deleting a single episode re-emits that
     * season's flow and the list refreshes on its own — no reload needed.
     */
    val episodes: StateFlow<Map<String, List<OfflineMediaItem>>> =
        _seriesId.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyMap())
            } else {
                offlineRepository.getSeasonsForSeries(id).flatMapLatest { seasonList ->
                    if (seasonList.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        val perSeason: List<Flow<Pair<String, List<OfflineMediaItem>>>> = seasonList.map { season ->
                            offlineRepository.getEpisodesForSeason(season.id).map { eps -> season.id to eps }
                        }
                        combine(perSeason) { pairs -> pairs.toMap() }
                    }
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Aggregated total size across all episodes of this series. */
    val totalSizeBytes: StateFlow<Long> =
        episodes.map { map -> map.values.flatten().sumOf { it.totalSizeBytes } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Number of episodes actually downloaded for this series (across all seasons). */
    val downloadedEpisodeCount: StateFlow<Int> =
        episodes.map { map -> map.values.sumOf { it.size } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Compact vertical episode list preference (shared with the online detail screen). */
    val compactEpisodeList: StateFlow<Boolean> =
        libraryStore.library.map { it.compactEpisodeList }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setCompactEpisodeList(enabled: Boolean) =
        editor.edit { library.setCompactEpisodeList(enabled) }

    /** Drives the screen's data. Called once from a LaunchedEffect(seriesId). */
    fun load(seriesId: String) {
        if (_seriesId.value == seriesId) return
        _isLoading.value = true
        _seriesId.value = seriesId
        launch {
            // Drop the loading flag as soon as the first series row lands.
            offlineRepository.getOfflineDetail(seriesId).first()
            _isLoading.value = false
        }
    }

    fun deleteEpisode(episodeId: String) {
        launch { offlineRepository.deleteOfflineItem(episodeId) }
    }

    /**
     * Deletes a batch of downloaded episodes. Used by the multi-select delete
     * sheet: if the selection covers an entire season, [deleteSeason] is used
     * (one DB transaction + parallel artifact cleanup) so fully-selected seasons
     * are pruned efficiently rather than one episode at a time; the remaining
     * partial-season selections fall back to per-episode [deleteEpisode].
     */
    fun deleteEpisodes(episodeIds: Collection<String>) {
        if (episodeIds.isEmpty()) return
        val targets = episodeIds.toSet()
        // Snapshot episodes once to classify whole-season vs partial selections.
        val currentEpisodes = episodes.value
        val currentSeasons = seasons.value
        launch {
            // For each season where every downloaded episode is selected, drop
            // the season in one call; otherwise delete the selected episodes
            // individually. This keeps whole-season deletes a single transaction.
            val remainingEpisodeIds = mutableSetOf<String>()
            currentSeasons.forEach { season ->
                val seasonEpisodeIds = currentEpisodes[season.id].orEmpty().map { it.id }.toSet()
                if (seasonEpisodeIds.isNotEmpty() && seasonEpisodeIds.all { it in targets }) {
                    offlineRepository.deleteOfflineSeason(season.id)
                } else {
                    seasonEpisodeIds.filter { it in targets }.forEach { remainingEpisodeIds.add(it) }
                }
            }
            // Any selected id not seen under a known season (defensive) — delete
            // it directly so the selection is honored even if the seasons list
            // changed between the sheet snapshot and this call.
            targets
                .filter { it !in currentEpisodes.values.flatten().map { e -> e.id } }
                .forEach { remainingEpisodeIds.add(it) }
            remainingEpisodeIds.forEach { offlineRepository.deleteOfflineItem(it) }
        }
    }

    fun deleteSeason(seasonId: String) {
        launch { offlineRepository.deleteOfflineSeason(seasonId) }
    }

    /**
     * Marks every downloaded episode in [seasonId] (and the season row itself)
     * as watched. Routes through [MediaRepository.markPlayed] so the change is
     * applied to the local offline DB AND enqueued into the playback outbox for
     * server sync on reconnect (or pushed immediately when online) — mirroring
     * the online season-mark path. The batch UPDATE flows back through the
     * reactive [seasons]/[episodes] queries so the UI refreshes on its own.
     */
    fun markSeasonPlayed(seasonId: String) {
        launch { mediaRepository.markPlayed(seasonId) }
    }

    /**
     * Marks every downloaded episode in [seasonId] (and the season row itself)
     * as unwatched, clearing position/percentage. See [markSeasonPlayed]; uses
     * [MediaRepository.markUnplayed] for the same offline-aware sync behavior.
     */
    fun markSeasonUnplayed(seasonId: String) {
        launch { mediaRepository.markUnplayed(seasonId) }
    }

    /**
     * Toggles played state for a single downloaded episode from the long-press
     * quick-action sheet. See [markSeasonPlayed]; uses the same offline-aware
     * sync path so the per-episode change applies locally and outboxes for the
     * server.
     */
    fun markEpisodePlayed(episodeId: String, played: Boolean) {
        launch {
            if (played) mediaRepository.markPlayed(episodeId)
            else mediaRepository.markUnplayed(episodeId)
        }
    }

    /**
     * Toggles favorite for a downloaded episode. Routes through
     * [PlayedStateSync.toggleFavorite], so it works fully offline: the flip is
     * applied locally and staged in the playback outbox for delivery on
     * reconnect.
     */
    fun toggleFavorite(itemId: String) {
        launch { mediaRepository.toggleFavorite(itemId) }
    }

    fun deleteSeries() {
        val id = _seriesId.value ?: return
        launch { offlineRepository.deleteOfflineSeries(id) }
    }
}
