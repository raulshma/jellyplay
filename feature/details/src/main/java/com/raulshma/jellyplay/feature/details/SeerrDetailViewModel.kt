package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeerrDetailViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesStore.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    private val _movieDetails = mutableStateOf<SeerrMovieDetails?>(null)
    val movieDetails: State<SeerrMovieDetails?> = _movieDetails

    private val _tvDetails = mutableStateOf<SeerrTvDetails?>(null)
    val tvDetails: State<SeerrTvDetails?> = _tvDetails

    private val _ratings = mutableStateOf<SeerrRatings?>(null)
    val ratings: State<SeerrRatings?> = _ratings

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _seerrRecommendations = MutableStateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrRecommendations: StateFlow<List<SeerrSearchItem>> = _seerrRecommendations

    private val _seerrSimilar = MutableStateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrSimilar: StateFlow<List<SeerrSearchItem>> = _seerrSimilar

    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _requestResult = MutableStateFlow<SeerrRequestResult?>(null)
    val requestResult: StateFlow<SeerrRequestResult?> = _requestResult

    fun loadDetails(tmdbId: Int, mediaType: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            if (mediaType == "movie") {
                seerrRepository.getMovieDetails(tmdbId).onSuccess {
                    _movieDetails.value = it
                    _ratings.value = it.ratings
                }.onFailure {
                    _error.value = it.message
                }
            } else {
                seerrRepository.getTvDetails(tmdbId).onSuccess {
                    _tvDetails.value = it
                    _ratings.value = it.ratings
                }.onFailure {
                    _error.value = it.message
                }
            }

            // Fetch ratings separately as well if not included
            if (_ratings.value == null) {
                seerrRepository.getRatings(tmdbId, mediaType).onSuccess {
                    _ratings.value = it
                }
            }

            // Load recommendations and similar
            val type = if (mediaType == "movie") MediaType.MOVIE else MediaType.SERIES
            seerrRepository.getRecommendations(tmdbId, type).onSuccess {
                _seerrRecommendations.value = it.results
            }
            seerrRepository.getSimilar(tmdbId, type).onSuccess {
                _seerrSimilar.value = it.results
            }

            _isLoading.value = false
        }
    }

    fun requestMedia(item: SeerrSearchItem, seasons: List<Int>? = null) {
        viewModelScope.launch {
            _requestResult.value = SeerrRequestResult(isLoading = true)
            seerrRepository.requestMedia(item.id, item.mediaType, seasons).onSuccess {
                _requestResult.value = SeerrRequestResult(isLoading = false, success = true)
                // Reload details to update status
                loadDetails(item.id, item.mediaType)
            }.onFailure {
                _requestResult.value = SeerrRequestResult(isLoading = false, success = false, error = it.message)
            }
        }
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    fun getSeerrPosterUrl(path: String?): String? {
        if (path == null) return null
        return "https://image.tmdb.org/t/p/w500$path"
    }
    
    fun getSeerrBackdropUrl(path: String?): String? {
        if (path == null) return null
        return "https://image.tmdb.org/t/p/original$path"
    }
}
