package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class OfflineLibraryViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    savedStateHandle: SavedStateHandle,
) : JellyPlayViewModel() {

    private val _offlineLibrary = stateFlow<List<OfflineMediaItem>>(emptyList())
    val offlineLibrary: StateFlow<List<OfflineMediaItem>> = _offlineLibrary.flow

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _seriesItem = stateFlow<OfflineMediaItem?>(null)
    val seriesItem = _seriesItem.flow

    private val _seasons = stateFlow<List<OfflineMediaItem>>(emptyList())
    val seasons = _seasons.flow

    private val _episodes = stateFlow<Map<String, List<OfflineMediaItem>>>(emptyMap())
    val episodes = _episodes.flow

    init {
        launch {
            offlineRepository.getOfflineLibrary().collect { items ->
                _offlineLibrary.set(items)
                _isLoading.value = false
            }
        }
    }

    fun loadSeries(seriesId: String) {
        launch {
            val item = offlineRepository.getOfflineItem(seriesId)
            _seriesItem.set(item)

            offlineRepository.getSeasonsForSeries(seriesId).collect { seasonList ->
                _seasons.set(seasonList)

                val episodesMap = ConcurrentHashMap<String, List<OfflineMediaItem>>()
                coroutineScope {
                    seasonList.map { season ->
                        async {
                            val episodeList = offlineRepository.getEpisodesForSeason(season.id).first()
                            episodesMap[season.id] = episodeList
                        }
                    }.awaitAll()
                    _episodes.set(episodesMap.toMap())
                }
            }
        }
    }

    fun deleteEpisode(episodeId: String) {
        launch {
            offlineRepository.deleteOfflineItem(episodeId)
        }
    }

    fun deleteSeason(seasonId: String) {
        launch {
            offlineRepository.deleteOfflineSeason(seasonId)
        }
    }

    fun deleteSeries(seriesId: String) {
        launch {
            offlineRepository.deleteOfflineSeries(seriesId)
        }
    }
}
