package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loads the full TMDB review list for the Reviews screen. Follows the
 * [CastAndCrewViewModel] shape — the item resolution rides the same cached
 * `getMediaDetail` path the detail screen warms (usually a cache hit when
 * arriving from a loaded detail screen) — and the reviews themselves ride the
 * SAME seam the detail screen's `loadSeerrData` reviews leg uses
 * ([SeerrRepository.getTmdbReviews], TMDB-direct and detail-cache-backed), so
 * returning here after the detail screen already loaded is a cache hit, not a
 * refetch. Unlike the detail screen's take-limited leg, the full list lands —
 * that is the screen's whole point.
 */
class ReviewsViewModel constructor(
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<ReviewsUiState>(ReviewsUiState.Loading)
    val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()

    fun load(itemId: String) {
        _uiState.value = ReviewsUiState.Loading
        launch {
            // No feature-level retry: the repository paths already retry
            // (and coordinate retry with address failover) in the engine.
            val detail = mediaRepository.getMediaDetail(itemId)
            val resolved = detail.fold(
                onSuccess = { loaded ->
                    val mediaType = loaded.item.mediaType
                    val tmdbId = resolveTmdbId(loaded)
                    if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) {
                        emptyList()
                    } else if (tmdbId == null) {
                        emptyList()
                    } else {
                        // The detail screen tolerates a failed reviews leg
                        // with an empty row (the TmdbCompanion reviews-leg
                        // contract) — so does this screen.
                        seerrRepository.getTmdbReviews(tmdbId, mediaType).getOrElse { emptyList() }
                    }
                },
                onFailure = { null },
            )
            _uiState.value = when {
                resolved == null ->
                    ReviewsUiState.Error(UserErrorMessages.resolve(detail.exceptionOrNull(), "Failed to load"))
                else -> ReviewsUiState.Success(
                    title = detail.getOrNull()?.item?.name.orEmpty(),
                    reviews = resolved,
                )
            }
        }
    }
}
