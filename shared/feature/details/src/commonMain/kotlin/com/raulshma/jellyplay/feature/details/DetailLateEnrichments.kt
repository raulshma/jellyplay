package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The late-landing enrichment leaves of [DetailUiState] — the fields the
 * bounded parallel fan-out writes AFTER the core detail has rendered
 * (trailer/review/similar/franchise/… landings). Everything else on the bag
 * (detail, load state, origin, capabilities, seasons/episodes, stream
 * selections, …) is core content that the whole tree must see atomically.
 *
 * The split exists so the screen's content ROOT can subscribe to a projection
 * that excludes these leaves: a root collection of the full bag re-executes
 * the entire scaffold (state-bag construction, ⋮ options build, top bar,
 * backdrop) on every enrichment landing, while the sections that actually
 * render the leaves are one lazy item each. The projections are:
 *  - [com.raulshma.jellyplay.feature.details.withoutLateEnrichments] — the
 *    root's base view (late leaves pinned to their defaults),
 *  - [com.raulshma.jellyplay.feature.details.lateEnrichments] — the sub-flow
 *    payload the body merges back in ([mergedWith]) before admission and
 *    rendering.
 *
 * A leaf belongs here exactly when ALL of its readers are body-scoped (a
 * section item, the scroll body's admission fold, or the backdrop's narrow
 * host) — a leaf any root-scope surface reads (e.g. `detailContext`,
 * `themeMusicPlaying`, the Seerr dialog snapshot) stays core so its landing
 * keeps the current root behavior.
 */
@Immutable
data class DetailLateEnrichments(
    val pluginRatings: List<com.raulshma.jellyplay.core.model.JellyPlayPluginRating> = emptyList(),
    val externalRatings: DetailExternalRatings = DetailExternalRatings(),
    val externalRatingsSuppressedByPlugin: Boolean = false,
    val relatedItems: List<MediaItem> = emptyList(),
    val localRelatedItems: List<MediaItem> = emptyList(),
    val pluginSimilarItems: List<MediaItem> = emptyList(),
    val pluginSimilarSuppressesStock: Boolean = false,
    val specialFeatures: List<MediaItem> = emptyList(),
    val collectionItems: List<MediaItem> = emptyList(),
    val franchiseCollections: List<DetailUiState.FranchiseCollection> = emptyList(),
    val upcomingEpisodes: List<MediaItem> = emptyList(),
    val trailers: List<DetailUiState.TrailerEntry> = emptyList(),
    val themeVideoStreamUrl: String? = null,
    val tmdbReviews: List<TmdbReview> = emptyList(),
    val seerrRecommendations: List<SeerrSearchItem> = emptyList(),
    val seerrSimilar: List<SeerrSearchItem> = emptyList(),
    val animeMarkers: Map<Int, AnimeBadgeKind> = emptyMap(),
    val seasonRatings: Map<String, Map<Int, com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore>> = emptyMap(),
    val hasIntroSegment: Boolean = false,
    val hasCreditSegment: Boolean = false,
    val smartPlayTarget: DetailUiState.SmartPlayTarget? = null,
    val book: DetailUiState.BookDetailState? = null,
) {
    companion object {
        /**
         * The unwired-seam default for the sub-flow local: a never-emitting
         * holder of the all-defaults snapshot, so a consumer that never saw a
         * provider renders the base state unmodified.
         */
        internal val emptyFlow: StateFlow<DetailLateEnrichments> =
            MutableStateFlow(DetailLateEnrichments())
    }
}

/** Projects the late-landing leaves off a content snapshot (the sub-flow's map). */
fun DetailUiState.lateEnrichments(): DetailLateEnrichments = DetailLateEnrichments(
    pluginRatings = pluginRatings,
    externalRatings = externalRatings,
    externalRatingsSuppressedByPlugin = externalRatingsSuppressedByPlugin,
    relatedItems = relatedItems,
    localRelatedItems = localRelatedItems,
    pluginSimilarItems = pluginSimilarItems,
    pluginSimilarSuppressesStock = pluginSimilarSuppressesStock,
    specialFeatures = specialFeatures,
    collectionItems = collectionItems,
    franchiseCollections = franchiseCollections,
    upcomingEpisodes = upcomingEpisodes,
    trailers = trailers,
    themeVideoStreamUrl = themeVideoStreamUrl,
    tmdbReviews = tmdbReviews,
    seerrRecommendations = seerrRecommendations,
    seerrSimilar = seerrSimilar,
    animeMarkers = animeMarkers,
    seasonRatings = seasonRatings,
    hasIntroSegment = hasIntroSegment,
    hasCreditSegment = hasCreditSegment,
    smartPlayTarget = smartPlayTarget,
    book = book,
)

/**
 * The root projection: this snapshot with every late-landing leaf pinned to
 * its default. Pure and structural — combined with `distinctUntilChanged` it
 * collapses a burst of enrichment landings into zero root emissions when no
 * core field moved.
 */
fun DetailUiState.withoutLateEnrichments(): DetailUiState = copy(
    pluginRatings = emptyList(),
    externalRatings = DetailExternalRatings(),
    externalRatingsSuppressedByPlugin = false,
    relatedItems = emptyList(),
    localRelatedItems = emptyList(),
    pluginSimilarItems = emptyList(),
    pluginSimilarSuppressesStock = false,
    specialFeatures = emptyList(),
    collectionItems = emptyList(),
    franchiseCollections = emptyList(),
    upcomingEpisodes = emptyList(),
    trailers = emptyList(),
    themeVideoStreamUrl = null,
    tmdbReviews = emptyList(),
    seerrRecommendations = emptyList(),
    seerrSimilar = emptyList(),
    animeMarkers = emptyMap(),
    seasonRatings = emptyMap(),
    hasIntroSegment = false,
    hasCreditSegment = false,
    smartPlayTarget = null,
    book = null,
)

/** Re-applies the late leaves onto a [withoutLateEnrichments] projection — the body's merged view. */
fun DetailUiState.mergedWith(late: DetailLateEnrichments): DetailUiState = copy(
    pluginRatings = late.pluginRatings,
    externalRatings = late.externalRatings,
    externalRatingsSuppressedByPlugin = late.externalRatingsSuppressedByPlugin,
    relatedItems = late.relatedItems,
    localRelatedItems = late.localRelatedItems,
    pluginSimilarItems = late.pluginSimilarItems,
    pluginSimilarSuppressesStock = late.pluginSimilarSuppressesStock,
    specialFeatures = late.specialFeatures,
    collectionItems = late.collectionItems,
    franchiseCollections = late.franchiseCollections,
    upcomingEpisodes = late.upcomingEpisodes,
    trailers = late.trailers,
    themeVideoStreamUrl = late.themeVideoStreamUrl,
    tmdbReviews = late.tmdbReviews,
    seerrRecommendations = late.seerrRecommendations,
    seerrSimilar = late.seerrSimilar,
    animeMarkers = late.animeMarkers,
    seasonRatings = late.seasonRatings,
    hasIntroSegment = late.hasIntroSegment,
    hasCreditSegment = late.hasCreditSegment,
    smartPlayTarget = late.smartPlayTarget,
    book = late.book,
)
