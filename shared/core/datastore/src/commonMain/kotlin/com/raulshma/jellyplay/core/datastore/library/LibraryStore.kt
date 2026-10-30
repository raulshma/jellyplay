package com.raulshma.jellyplay.core.datastore.library

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **library browsing** preference domain: the default
 * library view mode, and the per-library (sort order / view-mode / filter) JSON
 * maps, plus the episode thumbnail / sort-order / skip-specials toggles.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the read-modify-write JSON maps), its read
 * projection, and its reset-key list end-to-end. Mirrors the `PlaybackStore` /
 * `AppearanceStore` shape.
 *
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [LibraryPreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding, the single-key
 * setters and the derived rows of [restore] delegate to the rows' derived
 * writes, and [resetKeysFor] filters the rows by reset category. The three
 * override maps' JSON decodes live in their rows' reads (memoised beside the
 * rows), and every key this store owns resets under the single legacy
 * `HOME_DISCOVERY` category.
 *
 * **Hand-written residuals (the documented opt-outs — a declaration-only row
 * has no derived write, and a read-modify-write cannot be a row encoding):**
 *  - the three per-library override setters
 *    ([setDefaultLibrarySortOrder] / [setLibraryViewMode] (the per-library
 *    overload) / [setLibraryFilters]) decode the current map, put the one
 *    entry and re-encode it in one transaction; the rows own the read and the
 *    whole-map encode [restore] writes.
 *  - the [LibraryPreferenceSpecs.CONFIRM_LIBRARY_RESET] row is the
 *    declaration-only [PreferenceSpec.boolean] (the hand read was the plain
 *    typed `prefs[key] ?: true` — the key was born in the typed-key era, so
 *    no legacy fallback applies); its setter/restore lines stay hand-written.
 *  - [setLibraryPosterSize] keeps the poster-size clamp
 *    ([POSTER_SIZE_MIN]..[POSTER_SIZE_MAX]) — the setter is the clamp owner.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class LibraryStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    /**
     * The store's DataStore keys, each derived from its
     * [LibraryPreferenceSpecs] row — the member rebuilds the row's typed key
     * from the row's single-declared wire name (`Preferences.Key` equality is
     * name-based, so these interoperate with any hand-built key of the same
     * name). Kept as a plain object rather than folded into the rows because
     * it is the reflection anchor for the JVM reset-coverage guard and the
     * key-identity reference for the hand-written override-map setters below.
     */
    internal object Keys {
        val LIBRARY_VIEW_MODE = stringPreferencesKey(LibraryPreferenceSpecs.LIBRARY_VIEW_MODE.keyName)
        val DEFAULT_LIBRARY_SORT_ORDERS = stringPreferencesKey(LibraryPreferenceSpecs.DEFAULT_LIBRARY_SORT_ORDERS.keyName)
        val LIBRARY_VIEW_MODES = stringPreferencesKey(LibraryPreferenceSpecs.LIBRARY_VIEW_MODES.keyName)
        val LIBRARY_FILTERS = stringPreferencesKey(LibraryPreferenceSpecs.LIBRARY_FILTERS.keyName)
        val HIDE_EPISODE_THUMBNAILS = booleanPreferencesKey(LibraryPreferenceSpecs.HIDE_EPISODE_THUMBNAILS.keyName)
        val EPISODES_DESCENDING = booleanPreferencesKey(LibraryPreferenceSpecs.EPISODES_DESCENDING.keyName)
        val SKIP_SPECIALS = booleanPreferencesKey(LibraryPreferenceSpecs.SKIP_SPECIALS.keyName)
        val COMPACT_EPISODE_LIST = booleanPreferencesKey(LibraryPreferenceSpecs.COMPACT_EPISODE_LIST.keyName)
        val SHOW_DETAIL_UP_NEXT = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_DETAIL_UP_NEXT.keyName)
        val SHOW_MISSING_EPISODES = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_MISSING_EPISODES.keyName)
        val LIBRARY_POSTER_SIZE = floatPreferencesKey(LibraryPreferenceSpecs.LIBRARY_POSTER_SIZE.keyName)
        val PREFER_LOGOS = booleanPreferencesKey(LibraryPreferenceSpecs.PREFER_LOGOS.keyName)
        val LIBRARY_GROUP_BY = stringPreferencesKey(LibraryPreferenceSpecs.LIBRARY_GROUP_BY.keyName)
        val CONFIRM_LIBRARY_RESET = booleanPreferencesKey(LibraryPreferenceSpecs.CONFIRM_LIBRARY_RESET.keyName)
        val SHOW_EXTERNAL_LINKS = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_EXTERNAL_LINKS.keyName)
        val SHOW_TAGLINE = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_TAGLINE.keyName)
        val PREFER_LOCAL_TRAILERS = booleanPreferencesKey(LibraryPreferenceSpecs.PREFER_LOCAL_TRAILERS.keyName)
        val SHOW_MORE_IN_COLLECTION = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_MORE_IN_COLLECTION.keyName)
        val SHOW_UPCOMING_STRIP = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_UPCOMING_STRIP.keyName)
        val PLAY_THEME_MUSIC = booleanPreferencesKey(LibraryPreferenceSpecs.PLAY_THEME_MUSIC.keyName)
        val SHOW_REVIEWS = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_REVIEWS.keyName)
        val SHOW_EPISODE_STREAM_BADGES = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_EPISODE_STREAM_BADGES.keyName)
        val SHOW_FILMOGRAPHY_ROLES = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_FILMOGRAPHY_ROLES.keyName)
        val SHOW_FREQUENT_COSTARS = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_FREQUENT_COSTARS.keyName)
        val TRAILER_PREVIEW_ON_FOCUS = booleanPreferencesKey(LibraryPreferenceSpecs.TRAILER_PREVIEW_ON_FOCUS.keyName)
        val SHOW_PROMOTED_ACTIONS_LANDSCAPE = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_PROMOTED_ACTIONS_LANDSCAPE.keyName)
        val SHOW_DOWNLOAD_PROGRESS = booleanPreferencesKey(LibraryPreferenceSpecs.SHOW_DOWNLOAD_PROGRESS.keyName)
    }

    val library: StateFlow<LibrarySlice> =
        dataStore.sliceStateFlow(scope, seed = LibrarySlice(), read = ::read)

    /**
     * Pure read of the library-browsing fields from a raw [Preferences]
     * snapshot, each field delegated to its [LibraryPreferenceSpecs] row
     * encoding (the declaration-only confirm-reset row via its plain
     * [PreferenceSpec.readStored] — the exact hand read). Exposed so the
     * facade can fold these into the whole-`UserPreferences` projection
     * without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): LibrarySlice = LibrarySlice(
        libraryViewMode = LibraryPreferenceSpecs.LIBRARY_VIEW_MODE.readFrom(prefs),
        defaultLibrarySortOrders = LibraryPreferenceSpecs.DEFAULT_LIBRARY_SORT_ORDERS.readFrom(prefs),
        libraryViewModes = LibraryPreferenceSpecs.LIBRARY_VIEW_MODES.readFrom(prefs),
        libraryFilters = LibraryPreferenceSpecs.LIBRARY_FILTERS.readFrom(prefs),
        hideEpisodeThumbnails = LibraryPreferenceSpecs.HIDE_EPISODE_THUMBNAILS.readFrom(prefs),
        episodesDescending = LibraryPreferenceSpecs.EPISODES_DESCENDING.readFrom(prefs),
        skipSpecials = LibraryPreferenceSpecs.SKIP_SPECIALS.readFrom(prefs),
        compactEpisodeList = LibraryPreferenceSpecs.COMPACT_EPISODE_LIST.readFrom(prefs),
        showDetailUpNext = LibraryPreferenceSpecs.SHOW_DETAIL_UP_NEXT.readFrom(prefs),
        showMissingEpisodes = LibraryPreferenceSpecs.SHOW_MISSING_EPISODES.readFrom(prefs),
        libraryPosterSize = LibraryPreferenceSpecs.LIBRARY_POSTER_SIZE.readFrom(prefs),
        preferLogos = LibraryPreferenceSpecs.PREFER_LOGOS.readFrom(prefs),
        libraryGroupBy = LibraryPreferenceSpecs.LIBRARY_GROUP_BY.readFrom(prefs),
        confirmLibraryReset = LibraryPreferenceSpecs.CONFIRM_LIBRARY_RESET.readStored(prefs),
        showExternalLinks = LibraryPreferenceSpecs.SHOW_EXTERNAL_LINKS.readFrom(prefs),
        showTagline = LibraryPreferenceSpecs.SHOW_TAGLINE.readFrom(prefs),
        preferLocalTrailers = LibraryPreferenceSpecs.PREFER_LOCAL_TRAILERS.readFrom(prefs),
        showMoreInCollection = LibraryPreferenceSpecs.SHOW_MORE_IN_COLLECTION.readFrom(prefs),
        showUpcomingStrip = LibraryPreferenceSpecs.SHOW_UPCOMING_STRIP.readFrom(prefs),
        playThemeMusic = LibraryPreferenceSpecs.PLAY_THEME_MUSIC.readFrom(prefs),
        showReviews = LibraryPreferenceSpecs.SHOW_REVIEWS.readFrom(prefs),
        showEpisodeStreamBadges = LibraryPreferenceSpecs.SHOW_EPISODE_STREAM_BADGES.readFrom(prefs),
        showFilmographyRoles = LibraryPreferenceSpecs.SHOW_FILMOGRAPHY_ROLES.readFrom(prefs),
        showFrequentCostars = LibraryPreferenceSpecs.SHOW_FREQUENT_COSTARS.readFrom(prefs),
        trailerPreviewOnFocus = LibraryPreferenceSpecs.TRAILER_PREVIEW_ON_FOCUS.readFrom(prefs),
        showPromotedActionsLandscape = LibraryPreferenceSpecs.SHOW_PROMOTED_ACTIONS_LANDSCAPE.readFrom(prefs),
        showDownloadProgress = LibraryPreferenceSpecs.SHOW_DOWNLOAD_PROGRESS.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, plain slot — is the row's, not
    // re-declared here); the read-modify-write override-map verbs and the
    // declaration-only confirm-reset row keep their hand-written lines.
    // ------------------------------------------------------------------

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { LibraryPreferenceSpecs.LIBRARY_VIEW_MODE.writeTo(it, mode) }
    }

    /**
     * The per-library sort override: read-modify-writes the
     * `default_library_sort_orders` map (decode the current map, put the one
     * entry, re-encode) in one transaction — the documented opt-out (see the
     * class KDoc); the row owns the read and the whole-map encode.
     */
    suspend fun setDefaultLibrarySortOrder(libraryId: String, order: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, order) }
            prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS] = json.encodeToString(next)
        }
    }

    /**
     * The per-library view-mode override: the same read-modify-write shape as
     * [setDefaultLibrarySortOrder] (see the class KDoc's opt-outs).
     */
    suspend fun setLibraryViewMode(libraryId: String, viewMode: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_VIEW_MODES]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, viewMode) }
            prefs[Keys.LIBRARY_VIEW_MODES] = json.encodeToString(next)
        }
    }

    /** The per-library filter override: the same read-modify-write shape. */
    suspend fun setLibraryFilters(libraryId: String, filters: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_FILTERS]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, filters) }
            prefs[Keys.LIBRARY_FILTERS] = json.encodeToString(next)
        }
    }

    suspend fun setHideEpisodeThumbnails(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.HIDE_EPISODE_THUMBNAILS.writeTo(it, enabled) }
    }

    suspend fun setEpisodesDescending(descending: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.EPISODES_DESCENDING.writeTo(it, descending) }
    }

    suspend fun setSkipSpecials(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SKIP_SPECIALS.writeTo(it, enabled) }
    }

    suspend fun setCompactEpisodeList(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.COMPACT_EPISODE_LIST.writeTo(it, enabled) }
    }

    suspend fun setShowDetailUpNext(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_DETAIL_UP_NEXT.writeTo(it, enabled) }
    }

    suspend fun setShowMissingEpisodes(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_MISSING_EPISODES.writeTo(it, enabled) }
    }

    /** Poster-size multiplier; clamped to [POSTER_SIZE_MIN]..[POSTER_SIZE_MAX]. */
    suspend fun setLibraryPosterSize(size: Float) {
        dataStore.edit {
            LibraryPreferenceSpecs.LIBRARY_POSTER_SIZE.writeTo(it, size.coerceIn(POSTER_SIZE_MIN, POSTER_SIZE_MAX))
        }
    }

    suspend fun setPreferLogos(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.PREFER_LOGOS.writeTo(it, enabled) }
    }

    suspend fun setShowExternalLinks(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_EXTERNAL_LINKS.writeTo(it, enabled) }
    }

    suspend fun setShowTagline(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_TAGLINE.writeTo(it, enabled) }
    }

    suspend fun setPreferLocalTrailers(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.PREFER_LOCAL_TRAILERS.writeTo(it, enabled) }
    }

    suspend fun setShowMoreInCollection(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_MORE_IN_COLLECTION.writeTo(it, enabled) }
    }

    suspend fun setShowUpcomingStrip(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_UPCOMING_STRIP.writeTo(it, enabled) }
    }

    suspend fun setPlayThemeMusic(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.PLAY_THEME_MUSIC.writeTo(it, enabled) }
    }

    suspend fun setShowReviews(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_REVIEWS.writeTo(it, enabled) }
    }

    suspend fun setShowEpisodeStreamBadges(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_EPISODE_STREAM_BADGES.writeTo(it, enabled) }
    }

    suspend fun setShowFilmographyRoles(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_FILMOGRAPHY_ROLES.writeTo(it, enabled) }
    }

    suspend fun setShowFrequentCostars(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_FREQUENT_COSTARS.writeTo(it, enabled) }
    }

    suspend fun setTrailerPreviewOnFocus(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.TRAILER_PREVIEW_ON_FOCUS.writeTo(it, enabled) }
    }

    suspend fun setShowPromotedActionsLandscape(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_PROMOTED_ACTIONS_LANDSCAPE.writeTo(it, enabled) }
    }

    suspend fun setShowDownloadProgress(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.SHOW_DOWNLOAD_PROGRESS.writeTo(it, enabled) }
    }

    suspend fun setLibraryGroupBy(groupBy: GroupBy) {
        dataStore.edit { LibraryPreferenceSpecs.LIBRARY_GROUP_BY.writeTo(it, groupBy) }
    }

    /**
     * The declaration-only confirm-reset row (no derived write — see the
     * class KDoc's opt-outs): the hand-written plain slot write.
     */
    suspend fun setConfirmLibraryReset(enabled: Boolean) {
        dataStore.edit { it[Keys.CONFIRM_LIBRARY_RESET] = enabled }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. These are the library-view/sort/filter +
     * episode keys split out of the legacy `HOME_DISCOVERY` reset category.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [LibraryPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. Every key this store
     * owns sits in the legacy `HOME_DISCOVERY` bucket (the
     * library-view/sort/filter + episode keys split out of that category), so
     * all other categories return an empty list.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        LibraryPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with) — except the declaration-only confirm-reset row, whose
     * hand-written line mirrors the setter.
     */
    suspend fun restore(slice: LibrarySlice) {
        dataStore.edit { prefs ->
            LibraryPreferenceSpecs.LIBRARY_VIEW_MODE.writeTo(prefs, slice.libraryViewMode)
            LibraryPreferenceSpecs.DEFAULT_LIBRARY_SORT_ORDERS.writeTo(prefs, slice.defaultLibrarySortOrders)
            LibraryPreferenceSpecs.LIBRARY_VIEW_MODES.writeTo(prefs, slice.libraryViewModes)
            LibraryPreferenceSpecs.LIBRARY_FILTERS.writeTo(prefs, slice.libraryFilters)
            LibraryPreferenceSpecs.HIDE_EPISODE_THUMBNAILS.writeTo(prefs, slice.hideEpisodeThumbnails)
            LibraryPreferenceSpecs.EPISODES_DESCENDING.writeTo(prefs, slice.episodesDescending)
            LibraryPreferenceSpecs.SKIP_SPECIALS.writeTo(prefs, slice.skipSpecials)
            LibraryPreferenceSpecs.COMPACT_EPISODE_LIST.writeTo(prefs, slice.compactEpisodeList)
            LibraryPreferenceSpecs.SHOW_DETAIL_UP_NEXT.writeTo(prefs, slice.showDetailUpNext)
            LibraryPreferenceSpecs.SHOW_MISSING_EPISODES.writeTo(prefs, slice.showMissingEpisodes)
            LibraryPreferenceSpecs.LIBRARY_POSTER_SIZE.writeTo(prefs, slice.libraryPosterSize)
            LibraryPreferenceSpecs.PREFER_LOGOS.writeTo(prefs, slice.preferLogos)
            LibraryPreferenceSpecs.LIBRARY_GROUP_BY.writeTo(prefs, slice.libraryGroupBy)
            prefs[Keys.CONFIRM_LIBRARY_RESET] = slice.confirmLibraryReset
            LibraryPreferenceSpecs.SHOW_EXTERNAL_LINKS.writeTo(prefs, slice.showExternalLinks)
            LibraryPreferenceSpecs.SHOW_TAGLINE.writeTo(prefs, slice.showTagline)
            LibraryPreferenceSpecs.PREFER_LOCAL_TRAILERS.writeTo(prefs, slice.preferLocalTrailers)
            LibraryPreferenceSpecs.SHOW_MORE_IN_COLLECTION.writeTo(prefs, slice.showMoreInCollection)
            LibraryPreferenceSpecs.SHOW_UPCOMING_STRIP.writeTo(prefs, slice.showUpcomingStrip)
            LibraryPreferenceSpecs.PLAY_THEME_MUSIC.writeTo(prefs, slice.playThemeMusic)
            LibraryPreferenceSpecs.SHOW_REVIEWS.writeTo(prefs, slice.showReviews)
            LibraryPreferenceSpecs.SHOW_EPISODE_STREAM_BADGES.writeTo(prefs, slice.showEpisodeStreamBadges)
            LibraryPreferenceSpecs.SHOW_FILMOGRAPHY_ROLES.writeTo(prefs, slice.showFilmographyRoles)
            LibraryPreferenceSpecs.SHOW_FREQUENT_COSTARS.writeTo(prefs, slice.showFrequentCostars)
            LibraryPreferenceSpecs.TRAILER_PREVIEW_ON_FOCUS.writeTo(prefs, slice.trailerPreviewOnFocus)
            LibraryPreferenceSpecs.SHOW_PROMOTED_ACTIONS_LANDSCAPE.writeTo(prefs, slice.showPromotedActionsLandscape)
            LibraryPreferenceSpecs.SHOW_DOWNLOAD_PROGRESS.writeTo(prefs, slice.showDownloadProgress)
        }
    }
}

/**
 * Poster-size multiplier bounds. 1.0 = the adaptive default cell size; higher =
 * larger posters (fewer columns), lower = smaller posters (more columns).
 * Matches the slider range exposed in the Library toolbar.
 */
internal const val DEFAULT_POSTER_SIZE = 1.0f
internal const val POSTER_SIZE_MIN = 0.7f
internal const val POSTER_SIZE_MAX = 1.4f

/**
 * The library browsing preference slice. Plain data class. Every default IS
 * its [LibraryPreferenceSpecs] row's declared default — read off the row via a
 * plain accessor, so a preference's default is stated exactly once, in its
 * spec, and cannot drift between the spec (what an absent key reads) and the
 * slice (what a fresh in-memory instance serves). The spec-default equality is
 * pinned field-for-field by `LibrarySpecDerivationTest`.
 */
@Immutable
@Serializable
data class LibrarySlice(
    val libraryViewMode: LibraryViewMode = LibraryPreferenceSpecs.LIBRARY_VIEW_MODE.default,
    val defaultLibrarySortOrders: Map<String, String> = LibraryPreferenceSpecs.DEFAULT_LIBRARY_SORT_ORDERS.default,
    val libraryViewModes: Map<String, String> = LibraryPreferenceSpecs.LIBRARY_VIEW_MODES.default,
    val libraryFilters: Map<String, String> = LibraryPreferenceSpecs.LIBRARY_FILTERS.default,
    val hideEpisodeThumbnails: Boolean = LibraryPreferenceSpecs.HIDE_EPISODE_THUMBNAILS.default,
    val episodesDescending: Boolean = LibraryPreferenceSpecs.EPISODES_DESCENDING.default,
    val skipSpecials: Boolean = LibraryPreferenceSpecs.SKIP_SPECIALS.default,
    val compactEpisodeList: Boolean = LibraryPreferenceSpecs.COMPACT_EPISODE_LIST.default,
    val showDetailUpNext: Boolean = LibraryPreferenceSpecs.SHOW_DETAIL_UP_NEXT.default,
    /** Whether virtual (missing/unaired) episodes appear in season views. */
    val showMissingEpisodes: Boolean = LibraryPreferenceSpecs.SHOW_MISSING_EPISODES.default,
    val libraryPosterSize: Float = LibraryPreferenceSpecs.LIBRARY_POSTER_SIZE.default,
    /** Render the server clear-logo as the detail-screen title instead of text. */
    val preferLogos: Boolean = LibraryPreferenceSpecs.PREFER_LOGOS.default,
    val libraryGroupBy: GroupBy = LibraryPreferenceSpecs.LIBRARY_GROUP_BY.default,
    val confirmLibraryReset: Boolean = LibraryPreferenceSpecs.CONFIRM_LIBRARY_RESET.default,
    /** Show provider external-link buttons (TMDb / IMDb / server `externalUrls`) on the detail header. */
    val showExternalLinks: Boolean = LibraryPreferenceSpecs.SHOW_EXTERNAL_LINKS.default,
    /** Show the item's first tagline under the detail title. */
    val showTagline: Boolean = LibraryPreferenceSpecs.SHOW_TAGLINE.default,
    /** Prefer server-hosted local trailers over remote (YouTube) entries in the trailer list. */
    val preferLocalTrailers: Boolean = LibraryPreferenceSpecs.PREFER_LOCAL_TRAILERS.default,
    /** Show the "More in this collection" franchise row on movie/series detail. */
    val showMoreInCollection: Boolean = LibraryPreferenceSpecs.SHOW_MORE_IN_COLLECTION.default,
    /** Show the "Airing next" strip on series detail. */
    val showUpcomingStrip: Boolean = LibraryPreferenceSpecs.SHOW_UPCOMING_STRIP.default,
    /** Play a series' ambient theme music on detail open. */
    val playThemeMusic: Boolean = LibraryPreferenceSpecs.PLAY_THEME_MUSIC.default,
    /** Show the reviews preview + ratings block on the detail screen. */
    val showReviews: Boolean = LibraryPreferenceSpecs.SHOW_REVIEWS.default,
    /** Show subtitle / audio layout stream badges on season episode rows. */
    val showEpisodeStreamBadges: Boolean = LibraryPreferenceSpecs.SHOW_EPISODE_STREAM_BADGES.default,
    /** Show role names + role grouping on the person screen's filmography. */
    val showFilmographyRoles: Boolean = LibraryPreferenceSpecs.SHOW_FILMOGRAPHY_ROLES.default,
    /** Show the "frequently appears with" co-star row on the person screen. */
    val showFrequentCostars: Boolean = LibraryPreferenceSpecs.SHOW_FREQUENT_COSTARS.default,
    /**
     * TV focus-gated trailer preview: mount the muted detail-hero embed only
     * while a Play/Trailer action holds focus (TV-only semantics; requires the
     * player's `trailerAutoplay`).
     */
    val trailerPreviewOnFocus: Boolean = LibraryPreferenceSpecs.TRAILER_PREVIEW_ON_FOCUS.default,
    /** Promote the ⋮ options' fixed promoted set into the landscape/TV detail rail. */
    val showPromotedActionsLandscape: Boolean = LibraryPreferenceSpecs.SHOW_PROMOTED_ACTIONS_LANDSCAPE.default,
    /** Show live download progress on season episode rows. */
    val showDownloadProgress: Boolean = LibraryPreferenceSpecs.SHOW_DOWNLOAD_PROGRESS.default,
)
