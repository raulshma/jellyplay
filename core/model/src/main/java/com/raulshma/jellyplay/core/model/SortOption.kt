package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Sort options for library / section item queries. Each [apiValue] is the
 * Jellyfin `sortBy` string (may be a compound comma-separated key, e.g.
 * "ProductionYear,SortName") so it can be passed straight to
 * `MediaRepository.getMediaItemsPaged(sortBy = ...)`.
 *
 * Promoted here from `feature/library` so the home layer can build a
 * [LibrarySectionContext] (e.g. Recently Added → DATE_ADDED) without a
 * feature-on-feature dependency.
 *
 * `@Serializable` (by Kotlin enum `.name`, matching the on-disk wire format
 * the library filter store already writes) so [LibraryFilters] can round-trip
 * through kotlinx.serialization without a String-typed mirror.
 */
@Immutable
@Serializable
enum class SortOption(
    val displayName: String,
    val apiValue: String,
    val sortOrder: String = "Ascending",
) {
    SORT_NAME("Name", "SortName", "Ascending"),
    YEAR_DESC("Newest", "ProductionYear,SortName", "Descending"),
    YEAR_ASC("Oldest", "ProductionYear,SortName", "Ascending"),
    RATING("Rating", "CommunityRating,SortName", "Descending"),
    DATE_ADDED("Recently Added", "DateCreated,SortName", "Descending"),
    DATE_LAST_CONTENT_ADDED("Recently Added Content", "DateLastContentAdded,SortName", "Descending"),
    RANDOM("Random", "Random", "Ascending"),
    DATE_PLAYED("Recently Played", "DatePlayed,SortName", "Descending"),
    PREMIERE_DATE("Release Date", "PremiereDate,SortName", "Descending"),
}
