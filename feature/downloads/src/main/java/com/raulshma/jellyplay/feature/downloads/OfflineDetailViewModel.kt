package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OfflineDetailViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    @Suppress("unused") savedStateHandle: SavedStateHandle,
) : JellyPlayViewModel() {

    private val _itemId = MutableStateFlow<String?>(null)

    val item: StateFlow<OfflineMediaItem?> =
        _itemId.flatMapLatest { id -> if (id == null) flowOf(null) else offlineRepository.getOfflineDetail(id) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Children (e.g. album tracks) for the item, with download rows joined. */
    val children: StateFlow<List<OfflineMediaItem>> =
        _itemId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else offlineRepository.getChildren(id)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Seasons for the loaded item's series. Populated only when the item is an
     * episode (i.e. [OfflineMediaItem.seriesId] is set) so the detail screen can
     * render the same seasons/episodes list the online episode detail shows.
     * Empty otherwise.
     */
    val seasons: StateFlow<List<OfflineMediaItem>> =
        item.flatMapLatest { loaded ->
            val seriesId = loaded?.seriesId
            if (seriesId == null) flowOf(emptyList()) else offlineRepository.getSeasonsForSeries(seriesId)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Episodes keyed by season id for the loaded item's series. Each season's
     * Room flow is subscribed reactively (not snapshotted), so deleting a single
     * episode re-emits that season's flow and the list refreshes on its own —
     * no reload needed. Mirrors OfflineSeriesViewModel.episodes.
     */
    val episodes: StateFlow<Map<String, List<OfflineMediaItem>>> =
        seasons.flatMapLatest { seasonList ->
            if (seasonList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val perSeason: List<Flow<Pair<String, List<OfflineMediaItem>>>> = seasonList.map { season ->
                    offlineRepository.getEpisodesForSeason(season.id).map { eps -> season.id to eps }
                }
                combine(perSeason) { pairs -> pairs.toMap() }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Drives the screen's data. Called once from a LaunchedEffect(itemId). */
    fun load(itemId: String) {
        if (_itemId.value == itemId) return
        _itemId.value = itemId
    }

    /**
     * Builds the primary-image URL for a cast member. The matching image is
     * preloaded into Coil's cache at download time, so this is a cache hit when
     * offline. Used by the cast row in the offline detail screen.
     */
    fun personImageUrl(personId: String): String =
        playbackRepository.getImageUrl(personId, maxWidth = 200)

    fun delete(onDone: () -> Unit) {
        val id = _itemId.value ?: return
        launch {
            offlineRepository.deleteOfflineItem(id)
            onDone()
        }
    }

    /** Deletes a single episode from the embedded seasons list. */
    fun deleteEpisode(episodeId: String) {
        launch { offlineRepository.deleteOfflineItem(episodeId) }
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
}
