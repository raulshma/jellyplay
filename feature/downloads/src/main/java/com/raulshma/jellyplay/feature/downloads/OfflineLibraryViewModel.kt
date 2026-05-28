package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineLibraryViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val offlineLibrary: StateFlow<List<OfflineMediaItem>> = offlineRepository.getOfflineLibrary()
        .let { flow ->
            val stateFlow = MutableStateFlow<List<OfflineMediaItem>>(emptyList())
            viewModelScope.launch { flow.collect { stateFlow.value = it } }
            stateFlow
        }

    var isLoading by mutableStateOf(true)
        private set

    private val _seriesItem = MutableStateFlow<OfflineMediaItem?>(null)
    val seriesItem: StateFlow<OfflineMediaItem?> = _seriesItem.asStateFlow()

    private val _seasons = MutableStateFlow<List<OfflineMediaItem>>(emptyList())
    val seasons: StateFlow<List<OfflineMediaItem>> = _seasons.asStateFlow()

    private val _episodes = MutableStateFlow<Map<String, List<OfflineMediaItem>>>(emptyMap())
    val episodes: StateFlow<Map<String, List<OfflineMediaItem>>> = _episodes.asStateFlow()

    init {
        viewModelScope.launch {
            offlineRepository.getOfflineLibrary().collect {
                isLoading = false
            }
        }
    }

    fun loadSeries(seriesId: String) {
        viewModelScope.launch {
            val item = offlineRepository.getOfflineItem(seriesId)
            _seriesItem.value = item

            offlineRepository.getSeasonsForSeries(seriesId).collect { seasonList ->
                _seasons.value = seasonList

                // Launch a separate coroutine for each season's episodes to avoid
                // the terminal inner collect blocking the outer loop
                val episodesMap = mutableMapOf<String, List<OfflineMediaItem>>()
                for (season in seasonList) {
                    launch {
                        offlineRepository.getEpisodesForSeason(season.id).collect { episodeList ->
                            episodesMap[season.id] = episodeList
                            _episodes.value = episodesMap.toMap()
                        }
                    }
                }
            }
        }
    }

    fun deleteEpisode(episodeId: String) {
        viewModelScope.launch {
            offlineRepository.deleteOfflineItem(episodeId)
        }
    }

    fun deleteSeason(seasonId: String) {
        viewModelScope.launch {
            offlineRepository.deleteOfflineSeason(seasonId)
        }
    }

    fun deleteSeries(seriesId: String) {
        viewModelScope.launch {
            offlineRepository.deleteOfflineSeries(seriesId)
        }
    }
}
