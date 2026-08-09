package com.raulshma.jellyplay.core.datastore.library

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

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
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class LibraryStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val DEFAULT_LIBRARY_SORT_ORDERS = stringPreferencesKey("default_library_sort_orders")
        val LIBRARY_VIEW_MODES = stringPreferencesKey("library_view_modes")
        val LIBRARY_FILTERS = stringPreferencesKey("library_filters")
        val HIDE_EPISODE_THUMBNAILS = booleanPreferencesKey("hide_episode_thumbnails")
        val EPISODES_DESCENDING = booleanPreferencesKey("episodes_descending")
        val SKIP_SPECIALS = booleanPreferencesKey("skip_specials")
        val COMPACT_EPISODE_LIST = booleanPreferencesKey("compact_episode_list")
        val SHOW_DETAIL_UP_NEXT = booleanPreferencesKey("show_detail_up_next")
        val LIBRARY_POSTER_SIZE = floatPreferencesKey("library_poster_size")
        val LIBRARY_GROUP_BY = stringPreferencesKey("library_group_by")
        val CONFIRM_LIBRARY_RESET = booleanPreferencesKey("confirm_library_reset")
    }

    private var cachedDefaultLibrarySortOrders = ParsedCache<Map<String, String>>(null, emptyMap())
    private var cachedLibraryViewModes = ParsedCache<Map<String, String>>(null, emptyMap())
    private var cachedLibraryFilters = ParsedCache<Map<String, String>>(null, emptyMap())

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val library: StateFlow<LibrarySlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LibrarySlice())

    internal fun read(prefs: Preferences): LibrarySlice = LibrarySlice(
        libraryViewMode = readLibraryViewMode(prefs),
        defaultLibrarySortOrders = readDefaultLibrarySortOrders(prefs),
        libraryViewModes = readLibraryViewModes(prefs),
        libraryFilters = readLibraryFilters(prefs),
        hideEpisodeThumbnails = PreferenceCodec.readBool(prefs, Keys.HIDE_EPISODE_THUMBNAILS, "hide_episode_thumbnails", false),
        episodesDescending = PreferenceCodec.readBool(prefs, Keys.EPISODES_DESCENDING, "episodes_descending", true),
        skipSpecials = PreferenceCodec.readBool(prefs, Keys.SKIP_SPECIALS, "skip_specials", false),
        compactEpisodeList = PreferenceCodec.readBool(prefs, Keys.COMPACT_EPISODE_LIST, "compact_episode_list", false),
        showDetailUpNext = PreferenceCodec.readBool(prefs, Keys.SHOW_DETAIL_UP_NEXT, "show_detail_up_next", true),
        libraryPosterSize = prefs[Keys.LIBRARY_POSTER_SIZE] ?: DEFAULT_POSTER_SIZE,
        libraryGroupBy = readGroupBy(prefs),
        confirmLibraryReset = readConfirmLibraryReset(prefs),
    )

    private fun readConfirmLibraryReset(prefs: Preferences): Boolean = prefs[Keys.CONFIRM_LIBRARY_RESET] ?: true

    private fun readLibraryViewMode(prefs: Preferences): LibraryViewMode = try {
        LibraryViewMode.valueOf(prefs[Keys.LIBRARY_VIEW_MODE] ?: LibraryViewMode.GRID.name)
    } catch (_: Exception) { LibraryViewMode.GRID }

    private fun readGroupBy(prefs: Preferences): GroupBy = try {
        GroupBy.valueOf(prefs[Keys.LIBRARY_GROUP_BY] ?: GroupBy.NONE.name)
    } catch (_: Exception) { GroupBy.NONE }

    private fun readDefaultLibrarySortOrders(prefs: Preferences): Map<String, String> {
        val raw = prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS]
        return if (raw != cachedDefaultLibrarySortOrders.raw) {
            try {
                raw?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedDefaultLibrarySortOrders = ParsedCache(raw, it) }
        } else cachedDefaultLibrarySortOrders.value
    }

    private fun readLibraryViewModes(prefs: Preferences): Map<String, String> {
        val raw = prefs[Keys.LIBRARY_VIEW_MODES]
        return if (raw != cachedLibraryViewModes.raw) {
            try {
                raw?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedLibraryViewModes = ParsedCache(raw, it) }
        } else cachedLibraryViewModes.value
    }

    private fun readLibraryFilters(prefs: Preferences): Map<String, String> {
        val raw = prefs[Keys.LIBRARY_FILTERS]
        return if (raw != cachedLibraryFilters.raw) {
            try {
                raw?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedLibraryFilters = ParsedCache(raw, it) }
        } else cachedLibraryFilters.value
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { it[Keys.LIBRARY_VIEW_MODE] = mode.name }
    }

    suspend fun setDefaultLibrarySortOrder(libraryId: String, order: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, order) }
            prefs[Keys.DEFAULT_LIBRARY_SORT_ORDERS] = json.encodeToString(next)
        }
    }

    suspend fun setLibraryViewMode(libraryId: String, viewMode: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_VIEW_MODES]?.let {
                try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(libraryId, viewMode) }
            prefs[Keys.LIBRARY_VIEW_MODES] = json.encodeToString(next)
        }
    }

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
        dataStore.edit { it[Keys.HIDE_EPISODE_THUMBNAILS] = enabled }
    }

    suspend fun setEpisodesDescending(descending: Boolean) {
        dataStore.edit { it[Keys.EPISODES_DESCENDING] = descending }
    }

    suspend fun setSkipSpecials(enabled: Boolean) {
        dataStore.edit { it[Keys.SKIP_SPECIALS] = enabled }
    }

    suspend fun setCompactEpisodeList(enabled: Boolean) {
        dataStore.edit { it[Keys.COMPACT_EPISODE_LIST] = enabled }
    }

    suspend fun setShowDetailUpNext(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_DETAIL_UP_NEXT] = enabled }
    }

    suspend fun setLibraryPosterSize(size: Float) {
        dataStore.edit { it[Keys.LIBRARY_POSTER_SIZE] = size.coerceIn(POSTER_SIZE_MIN, POSTER_SIZE_MAX) }
    }

    suspend fun setLibraryGroupBy(groupBy: GroupBy) {
        dataStore.edit { it[Keys.LIBRARY_GROUP_BY] = groupBy.name }
    }

    suspend fun setConfirmLibraryReset(enabled: Boolean) {
        dataStore.edit { it[Keys.CONFIRM_LIBRARY_RESET] = enabled }
    }

    /**
     * Keys owned by this store, for factory-reset participation. These are the
     * library-view/sort/filter + episode keys split out of the legacy
     * `HOME_DISCOVERY` reset category.
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.LIBRARY_VIEW_MODE,
        Keys.DEFAULT_LIBRARY_SORT_ORDERS, Keys.LIBRARY_VIEW_MODES, Keys.LIBRARY_FILTERS,
        Keys.HIDE_EPISODE_THUMBNAILS, Keys.EPISODES_DESCENDING, Keys.SKIP_SPECIALS,
        Keys.COMPACT_EPISODE_LIST, Keys.SHOW_DETAIL_UP_NEXT,
        Keys.LIBRARY_POSTER_SIZE, Keys.LIBRARY_GROUP_BY,
        Keys.CONFIRM_LIBRARY_RESET,
    )

    /**
     * Category reset participation: all seven keys owned here sit in the legacy
     * `HOME_DISCOVERY` bucket (the library-view/sort/filter + episode keys split
     * out of that category).
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.HOME_DISCOVERY -> listOf(
            Keys.LIBRARY_VIEW_MODE,
            Keys.DEFAULT_LIBRARY_SORT_ORDERS, Keys.LIBRARY_VIEW_MODES, Keys.LIBRARY_FILTERS,
            Keys.HIDE_EPISODE_THUMBNAILS, Keys.EPISODES_DESCENDING, Keys.SKIP_SPECIALS,
            Keys.COMPACT_EPISODE_LIST, Keys.SHOW_DETAIL_UP_NEXT,
            Keys.LIBRARY_POSTER_SIZE, Keys.LIBRARY_GROUP_BY,
            Keys.CONFIRM_LIBRARY_RESET,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the library keys owned by this store
     * from a decoded [UserPreferences]. JSON maps are written with this store's
     * own [json] codec.
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.LIBRARY_VIEW_MODE] = userPreferences.libraryViewMode.name
            it[Keys.DEFAULT_LIBRARY_SORT_ORDERS] = json.encodeToString(userPreferences.defaultLibrarySortOrders)
            it[Keys.LIBRARY_VIEW_MODES] = json.encodeToString(userPreferences.libraryViewModes)
            it[Keys.LIBRARY_FILTERS] = json.encodeToString(userPreferences.libraryFilters)
            it[Keys.HIDE_EPISODE_THUMBNAILS] = userPreferences.hideEpisodeThumbnails
            it[Keys.EPISODES_DESCENDING] = userPreferences.episodesDescending
            it[Keys.SKIP_SPECIALS] = userPreferences.skipSpecials
            it[Keys.COMPACT_EPISODE_LIST] = userPreferences.compactEpisodeList
            it[Keys.SHOW_DETAIL_UP_NEXT] = true
            it[Keys.CONFIRM_LIBRARY_RESET] = true
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (JSON maps via
     * this store's [json] codec).
     */
    suspend fun restore(slice: LibrarySlice) {
        dataStore.edit { it ->
            it[Keys.LIBRARY_VIEW_MODE] = slice.libraryViewMode.name
            it[Keys.DEFAULT_LIBRARY_SORT_ORDERS] = json.encodeToString(slice.defaultLibrarySortOrders)
            it[Keys.LIBRARY_VIEW_MODES] = json.encodeToString(slice.libraryViewModes)
            it[Keys.LIBRARY_FILTERS] = json.encodeToString(slice.libraryFilters)
            it[Keys.HIDE_EPISODE_THUMBNAILS] = slice.hideEpisodeThumbnails
            it[Keys.EPISODES_DESCENDING] = slice.episodesDescending
            it[Keys.SKIP_SPECIALS] = slice.skipSpecials
            it[Keys.COMPACT_EPISODE_LIST] = slice.compactEpisodeList
            it[Keys.SHOW_DETAIL_UP_NEXT] = slice.showDetailUpNext
            it[Keys.LIBRARY_POSTER_SIZE] = slice.libraryPosterSize
            it[Keys.LIBRARY_GROUP_BY] = slice.libraryGroupBy.name
            it[Keys.CONFIRM_LIBRARY_RESET] = slice.confirmLibraryReset
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
 * The library browsing preference slice. Plain data class. Defaults mirror the
 * projection defaults in [LibraryStore.read].
 */
@Immutable
@Serializable
data class LibrarySlice(
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
    val defaultLibrarySortOrders: Map<String, String> = emptyMap(),
    val libraryViewModes: Map<String, String> = emptyMap(),
    val libraryFilters: Map<String, String> = emptyMap(),
    val hideEpisodeThumbnails: Boolean = false,
    val episodesDescending: Boolean = true,
    val skipSpecials: Boolean = false,
    val compactEpisodeList: Boolean = false,
    val showDetailUpNext: Boolean = true,
    val libraryPosterSize: Float = DEFAULT_POSTER_SIZE,
    val libraryGroupBy: GroupBy = GroupBy.NONE,
    val confirmLibraryReset: Boolean = true,
)
