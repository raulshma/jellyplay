package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.seerr.SeerrEpisode
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails

/**
 * Single-source-of-truth UI state for the Seerr detail screen.
 *
 * Replaces the former scattered `composeState`/`stateFlow` holders on
 * [SeerrDetailViewModel] with one atomic snapshot so Compose sees a single
 * recomposition per load instead of eight sequential ones as each holder
 * flipped in sequence. Mirrors the `DetailUiState` pattern.
 *
 * Seerr-request delegate state (service details, tv seasons, request result)
 * is folded into [uiState] by the ViewModel's aggregator and is also exposed
 * here so observers read one object.
 */
@Immutable
data class SeerrDetailUiState(
    val movieDetails: SeerrMovieDetails? = null,
    val tvDetails: SeerrTvDetails? = null,
    val ratings: SeerrRatings? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val recommendations: List<SeerrSearchItem> = emptyList(),
    val similar: List<SeerrSearchItem> = emptyList(),
    val selectedSeasonNumber: Int? = null,
    val episodesBySeason: Map<Int, List<SeerrEpisode>> = emptyMap(),
    val isLoadingEpisodes: Boolean = false,
    /** Jellyfin library item id resolved for an "Available" Seerr item, or null
     *  when the item isn't in the library (or resolution hasn't run/failed). */
    val jellyfinItemId: String? = null,
)
