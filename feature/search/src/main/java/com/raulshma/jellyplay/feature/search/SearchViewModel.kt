package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchFilters(
    val mediaTypes: List<MediaType> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
) : JellyPlayViewModel() {

    private val _query = composeState("")
    var query: String
        get() = _query.value
        private set(value) { _query.value = value }

    private val _filters = stateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.flow

    private val _genres = stateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.flow

    private val _showFilters = stateFlow(false)
    val showFilters: StateFlow<Boolean> = _showFilters.flow

    private val _seerrResults = stateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrResults: StateFlow<List<SeerrSearchItem>> = _seerrResults.flow

    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val isSeerrSearchEnabled: StateFlow<Boolean> = seerrRepository.isSearchEnabled()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val queryFlow = stateFlow("")

    private var seerrSearchJob: Job? = null

    val pagedResults: Flow<PagingData<MediaItem>> = combine(
        queryFlow.flow.debounce(400).distinctUntilChanged(),
        _filters.flow,
    ) { q, f -> q to f }
        .flatMapLatest { (currentQuery, filters) ->
            seerrSearchJob?.cancel()
            if (currentQuery.isBlank()) {
                _seerrResults.set(emptyList())
                flowOf(PagingData.empty())
            } else {
                seerrSearchJob = launch { searchSeerr(currentQuery) }
                mediaRepository.searchPaged(
                    query = currentQuery,
                    mediaTypes = filters.mediaTypes.ifEmpty { null },
                )
            }
        }
        .cachedIn(scope)

    init {
        loadGenres()
    }

    fun search(newQuery: String) {
        _query.value = newQuery
        queryFlow.set(newQuery)
    }

    fun updateFilters(newFilters: SearchFilters) {
        _filters.set(newFilters)
    }

    fun toggleMediaType(mediaType: MediaType) {
        _filters.update { current ->
            val types = current.mediaTypes
            current.copy(
                mediaTypes = if (mediaType in types) types - mediaType else types + mediaType,
            )
        }
    }

    fun toggleShowFilters() {
        _showFilters.set(!_showFilters.value)
    }

    fun clearFilters() {
        _filters.set(SearchFilters())
    }

    private fun loadGenres() {
        launch {
            mediaRepository.getGenres()
                .onSuccess { _genres.set(it) }
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getSeerrPosterUrl(posterPath: String?): String? =
        posterPath?.let { buildPosterUrl(it) }

    private suspend fun searchSeerr(query: String) {
        try {
            val connected = seerrRepository.isConnected().first()
            val enabled = seerrRepository.isSearchEnabled().first()
            if (!connected || !enabled) {
                _seerrResults.set(emptyList())
                return
            }
            seerrRepository.search(query)
                .onSuccess { response ->
                    _seerrResults.set(response.results.take(10))
                }
                .onFailure {
                    _seerrResults.set(emptyList())
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            _seerrResults.set(emptyList())
        }
    }

    private val _requestResult = stateFlow<RequestResult?>(null)
    val requestResult: StateFlow<RequestResult?> = _requestResult.flow

    private val _radarrServers = stateFlow<List<SeerrRadarrServiceDetail>>(emptyList())
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> = _radarrServers.flow

    private val _sonarrServers = stateFlow<List<SeerrSonarrServiceDetail>>(emptyList())
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> = _sonarrServers.flow

    private val _isLoadingSeerrServices = stateFlow(false)
    val isLoadingSeerrServices: StateFlow<Boolean> = _isLoadingSeerrServices.flow

    private val _tvSeasons = stateFlow<List<SeerrSeason>>(emptyList())
    val tvSeasons: StateFlow<List<SeerrSeason>> = _tvSeasons.flow

    fun requestSeerrMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) {
        launch {
            _requestResult.set(RequestResult(isLoading = true))
            seerrRequestDelegate.requestMedia(
                mediaType = item.mediaType,
                tmdbId = item.id,
                seasons = seasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
                tags = tags,
            ).onSuccess {
                _requestResult.set(RequestResult(success = true))
            }.onFailure {
                _requestResult.set(RequestResult(error = it.message ?: "Request failed"))
            }
        }
    }

    fun clearRequestResult() {
        _requestResult.set(null)
    }

    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        launch {
            seerrRequestDelegate.prefetchDetails(tmdbId, mediaType)
            onDone()
        }
    }

    fun loadSeerrServiceDetails(mediaType: String) {
        launch {
            _isLoadingSeerrServices.set(true)
            try {
                val result = seerrRequestDelegate.fetchServiceDetails(mediaType)
                _radarrServers.set(result.radarrServers)
                _sonarrServers.set(result.sonarrServers)
            } finally {
                _isLoadingSeerrServices.set(false)
            }
        }
    }

    fun loadTvSeasons(tmdbId: Int) {
        launch {
            _tvSeasons.set(emptyList())
            val seasons = seerrRequestDelegate.fetchTvSeasons(tmdbId)
            _tvSeasons.set(seasons)
        }
    }
}

data class RequestResult(
    val isLoading: Boolean = false,
    val success: Boolean? = null,
    val error: String? = null,
)
