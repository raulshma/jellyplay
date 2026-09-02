package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The active filter/sort state for a library browsing session.
 *
 * Promoted to core/model from the feature module so [LibraryBrowserState] and
 * [LibraryBrowserReducer] can live here too and be unit-tested without the
 * feature classpath. Its dependencies ([SortOption], [MediaType], [PlayedStatus])
 * already lived in core/model; this only sat in the feature module by accident.
 *
 * Serialised by name (kotlinx.serialization encodes enums as their `.name`) so
 * the on-disk wire format is unchanged from the legacy `SavedLibraryFilters`
 * mirror — see [LibraryFiltersSerializationTest].
 */
@Immutable
@Serializable
data class LibraryFilters(
    val mediaTypes: List<MediaType> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    // Newest (highest production year first) is the most useful landing sort for
    // a media library — a user opening the tab wants to see fresh content, not an
    // alphabetical list. Overridden per-folder by the persisted filter blob.
    val sortBy: SortOption = SortOption.YEAR_DESC,
    val playedStatus: PlayedStatus = PlayedStatus.ALL,
    val tags: List<String> = emptyList(),
    val minRating: Float = 0f,
    // Resumable filter: when non-null, restricts the query to items with a
    // playback position (Jellyfin's ItemFilter.IsResumable). A separate boolean
    // dimension from [playedStatus] so it composes with any played-status chip
    // and stays backward-compatible with the persisted filter blob (defaults to
    // null = "off"). Pair with [SortOption.IN_PROGRESS] for the classic
    // "In Progress" view, but usable independently.
    val isResumable: Boolean? = null,
    // Downloaded filter: when true, the browse list is served from the local
    // offline store instead of the server (downloads are device-local; the
    // server has no equivalent ItemFilter). Unlike [isResumable] this is
    // tri-state for the same backward-compatibility reason: null/absent in the
    // persisted blob means "off". While active, only the dimensions stored
    // offline can apply (media type, year, rating, genres, played status,
    // sort); [tags] has no offline column and is ignored.
    val isDownloaded: Boolean? = null,
)
