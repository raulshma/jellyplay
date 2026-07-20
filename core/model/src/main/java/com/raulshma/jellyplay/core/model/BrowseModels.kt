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
    /**
     * For per-library sections (currently LATEST_MEDIA), the Jellyfin library
     * (folder) id backing this row. `null` for every other section type. The
     * home UI uses this to apply per-library visibility overrides via the
     * inline section-config sheet — matching the Settings → Configure Libraries
     * screen semantics. See [HomeSectionType.isConfigurable] and
     * `libraryHomeSectionOverrides` in `UserPreferences`.
     */
    val libraryId: String? = null,
)

/**
 * Result of a home-sections fetch. [sections] are the rendered rows (empty
 * sections are dropped). [failedSectionTypes] records the section *types*
 * that actually errored (403/500/network), so the UI can surface a partial-
 * failure banner only for real failures — never for sections that legitimately
 * returned zero items (e.g. no Continue Watching history).
 */
@Immutable
@Serializable
data class HomeSectionsResult(
    val sections: List<HomeSection>,
    val failedSectionTypes: Set<HomeSectionType> = emptySet(),
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

    /**
     * Whether this section type can be toggled/reordered by the user via the
     * home layout configuration (Settings → Home Screen Layout, or long-press
     * on a home section title). Non-configurable types (FAVORITES, PINNED, …)
     * are driven by other surfaces and never show the configure affordance.
     */
    val isConfigurable: Boolean get() = this in CONFIGURABLE

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
    THUMB,
}

/**
 * Picks a sensible default [LibraryViewMode] for a library folder based on its
 * server-configured [collectionType]. This is the server-driven default — it
 * respects the admin's library-type choice so a music library shows as a list,
 * not the same poster grid as movies. Users can still override per-folder via
 * the toolbar toggle (see [LibraryViewModel.loadViewMode] precedence).
 *
 * - music → LIST (tracks/albums are best browsed as rows)
 * - musicvideos / homevideos / trailers → THUMB (16:9 landscape grid)
 * - movies / tvshows / boxsets / photos / unknown → GRID (poster grid; photo
 *   folders already render as 1:1 squares via PhotoGridCard)
 */
fun LibraryFolder.defaultViewMode(): LibraryViewMode = when (collectionType) {
    "music" -> LibraryViewMode.LIST
    "musicvideos", "homevideos", "trailers" -> LibraryViewMode.THUMB
    else -> LibraryViewMode.GRID
}
