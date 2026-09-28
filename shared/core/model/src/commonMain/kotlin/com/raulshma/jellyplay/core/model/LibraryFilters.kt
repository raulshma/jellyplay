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
 *
 * This is also the library/search filter WRITE ALGEBRA (same pattern as
 * [HomeSectionPrefs]): the `withX` transforms below are the single policy
 * behind every filter toggle/set/clear, and [hasActiveFilters] is the one
 * "is anything non-default" fold. The library and search screens used to
 * hand-roll these — the active-filter predicates had drifted (library's
 * omitted years/tags/minRating/sort), so both now read through the fold here.
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
    // Presence filters: when true, restrict the query to items that have
    // subtitles / a trailer. Standalone /Items query params on the server
    // (hasSubtitles / hasTrailer), NOT ItemFilter values. Tri-state like
    // [isResumable] for the same backward-compatibility with the persisted
    // filter blob (null = "off"; a stored false is an explicit "off" that
    // queries identically). Neither has an offline column — both are ignored
    // while the downloaded pin is active, like [tags].
    val hasSubtitles: Boolean? = null,
    val hasTrailer: Boolean? = null,
) {

    /**
     * Copy with [mediaType]'s membership in the media-type set toggled — the
     * policy behind the Type sheet's multi-select chips.
     */
    fun withMediaTypeToggled(mediaType: MediaType): LibraryFilters =
        copy(
            mediaTypes = if (mediaType in mediaTypes) mediaTypes - mediaType else mediaTypes + mediaType,
        )

    /** Copy with [genre]'s membership in the genre set toggled. */
    fun withGenreToggled(genre: String): LibraryFilters =
        copy(genres = if (genre in genres) genres - genre else genres + genre)

    /** Copy with [tag]'s membership in the tag set toggled. */
    fun withTagToggled(tag: String): LibraryFilters =
        copy(tags = if (tag in tags) tags - tag else tags + tag)

    /**
     * Copy with the year dimension replaced by [years] — the year-range
     * sheet's whole-set apply (decade presets + custom range).
     */
    fun withYears(years: Iterable<Int>): LibraryFilters =
        copy(years = years.toList())

    /** Copy with the minimum-rating floor set to [minRating] (0f = off). */
    fun withMinRating(minRating: Float): LibraryFilters =
        copy(minRating = minRating)

    /**
     * Copy with the single-select [sortBy] applied — the Sort sheet's
     * immediate-apply tap.
     */
    fun withSortBy(sortBy: SortOption): LibraryFilters =
        copy(sortBy = sortBy)

    /**
     * Copy with the single-select played [status] applied — the Status
     * sheet's immediate-apply tap.
     */
    fun withPlayedStatus(status: PlayedStatus): LibraryFilters =
        copy(playedStatus = status)

    /**
     * Copy with the resumable (In Progress) restriction toggled. Mirrors the
     * status sheet's exact `!(isResumable == true)` flip, so a stored `false`
     * flips back to `true` — same as the hand-rolled write it replaces.
     */
    fun withResumableToggled(): LibraryFilters =
        copy(isResumable = !(isResumable == true))

    /**
     * Copy with the downloaded-only source toggle flipped. Mirrors the
     * Downloaded chip's exact `!(isDownloaded == true)` flip (an explicit
     * `false` is a stored "off" in the persisted blob).
     */
    fun withDownloadedToggled(): LibraryFilters =
        copy(isDownloaded = !(isDownloaded == true))

    /**
     * Copy with the has-subtitles presence toggle flipped. Mirrors the
     * resumable/downloaded flip (`!(x == true)`), so a stored `false` flips
     * back to `true` — same algebra as the sheet chip it backs.
     */
    fun withHasSubtitlesToggled(): LibraryFilters =
        copy(hasSubtitles = !(hasSubtitles == true))

    /** Copy with the has-trailer presence toggle flipped (same algebra). */
    fun withHasTrailerToggled(): LibraryFilters =
        copy(hasTrailer = !(hasTrailer == true))

    /** Copy with every dimension back at its default — the clear-all write. */
    fun cleared(): LibraryFilters = LibraryFilters()

    /**
     * The one active-filter fold: true when ANY dimension departs from its
     * default — including a non-default [sortBy], which counts as active
     * because the sort chips highlight and Back-press clears it (search's
     * original predicate; library's drifted copy omitted years/tags/minRating/
     * sort/resumable). The tri-state booleans are active only when `true` —
     * a stored `false` means "explicitly off", indistinguishable in behavior
     * from the null default.
     */
    fun hasActiveFilters(): Boolean =
        mediaTypes.isNotEmpty() ||
            genres.isNotEmpty() ||
            years.isNotEmpty() ||
            tags.isNotEmpty() ||
            minRating > 0f ||
            playedStatus != PlayedStatus.ALL ||
            sortBy != SortOption.YEAR_DESC ||
            isResumable == true ||
            isDownloaded == true ||
            hasSubtitles == true ||
            hasTrailer == true

    /**
     * The read-side counterpart of [hasActiveFilters]: enumerates the active
     * dimensions as dismissible [ActiveFilterTag] entries — the fold behind
     * the shared active-filter tag bar in core:ui (library + search; both used
     * to hand-enumerate with per-dimension copy lambdas that had already
     * drifted). Multi-value dimensions emit one tag per value in list order;
     * the per-tag [ActiveFilterTag.clear] is the exact `copy(... minus x)`
     * write the hand-rolled dismiss lambdas performed, so dismiss semantics
     * are unchanged.
     *
     * [dimensions] selects AND orders what a caller renders — a screen passes
     * its own dimension list, so per-screen subsets/orderings stay explicit
     * rather than silently imposed (library renders media types, status,
     * downloaded, genres in that order; search renders media types, genres,
     * years, tags, min rating, status). Defaults to the canonical order here.
     * [SortOption][sortBy] is deliberately absent: neither screen surfaces the
     * active sort as a dismissible tag (the sort chips own that dimension)
     * even though [hasActiveFilters] counts it.
     */
    fun activeTags(
        dimensions: List<LibraryFilterDimension> = LibraryFilterDimension.entries,
    ): List<ActiveFilterTag> = buildList {
        for (dimension in dimensions) {
            when (dimension) {
                LibraryFilterDimension.MEDIA_TYPES -> mediaTypes.forEach { mediaType ->
                    add(ActiveFilterTag(dimension, mediaType.name) { copy(mediaTypes = mediaTypes - mediaType) })
                }

                LibraryFilterDimension.GENRES -> genres.forEach { genre ->
                    add(ActiveFilterTag(dimension, genre) { copy(genres = genres - genre) })
                }

                LibraryFilterDimension.YEARS -> years.forEach { year ->
                    add(ActiveFilterTag(dimension, year.toString()) { copy(years = years - year) })
                }

                LibraryFilterDimension.TAGS -> tags.forEach { tag ->
                    add(ActiveFilterTag(dimension, tag) { copy(tags = tags - tag) })
                }

                LibraryFilterDimension.MIN_RATING -> if (minRating > 0f) {
                    add(ActiveFilterTag(dimension, minRating.toString()) { copy(minRating = 0f) })
                }

                LibraryFilterDimension.PLAYED_STATUS -> if (playedStatus != PlayedStatus.ALL) {
                    add(ActiveFilterTag(dimension, playedStatus.name) { copy(playedStatus = PlayedStatus.ALL) })
                }

                LibraryFilterDimension.IS_RESUMABLE -> if (isResumable == true) {
                    add(ActiveFilterTag(dimension, "true") { copy(isResumable = null) })
                }

                LibraryFilterDimension.IS_DOWNLOADED -> if (isDownloaded == true) {
                    add(ActiveFilterTag(dimension, "true") { copy(isDownloaded = null) })
                }

                LibraryFilterDimension.HAS_SUBTITLES -> if (hasSubtitles == true) {
                    add(ActiveFilterTag(dimension, "true") { copy(hasSubtitles = null) })
                }

                LibraryFilterDimension.HAS_TRAILER -> if (hasTrailer == true) {
                    add(ActiveFilterTag(dimension, "true") { copy(hasTrailer = null) })
                }
            }
        }
    }
}

/**
 * The filter dimensions [LibraryFilters.activeTags] can enumerate. The order
 * here is the canonical read order (mirrors the [LibraryFilters.hasActiveFilters]
 * fold); callers pass their own ordered subset to control what and in which
 * order the tag bar renders.
 */
enum class LibraryFilterDimension {
    MEDIA_TYPES,
    GENRES,
    YEARS,
    TAGS,
    MIN_RATING,
    PLAYED_STATUS,
    IS_RESUMABLE,
    IS_DOWNLOADED,
    HAS_SUBTITLES,
    HAS_TRAILER,
}

/**
 * One dismissible entry of the active-filter bar: a dimension that currently
 * departs from its default, the value being dismissed, and the clear write.
 *
 * The model layer cannot resolve string resources, so [value] is the raw
 * identity (enum [name][LibraryFilterDimension] member name, genre/tag text,
 * year, rating floor) — the core:ui label resolver turns it into the localized
 * label. [clear] returns a copy of the originating filters with exactly this
 * value removed.
 */
@Immutable
data class ActiveFilterTag(
    val dimension: LibraryFilterDimension,
    val value: String,
    val clear: () -> LibraryFilters,
)
