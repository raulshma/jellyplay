package com.raulshma.jellyplay.feature.details

import android.util.Log
import androidx.compose.runtime.State
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrEpisode
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.network.seerr.buildBackdropUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "SeerrDetailVM"

@HiltViewModel
class SeerrDetailViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val preferencesStore: UserPreferencesStore,
    private val seerrPreferencesStore: SeerrPreferencesStore,
) : JellyPlayViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesStore.preferences
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    val seerrPreferences: StateFlow<SeerrPreferences> = seerrPreferencesStore.preferences
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SeerrPreferences()
        )

    private val _movieDetails = composeState<SeerrMovieDetails?>(null)
    val movieDetails: State<SeerrMovieDetails?> = _movieDetails.asState()

    private val _tvDetails = composeState<SeerrTvDetails?>(null)
    val tvDetails: State<SeerrTvDetails?> = _tvDetails.asState()

    private val _ratings = composeState<SeerrRatings?>(null)
    val ratings: State<SeerrRatings?> = _ratings.asState()

    private val _isLoading = composeState(false)
    val isLoading: State<Boolean> = _isLoading.asState()

    private val _error = composeState<String?>(null)
    val error: State<String?> = _error.asState()

    private val _seerrRecommendations = stateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrRecommendations = _seerrRecommendations.flow

    private val _seerrSimilar = stateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrSimilar = _seerrSimilar.flow

    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val seerrRequestState = com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder(scope, seerrRequestDelegate)
    val requestResult get() = seerrRequestState.requestResult
    val radarrServers get() = seerrRequestState.radarrServers
    val sonarrServers get() = seerrRequestState.sonarrServers
    val isLoadingServices get() = seerrRequestState.isLoadingServices

    private val _selectedSeasonNumber = composeState<Int?>(null)
    val selectedSeasonNumber: State<Int?> = _selectedSeasonNumber.asState()

    private val _episodesBySeason = stateFlow<Map<Int, List<SeerrEpisode>>>(emptyMap())
    val episodesBySeason = _episodesBySeason.flow

    private val _isLoadingEpisodes = stateFlow(false)
    val isLoadingEpisodes = _isLoadingEpisodes.flow

    fun loadDetails(tmdbId: Int, mediaType: String) {
        launch {
            _isLoading.value = true
            _error.value = null
            _ratings.value = null
            _movieDetails.value = null
            _tvDetails.value = null
            _seerrRecommendations.set(emptyList())
            _seerrSimilar.set(emptyList())
            _selectedSeasonNumber.value = null
            _episodesBySeason.set(emptyMap())
            _isLoadingEpisodes.set(false)

            var hasRatings = false

            try {
                if (mediaType == "movie") {
                    seerrRepository.getMovieDetails(tmdbId).onSuccess {
                        _movieDetails.value = it
                        val ratings = it.ratings
                        if (ratings?.rt != null || ratings?.imdb != null) {
                            hasRatings = true
                        }
                        updateRatings(ratings, it.voteAverage)
                    }.onFailure {
                        _error.value = it.message
                    }
                } else {
                    seerrRepository.getTvDetails(tmdbId).onSuccess {
                        _tvDetails.value = it
                        val ratings = it.ratings
                        if (ratings?.rt != null || ratings?.imdb != null) {
                            hasRatings = true
                        }
                        updateRatings(ratings, it.voteAverage)
                    }.onFailure {
                        _error.value = it.message
                    }
                }

                val type = if (mediaType == "movie") MediaType.MOVIE else MediaType.SERIES

                coroutineScope {
                    val ratingsDeferred = if (hasRatings) {
                        async { null }
                    } else {
                        async {
                            seerrRepository.getRatings(tmdbId, mediaType).getOrNull()
                        }
                    }
                    val recommendationsDeferred = async {
                        seerrRepository.getRecommendations(tmdbId, type).getOrNull()
                    }
                    val similarDeferred = async {
                        seerrRepository.getSimilar(tmdbId, type).getOrNull()
                    }

                    ratingsDeferred.await()?.let {
                        updateRatings(it, null)
                    }

                    recommendationsDeferred.await()?.let {
                        _seerrRecommendations.set(it.results)
                    }

                    similarDeferred.await()?.let {
                        _seerrSimilar.set(it.results)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Seerr details: ${e.message}")
            }

            _isLoading.value = false
        }
    }

    private fun updateRatings(newRatings: SeerrRatings?, tmdbScore: Float?) {
        val current = _ratings.value ?: SeerrRatings()
        val merged = newRatings ?: current

        _ratings.value = merged.copy(
            rt = merged.rt ?: current.rt,
            imdb = merged.imdb ?: current.imdb,
            tmdb = merged.tmdb ?: current.tmdb ?: tmdbScore?.let { com.raulshma.jellyplay.core.model.seerr.SeerrTmdbRating(rating = it) }
        )
    }

    fun loadServiceDetails(mediaType: String) = seerrRequestState.loadServiceDetails(mediaType)

    fun toggleSeason(tvId: Int, seasonNumber: Int) {
        if (_selectedSeasonNumber.value == seasonNumber) {
            _selectedSeasonNumber.value = null
            return
        }
        _selectedSeasonNumber.value = seasonNumber
        if (!_episodesBySeason.value.containsKey(seasonNumber)) {
            loadSeasonEpisodes(tvId, seasonNumber)
        }
    }

    private fun loadSeasonEpisodes(tvId: Int, seasonNumber: Int) {
        launch {
            _isLoadingEpisodes.set(true)
            try {
                seerrRepository.getTvSeasonDetails(tvId, seasonNumber).onSuccess { detail ->
                    val current = _episodesBySeason.value.toMutableMap()
                    current[seasonNumber] = detail.episodes
                    _episodesBySeason.set(current)
                }
            } catch (_: Exception) {}
            _isLoadingEpisodes.set(false)
        }
    }

    fun requestMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) {
        launch {
            seerrRequestState.setRequestResult(SeerrRequestResult(isLoading = true))
            seerrRequestDelegate.requestMedia(
                mediaType = item.mediaType,
                tmdbId = item.id,
                seasons = seasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
                tags = tags,
            ).onSuccess {
                seerrRequestState.setRequestResult(SeerrRequestResult(isLoading = false, success = true))
                val currentMovie = _movieDetails.value
                val currentTv = _tvDetails.value
                val movieMediaInfo = currentMovie?.mediaInfo
                val tvMediaInfo = currentTv?.mediaInfo
                if (movieMediaInfo?.tmdbId == item.id) {
                    _movieDetails.value = currentMovie.copy(
                        mediaInfo = movieMediaInfo.copy(status = com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus.PENDING.value)
                    )
                } else if (tvMediaInfo?.tmdbId == item.id) {
                    _tvDetails.value = currentTv.copy(
                        mediaInfo = tvMediaInfo.copy(status = com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus.PENDING.value)
                    )
                }
            }.onFailure {
                seerrRequestState.setRequestResult(SeerrRequestResult(isLoading = false, success = false, error = it.message))
            }
        }
    }

    fun clearRequestResult() = seerrRequestState.clearRequestResult()

    fun getSeerrPosterUrl(path: String?): String? = buildPosterUrl(path)

    fun getSeerrBackdropUrl(path: String?): String? = buildBackdropUrl(path)

    fun prefetchRelatedDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) =
        seerrRequestState.prefetchDetails(tmdbId, mediaType, onDone)
}
