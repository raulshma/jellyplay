package com.raulshma.jellyplay.feature.details

import android.util.Log
import androidx.compose.runtime.State
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
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
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
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

    private val _requestResult = stateFlow<SeerrRequestResult?>(null)
    val requestResult = _requestResult.flow

    private val _radarrServers = stateFlow<List<SeerrRadarrServiceDetail>>(emptyList())
    val radarrServers = _radarrServers.flow

    private val _sonarrServers = stateFlow<List<SeerrSonarrServiceDetail>>(emptyList())
    val sonarrServers = _sonarrServers.flow

    private val _isLoadingServices = stateFlow(false)
    val isLoadingServices = _isLoadingServices.flow

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

    /**
     * Fetches service details (Radarr/Sonarr) for the request dialog.
     * Uses /service/ endpoints matching the Seerr web UI flow.
     */
    fun loadServiceDetails(mediaType: String) {
        launch {
            _isLoadingServices.set(true)
            try {
                if (mediaType == "movie") {
                    seerrRepository.getServiceRadarrServers().onSuccess { servers ->
                        Log.d(TAG, "Found ${servers.size} Radarr servers via /service/")
                        val details = coroutineScope {
                            servers.map { server ->
                                async {
                                    val result = seerrRepository.getServiceRadarrDetail(server.id)
                                    if (result.isFailure) {
                                        Log.e(TAG, "Failed to get Radarr service detail for server ${server.id}: ${result.exceptionOrNull()?.message}")
                                    }
                                    result.getOrNull()
                                }
                            }.awaitAll().filterNotNull()
                        }
                        Log.d(TAG, "Loaded ${details.size} Radarr service details")
                        _radarrServers.set(details)
                    }
                } else {
                    seerrRepository.getServiceSonarrServers().onSuccess { servers ->
                        Log.d(TAG, "Found ${servers.size} Sonarr servers via /service/")
                        val details = coroutineScope {
                            servers.map { server ->
                                async {
                                    val result = seerrRepository.getServiceSonarrDetail(server.id)
                                    if (result.isFailure) {
                                        Log.e(TAG, "Failed to get Sonarr service detail for server ${server.id}: ${result.exceptionOrNull()?.message}")
                                    }
                                    result.getOrNull()
                                }
                            }.awaitAll().filterNotNull()
                        }
                        Log.d(TAG, "Loaded ${details.size} Sonarr service details")
                        _sonarrServers.set(details)
                    }
                }
            } finally {
                _isLoadingServices.set(false)
            }
        }
    }

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
            _requestResult.set(SeerrRequestResult(isLoading = true))
            seerrRepository.requestMedia(
                tmdbId = item.id,
                mediaType = item.mediaType,
                seasons = seasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
                tags = tags,
            ).onSuccess {
                _requestResult.set(SeerrRequestResult(isLoading = false, success = true))
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
                _requestResult.set(SeerrRequestResult(isLoading = false, success = false, error = it.message))
            }
        }
    }

    fun clearRequestResult() {
        _requestResult.set(null)
    }

    fun getSeerrPosterUrl(path: String?): String? {
        if (path == null) return null
        return "https://image.tmdb.org/t/p/w500$path"
    }

    fun getSeerrBackdropUrl(path: String?): String? {
        if (path == null) return null
        return "https://image.tmdb.org/t/p/w1280$path"
    }

    fun prefetchRelatedDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        launch {
            try {
                coroutineScope {
                    if (mediaType == "movie") {
                        seerrRepository.getMovieDetails(tmdbId)
                    } else {
                        seerrRepository.getTvDetails(tmdbId)
                    }
                    val type = if (mediaType == "movie") MediaType.MOVIE else MediaType.SERIES
                    launch { seerrRepository.getRatings(tmdbId, mediaType) }
                    launch { seerrRepository.getRecommendations(tmdbId, type) }
                    launch { seerrRepository.getSimilar(tmdbId, type) }
                }
            } catch (_: Exception) {
            }
            onDone()
        }
    }
}
