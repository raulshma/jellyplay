package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Configuration for the home-screen recommendations widgets.
 *
 * Stored in [com.raulshma.jellyplay.core.datastore.UserPreferencesStore]
 * and used by:
 *   * [com.raulshma.jellyplay.core.data.work.LibraryRecommendationsWidgetWorker]
 *   * [com.raulshma.jellyplay.core.data.work.SeerrRecommendationsWidgetWorker]
 *   * [com.raulshma.jellyplay.widget.config.WidgetConfigActivity]
 */
@Immutable
@Serializable
data class WidgetConfig(
    val librarySource: LibraryRecommendationsSource = LibraryRecommendationsSource.LATEST,
    val seerrSource: SeerrWidgetSource = SeerrWidgetSource.TRENDING,
    val continueWatchingItemCount: Int = DEFAULT_CONTINUE_WATCHING_ITEM_COUNT,
    val nowPlayingShowArtwork: Boolean = true,
    val nowPlayingShowProgress: Boolean = true,
) {
    companion object {
        const val DEFAULT_CONTINUE_WATCHING_ITEM_COUNT = 10
        const val MIN_CONTINUE_WATCHING_ITEM_COUNT = 3
        const val MAX_CONTINUE_WATCHING_ITEM_COUNT = 20
    }
}

@Immutable
@Serializable
enum class LibraryRecommendationsSource {
    SIMILAR_TO_RECENT,
    LATEST,
    FAVORITES,
    SURPRISE_ME,
    ;

    val displayName: String
        get() = when (this) {
            SIMILAR_TO_RECENT -> "Because you watched"
            LATEST -> "Latest in your library"
            FAVORITES -> "Your favorites"
            SURPRISE_ME -> "Surprise me"
        }

    val description: String
        get() = when (this) {
            SIMILAR_TO_RECENT -> "Picks based on your most recently watched item"
            LATEST -> "Freshly added media across your libraries"
            FAVORITES -> "Items you have marked as favorite"
            SURPRISE_ME -> "A randomized shuffle from your library"
        }
}

@Immutable
@Serializable
enum class SeerrWidgetSource {
    TRENDING,
    POPULAR_MOVIES,
    POPULAR_TV,
    UPCOMING_MOVIES,
    UPCOMING_TV,
    ;

    val displayName: String
        get() = when (this) {
            TRENDING -> "Trending this week"
            POPULAR_MOVIES -> "Popular movies"
            POPULAR_TV -> "Popular TV"
            UPCOMING_MOVIES -> "Upcoming movies"
            UPCOMING_TV -> "Upcoming TV"
        }

    val description: String
        get() = when (this) {
            TRENDING -> "What everyone is talking about"
            POPULAR_MOVIES -> "Top rated movies right now"
            POPULAR_TV -> "Top rated shows right now"
            UPCOMING_MOVIES -> "Movies releasing soon"
            UPCOMING_TV -> "Shows premiering soon"
        }
}

/**
 * Slim, widget-only representation of a Jellyfin media item. Stores
 * pre-built image URLs so the widget process never has to construct
 * or sign them.
 */
@Immutable
@Serializable
data class LibraryWidgetItem(
    val itemId: String,
    val name: String,
    val mediaType: MediaType,
    val year: Int? = null,
    val communityRating: Float? = null,
    val seriesName: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val isFavorite: Boolean = false,
    val seedItemName: String? = null,
)

/**
 * Slim, widget-only representation of a Seerr (Jellyseerr/Overseerr)
 * discover item. Image URLs are pre-built via TMDB's public CDN.
 */
@Immutable
@Serializable
data class SeerrWidgetItem(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val subtitle: String? = null,
    val year: Int? = null,
    val voteAverage: Float? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
)
