package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SearchResult(
    val items: List<MediaItem>,
    val totalRecordCount: Int,
    val startIndex: Int,
)

@Immutable
@Serializable
data class HomeSection(
    val id: String,
    val title: String,
    val type: HomeSectionType,
    val items: List<MediaItem>,
)

@Immutable
@Serializable
enum class HomeSectionType {
    CONTINUE_WATCHING,
    NEXT_UP,
    RECENTLY_ADDED,
    LATEST_MEDIA,
    FAVORITES,
    LIVE_TV,
    DOWNLOADED,
    RECOMMENDATIONS,
    ;

    val displayName: String
        get() = when (this) {
            CONTINUE_WATCHING -> "Continue Watching"
            NEXT_UP -> "Next Up"
            RECENTLY_ADDED -> "Recently Added"
            LATEST_MEDIA -> "Latest Media"
            FAVORITES -> "Favorites"
            LIVE_TV -> "Live TV"
            DOWNLOADED -> "Downloaded"
            RECOMMENDATIONS -> "Recommended For You"
        }

    val description: String
        get() = when (this) {
            CONTINUE_WATCHING -> "Resume watching in-progress media"
            NEXT_UP -> "Next unwatched episodes of your shows"
            RECENTLY_ADDED -> "Recently added items across all libraries"
            LATEST_MEDIA -> "Latest items from each library"
            FAVORITES -> "Your favorited items"
            LIVE_TV -> "Live television channels"
            DOWNLOADED -> "Offline downloaded items"
            RECOMMENDATIONS -> "Personalized picks based on your watch history"
        }

    companion object {
        val CONFIGURABLE = listOf(
            CONTINUE_WATCHING,
            NEXT_UP,
            LATEST_MEDIA,
            RECENTLY_ADDED,
            RECOMMENDATIONS,
        )
    }
}

@Immutable
@Serializable
data class LibraryFolder(
    val id: String,
    val name: String,
    val collectionType: String? = null,
    val type: String? = null,
)

@Immutable
@Serializable
data class Genre(
    val id: String,
    val name: String,
)

@Immutable
@Serializable
enum class LibraryViewMode {
    GRID,
    LIST,
}
