package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OfflineSeriesViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
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
     * Episodes keyed by season id. Loaded once all seasons are known so the UI
     * can switch season tabs without re-fetching.
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
                        val map = ConcurrentHashMap<String, List<OfflineMediaItem>>()
                        coroutineScope {
                            seasonList.map { season ->
                                async { map[season.id] = offlineRepository.getEpisodesForSeason(season.id).first() }
                            }.awaitAll()
                        }
                        flowOf(map.toMap())
                    }
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Aggregated total size across all episodes of this series. */
    val totalSizeBytes: StateFlow<Long> =
        episodes.map { map -> map.values.flatten().sumOf { it.totalSizeBytes } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0L)

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

    fun deleteSeason(seasonId: String) {
        launch { offlineRepository.deleteOfflineSeason(seasonId) }
    }

    fun deleteSeries() {
        val id = _seriesId.value ?: return
        launch { offlineRepository.deleteOfflineSeries(id) }
    }
}
