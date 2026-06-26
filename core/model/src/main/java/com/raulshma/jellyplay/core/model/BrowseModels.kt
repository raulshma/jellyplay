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
    val seedItem: MediaItem? = null,
)

@Immutable
@Serializable
data class RecommendationResult(
    val items: List<MediaItem>,
    val seedItem: MediaItem?,
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
    PINNED,
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
            PINNED -> "Pinned"
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
            PINNED -> "Collections and shelves you have pinned to home"
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

/**
 * The kind of source backing a user-pinned home section. Determines which
 * Jellyfin endpoint is queried to populate the row.
 */
@Immutable
@Serializable
enum class PinnedSectionType {
    COLLECTION,
    PLAYLIST,
    FAVORITES,
    GENRE,
    STUDIO,
    ;

    val displayName: String
        get() = when (this) {
            COLLECTION -> "Collection"
            PLAYLIST -> "Playlist"
            FAVORITES -> "Favorites"
            GENRE -> "Genre"
            STUDIO -> "Studio"
        }
}

/**
 * A user-pinned home section. The [id] is a stable composite key
 * ("${type.name}_${sourceId}") used for ordering/keying in the home list and
 * the management screen; [sourceId] is the Jellyfin item id (collection,
 * playlist, genre or studio). FAVORITES uses a sentinel source id.
 */
@Immutable
@Serializable
data class PinnedHomeSection(
    val type: PinnedSectionType,
    val sourceId: String,
    val title: String,
) {
    val id: String get() = "${type.name}_$sourceId"

    companion object {
        const val FAVORITES_SOURCE_ID = "__favorites__"
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
data class Studio(
    val id: String,
    val name: String,
)

@Immutable
@Serializable
enum class LibraryViewMode {
    GRID,
    LIST,
}
