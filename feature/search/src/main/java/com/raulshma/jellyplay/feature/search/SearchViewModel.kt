package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    var query by mutableStateOf("")
        private set

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _showFilters = MutableStateFlow(false)
    val showFilters: StateFlow<Boolean> = _showFilters.asStateFlow()

    // Seerr integration state
    private val _seerrResults = MutableStateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrResults: StateFlow<List<SeerrSearchItem>> = _seerrResults.asStateFlow()

    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isSeerrSearchEnabled: StateFlow<Boolean> = seerrRepository.isSearchEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val queryFlow = MutableStateFlow("")

    val pagedResults: Flow<PagingData<MediaItem>> = queryFlow
        .debounce(400)
        .distinctUntilChanged()
        .flatMapLatest { currentQuery ->
            if (currentQuery.isBlank()) {
                _seerrResults.value = emptyList()
                flowOf(PagingData.empty())
            } else {
                _isSearching.value = true
                // Run seerr search inline so flatMapLatest cancellation propagates
                searchSeerr(currentQuery)
                mediaRepository.searchPaged(
                    query = currentQuery,
                    mediaTypes = _filters.value.mediaTypes.ifEmpty { null },
                ).also { _isSearching.value = false }
            }
        }
        .cachedIn(viewModelScope)

    init {
        loadGenres()
    }

    fun search(newQuery: String) {
        query = newQuery
        queryFlow.value = newQuery
    }

    fun updateFilters(newFilters: SearchFilters) {
        _filters.update { newFilters }
        if (query.isNotBlank()) {
            queryFlow.value = query
        }
    }

    fun toggleMediaType(mediaType: MediaType) {
        _filters.update { current ->
            val types = current.mediaTypes
            current.copy(
                mediaTypes = if (mediaType in types) types - mediaType else types + mediaType,
            )
        }
        if (query.isNotBlank()) {
            queryFlow.value = query
        }
    }

    fun toggleShowFilters() {
        _showFilters.value = !_showFilters.value
    }

    fun clearFilters() {
        _filters.update { SearchFilters() }
        if (query.isNotBlank()) {
            queryFlow.value = query
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            mediaRepository.getGenres()
                .onSuccess { _genres.value = it }
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getSeerrPosterUrl(posterPath: String?): String? =
        posterPath?.let { buildPosterUrl(it) }

    private suspend fun searchSeerr(query: String) {
        _seerrResults.value = emptyList()
        try {
            val connected = seerrRepository.isConnected().first()
            val enabled = seerrRepository.isSearchEnabled().first()
            if (!connected || !enabled) return
            seerrRepository.search(query)
                .onSuccess { response ->
                    _seerrResults.value = response.results.take(10)
                }
                .onFailure {
                    _seerrResults.value = emptyList()
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _seerrResults.value = emptyList()
        }
    }

    private val _requestResult = MutableStateFlow<RequestResult?>(null)
    val requestResult: StateFlow<RequestResult?> = _requestResult.asStateFlow()

    // Service details for request dialog
    private val _radarrServers = MutableStateFlow<List<SeerrRadarrServiceDetail>>(emptyList())
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> = _radarrServers.asStateFlow()

    private val _sonarrServers = MutableStateFlow<List<SeerrSonarrServiceDetail>>(emptyList())
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> = _sonarrServers.asStateFlow()

    private val _isLoadingSeerrServices = MutableStateFlow(false)
    val isLoadingSeerrServices: StateFlow<Boolean> = _isLoadingSeerrServices.asStateFlow()

    // TV details for season data (fetched on-demand)
    private val _tvSeasons = MutableStateFlow<List<SeerrSeason>>(emptyList())
    val tvSeasons: StateFlow<List<SeerrSeason>> = _tvSeasons.asStateFlow()

    fun requestSeerrMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) {
        viewModelScope.launch {
            _requestResult.value = RequestResult(isLoading = true)
            seerrRepository.requestMedia(
                mediaType = item.mediaType,
                tmdbId = item.id,
                seasons = seasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
                tags = tags,
            ).onSuccess {
                _requestResult.value = RequestResult(success = true)
            }.onFailure {
                _requestResult.value = RequestResult(error = it.message ?: "Request failed")
            }
        }
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    /**
     * Pre-fetches Seerr detail data (details + ratings + recommendations) so the
     * detail screen loads instantly.  Callers should show a loading animation on
     * the card while this runs, then navigate when the returned job completes.
     */
    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                if (mediaType == "movie") {
                    seerrRepository.getMovieDetails(tmdbId)
                } else {
                    seerrRepository.getTvDetails(tmdbId)
                }
                val type = if (mediaType == "movie") MediaType.MOVIE else MediaType.SERIES
                launch { seerrRepository.getRatings(tmdbId, mediaType) }
                launch { seerrRepository.getRecommendations(tmdbId, type) }
                launch { seerrRepository.getSimilar(tmdbId, type) }
            } catch (_: Exception) {
                // Detail screen will retry on failure
            }
            onDone()
        }
    }

    /**
     * Fetches service details (Radarr/Sonarr) for the request dialog.
     * Uses /service/ endpoints matching the Seerr web UI flow.
     */
    fun loadSeerrServiceDetails(mediaType: String) {
        viewModelScope.launch {
            _isLoadingSeerrServices.value = true
            try {
                if (mediaType == "movie") {
                    seerrRepository.getServiceRadarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceRadarrDetail(server.id).getOrNull()
                        }
                        _radarrServers.value = details
                    }
                } else {
                    seerrRepository.getServiceSonarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceSonarrDetail(server.id).getOrNull()
                        }
                        _sonarrServers.value = details
                    }
                }
            } finally {
                _isLoadingSeerrServices.value = false
            }
        }
    }

    /**
     * Fetches TV details on-demand to get season data for the request dialog.
     */
    fun loadTvSeasons(tmdbId: Int) {
        viewModelScope.launch {
            _tvSeasons.value = emptyList()
            seerrRepository.getTvDetails(tmdbId).onSuccess { details ->
                _tvSeasons.value = details.seasons.filter { it.seasonNumber > 0 }
            }
        }
    }
}

data class RequestResult(
    val isLoading: Boolean = false,
    val success: Boolean? = null,
    val error: String? = null,
)
