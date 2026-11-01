package com.raulshma.jellyplay.core.datastore.library

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.DetailBackdropResolution
import com.raulshma.jellyplay.core.model.DetailChapterCardSize
import com.raulshma.jellyplay.core.model.DetailDensity
import com.raulshma.jellyplay.core.model.DetailStaggerStyle
import com.raulshma.jellyplay.core.model.FilmographySort
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PromotedRailSource
import com.raulshma.jellyplay.core.model.SmartPlayPriority
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
        val DETAIL_CAST_ITEM_SIZE = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_CAST_ITEM_SIZE.keyName)
        val DETAIL_CAST_SEE_ALL_LIMIT = intPreferencesKey(LibraryPreferenceSpecs.DETAIL_CAST_SEE_ALL_LIMIT.keyName)
        val DETAIL_BACKDROP_PARALLAX = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_BACKDROP_PARALLAX.keyName)
        val DETAIL_OVERVIEW_COLLAPSE_LINES = intPreferencesKey(LibraryPreferenceSpecs.DETAIL_OVERVIEW_COLLAPSE_LINES.keyName)
        val DETAIL_HIDE_WATCHED_EPISODES = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_HIDE_WATCHED_EPISODES.keyName)
        val DETAIL_CONFIRM_MARK_PLAYED = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_CONFIRM_MARK_PLAYED.keyName)
        val DETAIL_SHOW_STUDIO_CHIPS = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_SHOW_STUDIO_CHIPS.keyName)
        val DETAIL_SHOW_GENRE_CHIPS = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_SHOW_GENRE_CHIPS.keyName)
        val DETAIL_SHOW_TAG_CHIPS = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_SHOW_TAG_CHIPS.keyName)
        val DETAIL_SHOW_SKIP_CHIPS = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_SHOW_SKIP_CHIPS.keyName)
        val DETAIL_SEASON_TABS_ARTWORK = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_SEASON_TABS_ARTWORK.keyName)
        val DETAIL_PEOPLE_GRID_DENSITY = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_PEOPLE_GRID_DENSITY.keyName)
        val DETAIL_SHOW_TRAILER_BADGES = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_SHOW_TRAILER_BADGES.keyName)
        val DETAIL_PERSIST_SECTION_HIDES = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_PERSIST_SECTION_HIDES.keyName)
        val DETAIL_HIDDEN_FRANCHISE_IDS = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_HIDDEN_FRANCHISE_IDS.keyName)
        val DETAIL_SIMILAR_LIMIT = intPreferencesKey(LibraryPreferenceSpecs.DETAIL_SIMILAR_LIMIT.keyName)
        val DETAIL_UPCOMING_LIMIT = intPreferencesKey(LibraryPreferenceSpecs.DETAIL_UPCOMING_LIMIT.keyName)
        // The detail-screen QoL knobs, wave 2.
        val DETAIL_SECTION_ORDER = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_SECTION_ORDER.keyName)
        val DETAIL_STAGGER_STYLE = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_STAGGER_STYLE.keyName)
        val DETAIL_BACKDROP_RESOLUTION = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_BACKDROP_RESOLUTION.keyName)
        val DETAIL_CHAPTER_CARD_SIZE = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_CHAPTER_CARD_SIZE.keyName)
        val DETAIL_REVIEWS_PREVIEW_LINES = intPreferencesKey(LibraryPreferenceSpecs.DETAIL_REVIEWS_PREVIEW_LINES.keyName)
        val DETAIL_FILMOGRAPHY_SORT = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_FILMOGRAPHY_SORT.keyName)
        val DETAIL_FRANCHISE_BOX_LIMIT = intPreferencesKey(LibraryPreferenceSpecs.DETAIL_FRANCHISE_BOX_LIMIT.keyName)
        val DETAIL_FRANCHISE_MEMBER_LIMIT = intPreferencesKey(LibraryPreferenceSpecs.DETAIL_FRANCHISE_MEMBER_LIMIT.keyName)
        val DETAIL_SMART_PLAY_PRIORITY = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_SMART_PLAY_PRIORITY.keyName)
        val DETAIL_STREAM_FALLBACK_LANGUAGE = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_STREAM_FALLBACK_LANGUAGE.keyName)
        val DETAIL_PROMOTED_RAIL_SOURCE = stringPreferencesKey(LibraryPreferenceSpecs.DETAIL_PROMOTED_RAIL_SOURCE.keyName)
        val DETAIL_EPISODE_OVERVIEW_PREVIEW = booleanPreferencesKey(LibraryPreferenceSpecs.DETAIL_EPISODE_OVERVIEW_PREVIEW.keyName)
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
        detailCastItemSize = LibraryPreferenceSpecs.DETAIL_CAST_ITEM_SIZE.readFrom(prefs),
        detailCastSeeAllLimit = LibraryPreferenceSpecs.DETAIL_CAST_SEE_ALL_LIMIT.readFrom(prefs),
        detailBackdropParallax = LibraryPreferenceSpecs.DETAIL_BACKDROP_PARALLAX.readFrom(prefs),
        detailOverviewCollapseLines = LibraryPreferenceSpecs.DETAIL_OVERVIEW_COLLAPSE_LINES.readFrom(prefs),
        detailHideWatchedEpisodes = LibraryPreferenceSpecs.DETAIL_HIDE_WATCHED_EPISODES.readFrom(prefs),
        detailConfirmMarkPlayed = LibraryPreferenceSpecs.DETAIL_CONFIRM_MARK_PLAYED.readFrom(prefs),
        detailShowStudioChips = LibraryPreferenceSpecs.DETAIL_SHOW_STUDIO_CHIPS.readFrom(prefs),
        detailShowGenreChips = LibraryPreferenceSpecs.DETAIL_SHOW_GENRE_CHIPS.readFrom(prefs),
        detailShowTagChips = LibraryPreferenceSpecs.DETAIL_SHOW_TAG_CHIPS.readFrom(prefs),
        detailShowSkipChips = LibraryPreferenceSpecs.DETAIL_SHOW_SKIP_CHIPS.readFrom(prefs),
        detailSeasonTabsArtwork = LibraryPreferenceSpecs.DETAIL_SEASON_TABS_ARTWORK.readFrom(prefs),
        detailPeopleGridDensity = LibraryPreferenceSpecs.DETAIL_PEOPLE_GRID_DENSITY.readFrom(prefs),
        detailShowTrailerBadges = LibraryPreferenceSpecs.DETAIL_SHOW_TRAILER_BADGES.readFrom(prefs),
        detailPersistSectionHides = LibraryPreferenceSpecs.DETAIL_PERSIST_SECTION_HIDES.readFrom(prefs),
        detailHiddenFranchiseIds = LibraryPreferenceSpecs.DETAIL_HIDDEN_FRANCHISE_IDS.readFrom(prefs),
        detailSimilarLimit = LibraryPreferenceSpecs.DETAIL_SIMILAR_LIMIT.readFrom(prefs),
        detailUpcomingLimit = LibraryPreferenceSpecs.DETAIL_UPCOMING_LIMIT.readFrom(prefs),
        // The wave-2 knobs: the two declaration-only string rows via their
        // plain readStored (the downloads list-state precedent), the rest via
        // their row encodings.
        detailSectionOrder = LibraryPreferenceSpecs.DETAIL_SECTION_ORDER.readStored(prefs) ?: "",
        detailStaggerStyle = LibraryPreferenceSpecs.DETAIL_STAGGER_STYLE.readFrom(prefs),
        detailBackdropResolution = LibraryPreferenceSpecs.DETAIL_BACKDROP_RESOLUTION.readFrom(prefs),
        detailChapterCardSize = LibraryPreferenceSpecs.DETAIL_CHAPTER_CARD_SIZE.readFrom(prefs),
        detailReviewsPreviewLines = LibraryPreferenceSpecs.DETAIL_REVIEWS_PREVIEW_LINES.readFrom(prefs),
        detailFilmographySort = LibraryPreferenceSpecs.DETAIL_FILMOGRAPHY_SORT.readFrom(prefs),
        detailFranchiseBoxLimit = LibraryPreferenceSpecs.DETAIL_FRANCHISE_BOX_LIMIT.readFrom(prefs),
        detailFranchiseMemberLimit = LibraryPreferenceSpecs.DETAIL_FRANCHISE_MEMBER_LIMIT.readFrom(prefs),
        detailSmartPlayPriority = LibraryPreferenceSpecs.DETAIL_SMART_PLAY_PRIORITY.readFrom(prefs),
        detailStreamFallbackLanguage = LibraryPreferenceSpecs.DETAIL_STREAM_FALLBACK_LANGUAGE.readStored(prefs)
            ?: DETAIL_STREAM_FALLBACK_LANGUAGE_DEFAULT,
        detailPromotedRailSource = LibraryPreferenceSpecs.DETAIL_PROMOTED_RAIL_SOURCE.readFrom(prefs),
        detailEpisodeOverviewPreview = LibraryPreferenceSpecs.DETAIL_EPISODE_OVERVIEW_PREVIEW.readFrom(prefs),
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

    suspend fun setDetailCastItemSize(density: DetailDensity) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_CAST_ITEM_SIZE.writeTo(it, density) }
    }

    /** Cast "See all" threshold; clamped to [DETAIL_CAST_SEE_ALL_LIMIT_MIN]..[DETAIL_CAST_SEE_ALL_LIMIT_MAX]. */
    suspend fun setDetailCastSeeAllLimit(limit: Int) {
        dataStore.edit {
            LibraryPreferenceSpecs.DETAIL_CAST_SEE_ALL_LIMIT.writeTo(
                it,
                limit.coerceIn(DETAIL_CAST_SEE_ALL_LIMIT_MIN, DETAIL_CAST_SEE_ALL_LIMIT_MAX),
            )
        }
    }

    suspend fun setDetailBackdropParallax(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_BACKDROP_PARALLAX.writeTo(it, enabled) }
    }

    /** Overview collapsed line count; clamped to [DETAIL_OVERVIEW_COLLAPSE_LINES_MIN]..[DETAIL_OVERVIEW_COLLAPSE_LINES_MAX]. */
    suspend fun setDetailOverviewCollapseLines(lines: Int) {
        dataStore.edit {
            LibraryPreferenceSpecs.DETAIL_OVERVIEW_COLLAPSE_LINES.writeTo(
                it,
                lines.coerceIn(DETAIL_OVERVIEW_COLLAPSE_LINES_MIN, DETAIL_OVERVIEW_COLLAPSE_LINES_MAX),
            )
        }
    }

    suspend fun setDetailHideWatchedEpisodes(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_HIDE_WATCHED_EPISODES.writeTo(it, enabled) }
    }

    suspend fun setDetailConfirmMarkPlayed(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_CONFIRM_MARK_PLAYED.writeTo(it, enabled) }
    }

    suspend fun setDetailShowStudioChips(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_SHOW_STUDIO_CHIPS.writeTo(it, enabled) }
    }

    suspend fun setDetailShowGenreChips(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_SHOW_GENRE_CHIPS.writeTo(it, enabled) }
    }

    suspend fun setDetailShowTagChips(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_SHOW_TAG_CHIPS.writeTo(it, enabled) }
    }

    suspend fun setDetailShowSkipChips(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_SHOW_SKIP_CHIPS.writeTo(it, enabled) }
    }

    suspend fun setDetailSeasonTabsArtwork(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_SEASON_TABS_ARTWORK.writeTo(it, enabled) }
    }

    suspend fun setDetailPeopleGridDensity(density: DetailDensity) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_PEOPLE_GRID_DENSITY.writeTo(it, density) }
    }

    suspend fun setDetailShowTrailerBadges(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_SHOW_TRAILER_BADGES.writeTo(it, enabled) }
    }

    suspend fun setDetailPersistSectionHides(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_PERSIST_SECTION_HIDES.writeTo(it, enabled) }
    }

    /** The persisted franchise eye-hide memory (whole-set write; the DetailViewModel merges). */
    suspend fun setDetailHiddenFranchiseIds(ids: Set<String>) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_HIDDEN_FRANCHISE_IDS.writeTo(it, ids) }
    }

    /** "More like this" row bound; clamped to [DETAIL_SIMILAR_LIMIT_MIN]..[DETAIL_SIMILAR_LIMIT_MAX]. */
    suspend fun setDetailSimilarLimit(limit: Int) {
        dataStore.edit {
            LibraryPreferenceSpecs.DETAIL_SIMILAR_LIMIT.writeTo(
                it,
                limit.coerceIn(DETAIL_SIMILAR_LIMIT_MIN, DETAIL_SIMILAR_LIMIT_MAX),
            )
        }
    }

    /** "Airing next" strip bound; clamped to [DETAIL_UPCOMING_LIMIT_MIN]..[DETAIL_UPCOMING_LIMIT_MAX]. */
    suspend fun setDetailUpcomingLimit(limit: Int) {
        dataStore.edit {
            LibraryPreferenceSpecs.DETAIL_UPCOMING_LIMIT.writeTo(
                it,
                limit.coerceIn(DETAIL_UPCOMING_LIMIT_MIN, DETAIL_UPCOMING_LIMIT_MAX),
            )
        }
    }

    // ── The wave-2 detail QoL setters ────────────────────────────────────

    /**
     * The detail body's section permutation: normalizes the token list
     * (trim, drop empties, dedupe keeping first occurrence) and stores the
     * joined CSV. Tokens are NOT validated here — the consumption side
     * reconciles against the known section list (unknown dropped, missing
     * appended), so a settings-vocabulary drift can never strand a stale
     * stored value.
     */
    suspend fun setDetailSectionOrder(order: List<String>) {
        val csv = order.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        dataStore.edit { it[Keys.DETAIL_SECTION_ORDER] = csv }
    }

    suspend fun setDetailStaggerStyle(style: DetailStaggerStyle) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_STAGGER_STYLE.writeTo(it, style) }
    }

    suspend fun setDetailBackdropResolution(resolution: DetailBackdropResolution) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_BACKDROP_RESOLUTION.writeTo(it, resolution) }
    }

    suspend fun setDetailChapterCardSize(size: DetailChapterCardSize) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_CHAPTER_CARD_SIZE.writeTo(it, size) }
    }

    /** Review-card preview line count; clamped to [DETAIL_REVIEWS_PREVIEW_LINES_MIN]..[DETAIL_REVIEWS_PREVIEW_LINES_MAX]. */
    suspend fun setDetailReviewsPreviewLines(lines: Int) {
        dataStore.edit {
            LibraryPreferenceSpecs.DETAIL_REVIEWS_PREVIEW_LINES.writeTo(
                it,
                lines.coerceIn(DETAIL_REVIEWS_PREVIEW_LINES_MIN, DETAIL_REVIEWS_PREVIEW_LINES_MAX),
            )
        }
    }

    suspend fun setDetailFilmographySort(sort: FilmographySort) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_FILMOGRAPHY_SORT.writeTo(it, sort) }
    }

    /** Franchise BoxSet-row bound; clamped to [DETAIL_FRANCHISE_BOX_LIMIT_MIN]..[DETAIL_FRANCHISE_BOX_LIMIT_MAX]. */
    suspend fun setDetailFranchiseBoxLimit(limit: Int) {
        dataStore.edit {
            LibraryPreferenceSpecs.DETAIL_FRANCHISE_BOX_LIMIT.writeTo(
                it,
                limit.coerceIn(DETAIL_FRANCHISE_BOX_LIMIT_MIN, DETAIL_FRANCHISE_BOX_LIMIT_MAX),
            )
        }
    }

    /** Franchise per-row member page bound; clamped to [DETAIL_FRANCHISE_MEMBER_LIMIT_MIN]..[DETAIL_FRANCHISE_MEMBER_LIMIT_MAX]. */
    suspend fun setDetailFranchiseMemberLimit(limit: Int) {
        dataStore.edit {
            LibraryPreferenceSpecs.DETAIL_FRANCHISE_MEMBER_LIMIT.writeTo(
                it,
                limit.coerceIn(DETAIL_FRANCHISE_MEMBER_LIMIT_MIN, DETAIL_FRANCHISE_MEMBER_LIMIT_MAX),
            )
        }
    }

    suspend fun setDetailSmartPlayPriority(priority: SmartPlayPriority) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_SMART_PLAY_PRIORITY.writeTo(it, priority) }
    }

    /**
     * The stream-picker fallback language: lowercases the token and accepts
     * only 3-letter codes — anything else resets to
     * [DETAIL_STREAM_FALLBACK_LANGUAGE_DEFAULT] (the shipped "eng").
     */
    suspend fun setDetailStreamFallbackLanguage(language: String) {
        val normalized = language.trim().lowercase()
        val stored = if (normalized.length == 3 && normalized.all { it in 'a'..'z' }) {
            normalized
        } else {
            DETAIL_STREAM_FALLBACK_LANGUAGE_DEFAULT
        }
        dataStore.edit { it[Keys.DETAIL_STREAM_FALLBACK_LANGUAGE] = stored }
    }

    suspend fun setDetailPromotedRailSource(source: PromotedRailSource) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_PROMOTED_RAIL_SOURCE.writeTo(it, source) }
    }

    suspend fun setDetailEpisodeOverviewPreview(enabled: Boolean) {
        dataStore.edit { LibraryPreferenceSpecs.DETAIL_EPISODE_OVERVIEW_PREVIEW.writeTo(it, enabled) }
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
            LibraryPreferenceSpecs.DETAIL_CAST_ITEM_SIZE.writeTo(prefs, slice.detailCastItemSize)
            LibraryPreferenceSpecs.DETAIL_CAST_SEE_ALL_LIMIT.writeTo(prefs, slice.detailCastSeeAllLimit)
            LibraryPreferenceSpecs.DETAIL_BACKDROP_PARALLAX.writeTo(prefs, slice.detailBackdropParallax)
            LibraryPreferenceSpecs.DETAIL_OVERVIEW_COLLAPSE_LINES.writeTo(prefs, slice.detailOverviewCollapseLines)
            LibraryPreferenceSpecs.DETAIL_HIDE_WATCHED_EPISODES.writeTo(prefs, slice.detailHideWatchedEpisodes)
            LibraryPreferenceSpecs.DETAIL_CONFIRM_MARK_PLAYED.writeTo(prefs, slice.detailConfirmMarkPlayed)
            LibraryPreferenceSpecs.DETAIL_SHOW_STUDIO_CHIPS.writeTo(prefs, slice.detailShowStudioChips)
            LibraryPreferenceSpecs.DETAIL_SHOW_GENRE_CHIPS.writeTo(prefs, slice.detailShowGenreChips)
            LibraryPreferenceSpecs.DETAIL_SHOW_TAG_CHIPS.writeTo(prefs, slice.detailShowTagChips)
            LibraryPreferenceSpecs.DETAIL_SHOW_SKIP_CHIPS.writeTo(prefs, slice.detailShowSkipChips)
            LibraryPreferenceSpecs.DETAIL_SEASON_TABS_ARTWORK.writeTo(prefs, slice.detailSeasonTabsArtwork)
            LibraryPreferenceSpecs.DETAIL_PEOPLE_GRID_DENSITY.writeTo(prefs, slice.detailPeopleGridDensity)
            LibraryPreferenceSpecs.DETAIL_SHOW_TRAILER_BADGES.writeTo(prefs, slice.detailShowTrailerBadges)
            LibraryPreferenceSpecs.DETAIL_PERSIST_SECTION_HIDES.writeTo(prefs, slice.detailPersistSectionHides)
            LibraryPreferenceSpecs.DETAIL_HIDDEN_FRANCHISE_IDS.writeTo(prefs, slice.detailHiddenFranchiseIds)
            LibraryPreferenceSpecs.DETAIL_SIMILAR_LIMIT.writeTo(prefs, slice.detailSimilarLimit)
            LibraryPreferenceSpecs.DETAIL_UPCOMING_LIMIT.writeTo(prefs, slice.detailUpcomingLimit)
            // The wave-2 knobs: the two declaration-only string rows keep
            // their hand-written plain slot writes (the confirm-reset
            // precedent), the rest delegate to their row encodings.
            prefs[Keys.DETAIL_SECTION_ORDER] = slice.detailSectionOrder
            LibraryPreferenceSpecs.DETAIL_STAGGER_STYLE.writeTo(prefs, slice.detailStaggerStyle)
            LibraryPreferenceSpecs.DETAIL_BACKDROP_RESOLUTION.writeTo(prefs, slice.detailBackdropResolution)
            LibraryPreferenceSpecs.DETAIL_CHAPTER_CARD_SIZE.writeTo(prefs, slice.detailChapterCardSize)
            LibraryPreferenceSpecs.DETAIL_REVIEWS_PREVIEW_LINES.writeTo(prefs, slice.detailReviewsPreviewLines)
            LibraryPreferenceSpecs.DETAIL_FILMOGRAPHY_SORT.writeTo(prefs, slice.detailFilmographySort)
            LibraryPreferenceSpecs.DETAIL_FRANCHISE_BOX_LIMIT.writeTo(prefs, slice.detailFranchiseBoxLimit)
            LibraryPreferenceSpecs.DETAIL_FRANCHISE_MEMBER_LIMIT.writeTo(prefs, slice.detailFranchiseMemberLimit)
            LibraryPreferenceSpecs.DETAIL_SMART_PLAY_PRIORITY.writeTo(prefs, slice.detailSmartPlayPriority)
            prefs[Keys.DETAIL_STREAM_FALLBACK_LANGUAGE] = slice.detailStreamFallbackLanguage
            LibraryPreferenceSpecs.DETAIL_PROMOTED_RAIL_SOURCE.writeTo(prefs, slice.detailPromotedRailSource)
            LibraryPreferenceSpecs.DETAIL_EPISODE_OVERVIEW_PREVIEW.writeTo(prefs, slice.detailEpisodeOverviewPreview)
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
 * The detail-screen QoL int knobs' bounds. Catalog metadata lives on the
 * rows; these are the clamp owners' (the setters') bounds — the shipped
 * values and the range the settings pickers offer.
 */
internal const val DETAIL_CAST_SEE_ALL_LIMIT_DEFAULT = 12
internal const val DETAIL_CAST_SEE_ALL_LIMIT_MIN = 6
internal const val DETAIL_CAST_SEE_ALL_LIMIT_MAX = 24
internal const val DETAIL_OVERVIEW_COLLAPSE_LINES_DEFAULT = 3
internal const val DETAIL_OVERVIEW_COLLAPSE_LINES_MIN = 3
internal const val DETAIL_OVERVIEW_COLLAPSE_LINES_MAX = 8
internal const val DETAIL_SIMILAR_LIMIT_DEFAULT = 12
internal const val DETAIL_SIMILAR_LIMIT_MIN = 6
internal const val DETAIL_SIMILAR_LIMIT_MAX = 24
internal const val DETAIL_UPCOMING_LIMIT_DEFAULT = 12
internal const val DETAIL_UPCOMING_LIMIT_MIN = 4
internal const val DETAIL_UPCOMING_LIMIT_MAX = 24

/**
 * The wave-2 detail QoL int knobs' bounds (catalog metadata on the rows; the
 * setters are the clamp owners) and the validated string row's default.
 */
internal const val DETAIL_REVIEWS_PREVIEW_LINES_DEFAULT = 6
internal const val DETAIL_REVIEWS_PREVIEW_LINES_MIN = 2
internal const val DETAIL_REVIEWS_PREVIEW_LINES_MAX = 12
internal const val DETAIL_FRANCHISE_BOX_LIMIT_DEFAULT = 2
internal const val DETAIL_FRANCHISE_BOX_LIMIT_MIN = 1
internal const val DETAIL_FRANCHISE_BOX_LIMIT_MAX = 5
internal const val DETAIL_FRANCHISE_MEMBER_LIMIT_DEFAULT = 50
internal const val DETAIL_FRANCHISE_MEMBER_LIMIT_MIN = 10
internal const val DETAIL_FRANCHISE_MEMBER_LIMIT_MAX = 100
internal const val DETAIL_STREAM_FALLBACK_LANGUAGE_DEFAULT = "eng"

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
    // ── The detail-screen QoL knobs ──────────────────────────────────────

    /** Cast rail item size on the media-detail header (people geometry). */
    val detailCastItemSize: DetailDensity = LibraryPreferenceSpecs.DETAIL_CAST_ITEM_SIZE.default,
    /** Cast "See all" threshold: people hide behind it above this count. */
    val detailCastSeeAllLimit: Int = LibraryPreferenceSpecs.DETAIL_CAST_SEE_ALL_LIMIT.default,
    /** Parallax (translate + zoom) scroll on the detail hero backdrop. */
    val detailBackdropParallax: Boolean = LibraryPreferenceSpecs.DETAIL_BACKDROP_PARALLAX.default,
    /** Overview synopsis collapsed line count (the "Read more" toggle). */
    val detailOverviewCollapseLines: Int = LibraryPreferenceSpecs.DETAIL_OVERVIEW_COLLAPSE_LINES.default,
    /** Hide played episodes from season lists. */
    val detailHideWatchedEpisodes: Boolean = LibraryPreferenceSpecs.DETAIL_HIDE_WATCHED_EPISODES.default,
    /** Ask for confirmation before marking a single item played/unplayed. */
    val detailConfirmMarkPlayed: Boolean = LibraryPreferenceSpecs.DETAIL_CONFIRM_MARK_PLAYED.default,
    /** Header studio chips. */
    val detailShowStudioChips: Boolean = LibraryPreferenceSpecs.DETAIL_SHOW_STUDIO_CHIPS.default,
    /** Header genre chips. */
    val detailShowGenreChips: Boolean = LibraryPreferenceSpecs.DETAIL_SHOW_GENRE_CHIPS.default,
    /** Header tag chips. */
    val detailShowTagChips: Boolean = LibraryPreferenceSpecs.DETAIL_SHOW_TAG_CHIPS.default,
    /** Header skip-intro/credits availability chips. */
    val detailShowSkipChips: Boolean = LibraryPreferenceSpecs.DETAIL_SHOW_SKIP_CHIPS.default,
    /** Small poster thumbnails on the season tab bar. */
    val detailSeasonTabsArtwork: Boolean = LibraryPreferenceSpecs.DETAIL_SEASON_TABS_ARTWORK.default,
    /** Cast & Crew grid density (the grid's column minimum). */
    val detailPeopleGridDensity: DetailDensity = LibraryPreferenceSpecs.DETAIL_PEOPLE_GRID_DENSITY.default,
    /** Local/remote source badges on the trailer cards. */
    val detailShowTrailerBadges: Boolean = LibraryPreferenceSpecs.DETAIL_SHOW_TRAILER_BADGES.default,
    /** Persist the detail screen's inline section hides (the franchise eye-hide memory). */
    val detailPersistSectionHides: Boolean = LibraryPreferenceSpecs.DETAIL_PERSIST_SECTION_HIDES.default,
    /** The persisted franchise eye-hide memory (collection ids). */
    val detailHiddenFranchiseIds: Set<String> = LibraryPreferenceSpecs.DETAIL_HIDDEN_FRANCHISE_IDS.default,
    /** "More like this" row bound (both stock arms). */
    val detailSimilarLimit: Int = LibraryPreferenceSpecs.DETAIL_SIMILAR_LIMIT.default,
    /** "Airing next" strip bound. */
    val detailUpcomingLimit: Int = LibraryPreferenceSpecs.DETAIL_UPCOMING_LIMIT.default,
    // ── The detail-screen QoL knobs, wave 2 ──────────────────────────────

    /** The detail body's stored section permutation (CSV; empty = canonical order). */
    val detailSectionOrder: String = LibraryPreferenceSpecs.DETAIL_SECTION_ORDER.default ?: "",
    /** Entrance-stagger step (FAST/DEFAULT/SLOW). */
    val detailStaggerStyle: DetailStaggerStyle = LibraryPreferenceSpecs.DETAIL_STAGGER_STYLE.default,
    /** Hero-backdrop decode target (720p/1080p/1440p). */
    val detailBackdropResolution: DetailBackdropResolution = LibraryPreferenceSpecs.DETAIL_BACKDROP_RESOLUTION.default,
    /** Chapter thumbnail card size multiplier (0.8/1.0/1.2). */
    val detailChapterCardSize: DetailChapterCardSize = LibraryPreferenceSpecs.DETAIL_CHAPTER_CARD_SIZE.default,
    /** Review-card preview line count (clamped 2..12). */
    val detailReviewsPreviewLines: Int = LibraryPreferenceSpecs.DETAIL_REVIEWS_PREVIEW_LINES.default,
    /** Person filmography ordering. */
    val detailFilmographySort: FilmographySort = LibraryPreferenceSpecs.DETAIL_FILMOGRAPHY_SORT.default,
    /** Franchise BoxSet-row bound (clamped 1..5). */
    val detailFranchiseBoxLimit: Int = LibraryPreferenceSpecs.DETAIL_FRANCHISE_BOX_LIMIT.default,
    /** Franchise per-row member page bound (clamped 10..100). */
    val detailFranchiseMemberLimit: Int = LibraryPreferenceSpecs.DETAIL_FRANCHISE_MEMBER_LIMIT.default,
    /** Smart-play dispatch candidate preference. */
    val detailSmartPlayPriority: SmartPlayPriority = LibraryPreferenceSpecs.DETAIL_SMART_PLAY_PRIORITY.default,
    /** Stream-picker fallback language (3-letter code). */
    val detailStreamFallbackLanguage: String = LibraryPreferenceSpecs.DETAIL_STREAM_FALLBACK_LANGUAGE.default
        ?: DETAIL_STREAM_FALLBACK_LANGUAGE_DEFAULT,
    /** Which ⋮-menu preset the landscape/TV promoted rail shows. */
    val detailPromotedRailSource: PromotedRailSource = LibraryPreferenceSpecs.DETAIL_PROMOTED_RAIL_SOURCE.default,
    /** 2-line overview snippets on the season lists' episode rows. */
    val detailEpisodeOverviewPreview: Boolean = LibraryPreferenceSpecs.DETAIL_EPISODE_OVERVIEW_PREVIEW.default,
)
