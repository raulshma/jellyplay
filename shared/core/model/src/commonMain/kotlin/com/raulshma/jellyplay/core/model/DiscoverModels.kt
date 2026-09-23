package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Which backend populates a user-configured [DiscoverRowConfig] row.
 *
 * JELLYFIN rows run a filtered `/Items` catalog query against the user's own
 * server ([DiscoverRowConfig.filters] plus the discover-only dimensions);
 * SEERR rows run a Seerr/Overseerr `/api/v1/discover` query shaped by
 * [DiscoverRowConfig.seerrFilters] and render request-capable TMDB cards.
 */
@Immutable
@Serializable
enum class DiscoverRowSource {
    JELLYFIN,
    SEERR,
    ;

    val displayName: String
        get() = when (this) {
            JELLYFIN -> "My Server"
            SEERR -> "Seerr"
        }
}

/** A Jellyfin studio picked for a discover row — id + display name kept together so the editor can re-render the chip without a lookup round-trip. */
@Immutable
@Serializable
data class StudioRef(
    val id: String,
    val name: String,
)

/** A Jellyfin person (cast/crew) picked for a discover row, filtered server-side via `personIds`. */
@Immutable
@Serializable
data class PersonRef(
    val id: String,
    val name: String,
)

/** A TMDB genre picked for a Seerr discover row — TMDB genre ids are global constants (see [TmdbGenres]). */
@Immutable
@Serializable
data class TmdbGenreRef(
    val id: Int,
    val name: String,
)

/** The media kind a Seerr discover row queries — Seerr exposes separate movie/TV discover endpoints. */
@Immutable
@Serializable
enum class SeerrRowMedia {
    MOVIE,
    TV,
    ;
}

/**
 * Sort order for a Seerr discover row. The apiValue is TMDB's `sortBy`
 * vocabulary, which differs per media kind for the date sort — resolved at the
 * call site ([SeerrRowSort.apiValueFor]).
 */
@Immutable
@Serializable
enum class SeerrRowSort {
    POPULARITY,
    RATING,
    RELEASE_DATE,
    ;

    /** TMDB `sortBy` value for [media] (`release_date.desc` for movies, `first_air_date.desc` for TV). */
    fun apiValueFor(media: SeerrRowMedia): String = when (this) {
        POPULARITY -> "popularity.desc"
        RATING -> "vote_average.desc"
        RELEASE_DATE -> if (media == SeerrRowMedia.MOVIE) "release_date.desc" else "first_air_date.desc"
    }
}

/**
 * The filter set for a Seerr-backed discover row. Mirrors the dimensions the
 * Seerr `/api/v1/discover/movies|tv` endpoints accept (camelCase query
 * params): `genre`, `voteAverageGte`, `sortBy`, date ranges, and the release
 * year window (mapped to `primaryReleaseDateGte/Lte` / `firstAirDateGte/Lte`
 * from Jan 1 / Dec 31 of the boundary years at call time).
 */
@Immutable
@Serializable
data class SeerrRowFilters(
    val media: SeerrRowMedia = SeerrRowMedia.MOVIE,
    val genres: List<TmdbGenreRef> = emptyList(),
    /** Inclusive release-year window; null bounds are open-ended. */
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /** Minimum TMDB vote average; 0 = no floor. */
    val minVoteAverage: Float = 0f,
    val sort: SeerrRowSort = SeerrRowSort.POPULARITY,
    /** Restricts to items releasing today or later (upcoming). */
    val upcomingOnly: Boolean = false,
) {
    /** The one active-filter fold, same convention as [LibraryFilters.hasActiveFilters]. */
    fun hasActiveFilters(): Boolean =
        genres.isNotEmpty() ||
            yearFrom != null ||
            yearTo != null ||
            minVoteAverage > 0f ||
            upcomingOnly
}

/**
 * One user-configured Discover row on the home screen. The whole list is
 * persisted per user as a JSON blob (HomeDiscoveryStore, key
 * `home_discover_rows`) and rendered under the DISCOVER section type — each
 * row becomes its own `HomeSection` with id `discover_<id>`, ordered within
 * the DISCOVER block by list position.
 *
 * JELLYFIN rows compose the shared [LibraryFilters] (media types, genres,
 * years, tags, min rating, played status, sort — including RANDOM) with the
 * discover-only dimensions below; the library-screen filter model stays
 * untouched so its persisted per-folder blob schema is unchanged.
 *
 * Serialized with the lenient codec ([PreferenceCodec]/FilterCodec class of
 * Json config), so adding a field decodes forward-compatibly for existing
 * persisted rows.
 */
@Immutable
@Serializable
data class DiscoverRowConfig(
    /** Stable generated id (see [newDiscoverRowId]); doubles as the HomeSection instance id. */
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val source: DiscoverRowSource = DiscoverRowSource.JELLYFIN,
    /** Row length cap (10..50). */
    val limit: Int = DEFAULT_LIMIT,
    // ── JELLYFIN source ────────────────────────────────────────────────────
    /** Shared filter dimensions; default sort is RANDOM — the discovery default. */
    val filters: LibraryFilters = LibraryFilters(sortBy = SortOption.RANDOM),
    /** Libraries the row scopes to; empty = the whole catalog. */
    val libraryIds: List<String> = emptyList(),
    val studios: List<StudioRef> = emptyList(),
    val people: List<PersonRef> = emptyList(),
    /** Only items created (added to the server) within the last N days. */
    val addedWithinDays: Int? = null,
    /** Only items premiered within the last N years. */
    val premieredWithinYears: Int? = null,
    // ── SEERR source ───────────────────────────────────────────────────────
    val seerrFilters: SeerrRowFilters = SeerrRowFilters(),
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MIN_LIMIT = 10
        const val MAX_LIMIT = 50
    }
}

/** Generates a stable row id: `dr_<timestamp>-<random hex>` — unique across sessions without a UUID dependency in commonMain. */
fun newDiscoverRowId(): String {
    val random = List(6) { "0123456789abcdef"[kotlin.random.Random.nextInt(16)] }.joinToString("")
    return "dr_${wallNowMillis().toString(36)}-$random"
}

// ── Discover-row list write algebra (same pattern as HomeSectionPrefs) ───────

/** Copy with [rowId]'s enabled flag flipped — the row-level show/hide toggle. */
fun List<DiscoverRowConfig>.withDiscoverRowEnabled(rowId: String, enabled: Boolean): List<DiscoverRowConfig> =
    map { if (it.id == rowId) it.copy(enabled = enabled) else it }

/**
 * Copy with [rowId] swapped with its neighbour ([up] or down), or null when no
 * swap is possible (row absent, or already at the requested edge) so callers
 * can skip the write — same contract as
 * [HomeSectionPrefs.withSectionMoved].
 */
fun List<DiscoverRowConfig>.withDiscoverRowMoved(rowId: String, up: Boolean): List<DiscoverRowConfig>? {
    val index = indexOfFirst { it.id == rowId }
    if (index == -1) return null
    val target = if (up) index - 1 else index + 1
    if (target !in indices) return null
    return toMutableList().apply {
        val removed = removeAt(index)
        add(target, removed)
    }
}
