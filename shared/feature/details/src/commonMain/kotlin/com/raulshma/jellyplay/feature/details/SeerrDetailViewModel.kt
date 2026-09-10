package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSnapshot
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SeerrDetailPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrEpisode
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.withPendingRequest
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.model.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.model.seerr.buildBackdropUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val TAG = "SeerrDetailVM"

class SeerrDetailViewModel constructor(
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val projections: PreferenceProjections,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    /** Artwork theme + inline-trailer autoplay, projected centrally off the store slices. */
    val preferences: StateFlow<SeerrDetailPreferences> = projections.seerrDetailPreferences

    val seerrPreferences: StateFlow<SeerrPreferences> = seerrPreferencesStore.preferences

    // Single source of truth for Seerr-detail state. All mutations funnel
    // through [_uiState.update]. Seerr request state (service details, tv
    // seasons, request result) lives in the holder below and is exposed as
    // its single [seerrSnapshot] — it is not part of the atomic content
    // snapshot.
    private val _uiState = MutableStateFlow(SeerrDetailUiState())

    private val seerrRequestState = SeerrRequestStateHolder(scope, seerrRequestDelegate)

    /** Atomic snapshot of the Seerr-detail screen content state. */
    val uiState: StateFlow<SeerrDetailUiState> = _uiState
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrDetailUiState())

    // Seerr request lifecycle state (read by the SeerrRequestDialog). The
    // holder's snapshot is the single interface — commands go through the
    // wrappers below.
    val seerrSnapshot: StateFlow<SeerrRequestSnapshot> = seerrRequestState.snapshotIn(scope)

    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun loadDetails(tmdbId: Int, mediaType: String) {
        launch {
            // Normalize once so every downstream comparison is case-insensitive
            // and consistent (callers may pass "movie", "Movie", "tv", "TV", …).
            val isMovie = mediaType.equals("movie", ignoreCase = true)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    ratings = null,
                    movieDetails = null,
                    tvDetails = null,
                    jellyfinItemId = null,
                    recommendations = emptyList(),
                    similar = emptyList(),
                    selectedSeasonNumber = null,
                    episodesBySeason = emptyMap(),
                    isLoadingEpisodes = false,
                )
            }

            var hasRatings = false

            try {
                if (isMovie) {
                    seerrRepository.getMovieDetails(tmdbId).onSuccess { details ->
                        _uiState.update { it.copy(movieDetails = details) }
                        val ratings = details.ratings
                        if (ratings?.rt != null || ratings?.imdb != null) {
                            hasRatings = true
                        }
                        updateRatings(ratings, details.voteAverage)
                    }.onFailure {
                        _uiState.update { state -> state.copy(error = it.message) }
                    }
                } else {
                    seerrRepository.getTvDetails(tmdbId).onSuccess { details ->
                        _uiState.update { it.copy(tvDetails = details) }
                        val ratings = details.ratings
                        if (ratings?.rt != null || ratings?.imdb != null) {
                            hasRatings = true
                        }
                        updateRatings(ratings, details.voteAverage)
                    }.onFailure {
                        _uiState.update { state -> state.copy(error = it.message) }
                    }
                }

                val type = if (isMovie) MediaType.MOVIE else MediaType.SERIES

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

                    recommendationsDeferred.await()?.let { result ->
                        _uiState.update { it.copy(recommendations = result.results) }
                    }

                    similarDeferred.await()?.let { result ->
                        _uiState.update { it.copy(similar = result.results) }
                    }
                }

                // Once details are loaded, try to resolve the Jellyfin library item
                // so the "Available" action can open it directly. Best-effort: a
                // null result simply leaves the button disabled.
                resolveJellyfinItemId(tmdbId, mediaType)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Seerr details: ${e.message}")
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Resolves the Jellyfin item id for the loaded Seerr media by querying the
     * library against provider ids (tmdb first, then tvdb/imdb as fallbacks).
     * Only attempts resolution when the item is reported as available on the
     * server. Updates [SeerrDetailUiState.jellyfinItemId] on success.
     */
    private suspend fun resolveJellyfinItemId(tmdbId: Int, mediaType: String) {
        val isMovie = mediaType.equals("movie", ignoreCase = true)
        val state = _uiState.value
        val movie = state.movieDetails
        val tv = state.tvDetails
        val mediaInfo = movie?.mediaInfo ?: tv?.mediaInfo
        val status = mediaInfo?.status ?: 0
        val mediaStatus = SeerrMediaStatus.fromValue(status)
        if (mediaStatus != SeerrMediaStatus.AVAILABLE &&
            mediaStatus != SeerrMediaStatus.PARTIALLY_AVAILABLE
        ) return

        // Provider candidates in priority order. tmdb is the primary id Seerr tracks;
        // tvdb/imdb are fallbacks that may be present on the detail's externalIds.
        val candidates = buildList {
            add("tmdb" to tmdbId.toString())
            val externalIds = if (isMovie) movie?.externalIds else tv?.externalIds
            externalIds?.tvdbId?.let { add("tvdb" to it.toString()) }
            externalIds?.imdbId?.let { add("imdb" to it) }
        }

        for ((provider, id) in candidates) {
            val result = mediaRepository.findItemByProviderId(provider, id).getOrNull()
            if (!result.isNullOrBlank()) {
                _uiState.update { it.copy(jellyfinItemId = result) }
                return
            }
        }
    }

    private fun updateRatings(newRatings: SeerrRatings?, tmdbScore: Float?) {
        _uiState.update { state ->
            val current = state.ratings ?: SeerrRatings()
            val merged = newRatings ?: current
            state.copy(
                ratings = merged.copy(
                    rt = merged.rt ?: current.rt,
                    imdb = merged.imdb ?: current.imdb,
                    tmdb = merged.tmdb ?: current.tmdb ?: tmdbScore?.let {
                        com.raulshma.jellyplay.core.model.seerr.SeerrTmdbRating(rating = it)
                    },
                ),
            )
        }
    }

    /**
     * Opens the Seerr request dialog for [item]: the item plus the open
     * cascade (service details, TV seasons for tv) are owned by the holder —
     * the screen's dialog renders from the snapshot's `dialogItem`.
     */
    fun openRequestDialog(item: SeerrSearchItem) = seerrRequestState.openRequestDialog(item)

    /** Closes the dialog and clears the last request result (holder-owned ordering). */
    fun dismissRequestDialog() = seerrRequestState.dismissRequestDialog()

    fun toggleSeason(tvId: Int, seasonNumber: Int) {
        if (_uiState.value.selectedSeasonNumber == seasonNumber) {
            _uiState.update { it.copy(selectedSeasonNumber = null) }
            return
        }
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
        if (!_uiState.value.episodesBySeason.containsKey(seasonNumber)) {
            loadSeasonEpisodes(tvId, seasonNumber)
        }
    }

    private fun loadSeasonEpisodes(tvId: Int, seasonNumber: Int) {
        launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            try {
                seerrRepository.getTvSeasonDetails(tvId, seasonNumber).onSuccess { detail ->
                    _uiState.update {
                        it.copy(episodesBySeason = it.episodesBySeason + (seasonNumber to detail.episodes))
                    }
                }
            } catch (_: Exception) {
            }
            _uiState.update { it.copy(isLoadingEpisodes = false) }
        }
    }

    /**
     * Requests [item] through the holder, which owns the loading/success/
     * error result choreography. The only screen-local concern is the
     * optimistic PENDING flip on the loaded detail, applied via the
     * holder's `onSuccess` hook (the match-on-detail-id rule lives in the
     * model-level [withPendingRequest] helpers).
     */
    fun requestMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) {
        seerrRequestState.requestMedia(
            item = item,
            seasons = seasons,
            serverId = serverId,
            profileId = profileId,
            rootFolder = rootFolder,
            tags = tags,
            onSuccess = {
                _uiState.update { state ->
                    state.copy(
                        movieDetails = state.movieDetails?.withPendingRequest(item),
                        tvDetails = state.tvDetails?.withPendingRequest(item),
                    )
                }
            },
        )
    }

    fun getSeerrPosterUrl(path: String?): String? = buildPosterUrl(path)

    fun getSeerrBackdropUrl(path: String?): String? = buildBackdropUrl(path)

    fun prefetchRelatedDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) =
        seerrRequestState.prefetchDetails(tmdbId, mediaType, onDone)
}
