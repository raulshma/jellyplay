package com.raulshma.jellyplay.core.datastore.home

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning the **home discovery** preference domain: home mode, hero
 * + backdrop toggles, the configurable section-type enable set + ordering, the
 * per-library section overrides (with a one-shot legacy migration from the old
 * all-or-nothing "hide library from home" key), pinned sections, layout
 * presets, continue-watching click behaviour, the watched/badge/ratings home
 * toggles, the Next-Up + hidden-CW item lists, and the home clock + settings
 * search toggles.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the read-modify-write JSON list/map invariants),
 * its read projection, its legacy migration, and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class HomeDiscoveryStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    init {
        // One-shot legacy migration of the all-or-nothing "hide library from
        // home" Set<String> into the per-library section-override map. Runs once
        // at construction (idempotent — drops the legacy key once migrated) so
        // `read` stays a pure function of the snapshot.
        scope.launch { migrateHiddenLibrarySectionIds() }
    }

    internal suspend fun migrateHiddenLibrarySectionIds() {
        dataStore.edit { prefs ->
            val legacyRaw = prefs[Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS] ?: return@edit
            val overridesRaw = prefs[Keys.HOME_LIBRARY_SECTION_OVERRIDES]
            val overridesEmpty = overridesRaw == null
            if (!overridesEmpty) return@edit // already migrated; leave as-is
            try {
                val legacyIds = json.decodeFromString<Set<String>>(legacyRaw)
                if (legacyIds.isEmpty()) return@edit
                val migrated = legacyIds.associateWith {
                    setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED)
                }
                prefs[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(migrated)
            } catch (_: Exception) { return@edit }
            prefs.remove(Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS)
        }
    }

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val HOME_MODE = stringPreferencesKey("home_mode")
        val HOME_HERO_ENABLED = booleanPreferencesKey("home_hero_enabled")
        val HOME_BACKDROP_ENABLED = booleanPreferencesKey("home_backdrop_enabled")
        val HOME_ENABLED_SECTION_TYPES = stringPreferencesKey("home_enabled_section_types")
        val HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        val HOME_LIBRARY_SECTION_OVERRIDES = stringPreferencesKey("home_library_section_overrides")
        /** Legacy all-or-nothing "hide library from home" key — kept only to migrate. */
        val HOME_HIDDEN_LIBRARY_SECTION_IDS = stringPreferencesKey("home_hidden_library_section_ids")
        val PINNED_HOME_SECTIONS = stringPreferencesKey("pinned_home_sections")
        val HOME_LAYOUT_PRESETS = stringPreferencesKey("home_layout_presets")
        val CONTINUE_WATCHING_CLICK_BEHAVIOR = stringPreferencesKey("continue_watching_click_behavior")
        val SHOW_UNWATCHED_BADGE = booleanPreferencesKey("show_unwatched_badge")
        val HIDE_WATCHED_ITEMS = booleanPreferencesKey("hide_watched_items")
        val SHOW_WATCHED_CHECKMARK = booleanPreferencesKey("show_watched_checkmark")
        val SHOW_EXTERNAL_RATINGS = booleanPreferencesKey("show_external_ratings")
        val MERGE_CONTINUE_WATCHING_NEXT_UP = booleanPreferencesKey("merge_continue_watching_next_up")
        val NEXT_UP_MAX_DAYS = intPreferencesKey("next_up_max_days")
        val NEXT_UP_REWATCHING = booleanPreferencesKey("next_up_rewatching")
        val NEXT_UP_EXCLUDED_SERIES_IDS = stringPreferencesKey("next_up_excluded_series_ids")
        val HIDDEN_CW_ITEM_IDS = stringPreferencesKey("hidden_cw_item_ids")
        val SHOW_CLOCK_ON_HOME = booleanPreferencesKey("show_clock_on_home")
        val SHOW_SETTINGS_IN_HOME_SEARCH = booleanPreferencesKey("show_settings_in_home_search")
    }

    private var cachedEnabledHomeSectionTypes = ParsedCache<Set<HomeSectionType>>(null, HomeSectionType.CONFIGURABLE.toSet())
    private var cachedHomeSectionOrder = ParsedCache<List<HomeSectionType>>(null, HomeSectionType.CONFIGURABLE)
    private var cachedLibraryHomeSectionOverrides = ParsedCache<Map<String, Set<HomeSectionType>>>(null, emptyMap())
    private var cachedPinnedHomeSections = ParsedCache<List<PinnedHomeSection>>(null, emptyList())
    private var cachedHomeLayoutPresets = ParsedCache<List<HomeLayoutPreset>>(null, emptyList())
    private var cachedNextUpExcludedSeriesIds = ParsedCache<Set<String>>(null, emptySet())
    private var cachedHiddenCwItemIds = ParsedCache<Set<String>>(null, emptySet())

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val homeDiscovery: StateFlow<HomeDiscoverySlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, HomeDiscoverySlice())

    internal fun read(prefs: Preferences): HomeDiscoverySlice = HomeDiscoverySlice(
        homeMode = readHomeMode(prefs),
        homeHeroEnabled = PreferenceCodec.readBool(prefs, Keys.HOME_HERO_ENABLED, "home_hero_enabled", true),
        homeBackdropEnabled = PreferenceCodec.readBool(prefs, Keys.HOME_BACKDROP_ENABLED, "home_backdrop_enabled", true),
        enabledHomeSectionTypes = readEnabledHomeSectionTypes(prefs),
        homeSectionOrder = readHomeSectionOrder(prefs),
        libraryHomeSectionOverrides = readLibraryHomeSectionOverrides(prefs),
        pinnedHomeSections = readPinnedHomeSections(prefs),
        homeLayoutPresets = readHomeLayoutPresets(prefs),
        continueWatchingClickBehavior = readContinueWatchingClickBehavior(prefs),
        showUnwatchedBadge = PreferenceCodec.readBool(prefs, Keys.SHOW_UNWATCHED_BADGE, "show_unwatched_badge", true),
        hideWatchedItems = PreferenceCodec.readBool(prefs, Keys.HIDE_WATCHED_ITEMS, "hide_watched_items", false),
        showWatchedCheckmark = PreferenceCodec.readBool(prefs, Keys.SHOW_WATCHED_CHECKMARK, "show_watched_checkmark", true),
        showExternalRatings = PreferenceCodec.readBool(prefs, Keys.SHOW_EXTERNAL_RATINGS, "show_external_ratings", true),
        mergeContinueWatchingAndNextUp = PreferenceCodec.readBool(prefs, Keys.MERGE_CONTINUE_WATCHING_NEXT_UP, "merge_continue_watching_next_up", false),
        nextUpMaxDays = PreferenceCodec.readInt(prefs, Keys.NEXT_UP_MAX_DAYS, "next_up_max_days", 0),
        nextUpRewatching = PreferenceCodec.readBool(prefs, Keys.NEXT_UP_REWATCHING, "next_up_rewatching", false),
        nextUpExcludedSeriesIds = readNextUpExcludedSeriesIds(prefs),
        hiddenCwItemIds = readHiddenCwItemIds(prefs),
        showClockOnHome = PreferenceCodec.readBool(prefs, Keys.SHOW_CLOCK_ON_HOME, "show_clock_on_home", false),
        showSettingsInHomeSearch = PreferenceCodec.readBool(prefs, Keys.SHOW_SETTINGS_IN_HOME_SEARCH, "show_settings_in_home_search", true),
    )

    private fun readHomeMode(prefs: Preferences): HomeMode = try {
        HomeMode.valueOf(prefs[Keys.HOME_MODE] ?: HomeMode.VIDEO.name)
    } catch (_: Exception) { HomeMode.VIDEO }

    private fun readContinueWatchingClickBehavior(prefs: Preferences): ContinueWatchingClickBehavior = try {
        ContinueWatchingClickBehavior.valueOf(prefs[Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR] ?: ContinueWatchingClickBehavior.DETAILS.name)
    } catch (_: Exception) { ContinueWatchingClickBehavior.DETAILS }

    private fun readEnabledHomeSectionTypes(prefs: Preferences): Set<HomeSectionType> {
        val raw = prefs[Keys.HOME_ENABLED_SECTION_TYPES]
        return if (raw != cachedEnabledHomeSectionTypes.raw) {
            try {
                raw?.let {
                    json.decodeFromString<Set<String>>(it)
                        .mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                        .toSet()
                } ?: HomeSectionType.CONFIGURABLE.toSet()
            } catch (_: Exception) { HomeSectionType.CONFIGURABLE.toSet() }
                .also { cachedEnabledHomeSectionTypes = ParsedCache(raw, it) }
        } else cachedEnabledHomeSectionTypes.value
    }

    private fun readHomeSectionOrder(prefs: Preferences): List<HomeSectionType> {
        val raw = prefs[Keys.HOME_SECTION_ORDER]
        return if (raw != cachedHomeSectionOrder.raw) {
            try {
                raw?.let {
                    val parsed = try {
                        json.decodeFromString<List<String>>(it)
                    } catch (_: Exception) {
                        json.decodeFromString<Set<String>>(it).toList()
                    }
                    val mapped = parsed.mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                    buildList {
                        addAll(mapped)
                        addAll(HomeSectionType.CONFIGURABLE.filterNot { it in mapped })
                    }
                } ?: HomeSectionType.CONFIGURABLE
            } catch (_: Exception) { HomeSectionType.CONFIGURABLE }
                .also { cachedHomeSectionOrder = ParsedCache(raw, it) }
        } else cachedHomeSectionOrder.value
    }

    /**
     * Reads the per-library section overrides. Performs a one-shot read-time
     * migration of the legacy all-or-nothing "hide library from home"
     * `home_hidden_library_section_ids` Set<String>: when the new typed key is
     * absent but the legacy key holds ids, each id is migrated to a disabled set
     * of {LATEST_MEDIA, RECENTLY_ADDED}, then the legacy key is dropped.
     */
    private fun readLibraryHomeSectionOverrides(prefs: Preferences): Map<String, Set<HomeSectionType>> {
        val raw = prefs[Keys.HOME_LIBRARY_SECTION_OVERRIDES]
        // The legacy "hide library from home" Set<String> is migrated once at
        // construction (see migrateHiddenLibrarySectionIds); this read just
        // decodes the typed override map.
        return if (raw != cachedLibraryHomeSectionOverrides.raw) {
            try {
                raw?.let {
                    json.decodeFromString<Map<String, Set<HomeSectionType>>>(it)
                } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedLibraryHomeSectionOverrides = ParsedCache(raw, it) }
        } else cachedLibraryHomeSectionOverrides.value
    }

    private fun readPinnedHomeSections(prefs: Preferences): List<PinnedHomeSection> {
        val raw = prefs[Keys.PINNED_HOME_SECTIONS]
        return if (raw != cachedPinnedHomeSections.raw) {
            try {
                raw?.let { json.decodeFromString<List<PinnedHomeSection>>(it) } ?: emptyList()
            } catch (_: Exception) { emptyList() }
                .also { cachedPinnedHomeSections = ParsedCache(raw, it) }
        } else cachedPinnedHomeSections.value
    }

    private fun readHomeLayoutPresets(prefs: Preferences): List<HomeLayoutPreset> {
        val raw = prefs[Keys.HOME_LAYOUT_PRESETS]
        return if (raw != cachedHomeLayoutPresets.raw) {
            try {
                raw?.let { json.decodeFromString<List<HomeLayoutPreset>>(it) } ?: emptyList()
            } catch (_: Exception) { emptyList() }
                .also { cachedHomeLayoutPresets = ParsedCache(raw, it) }
        } else cachedHomeLayoutPresets.value
    }

    private fun readNextUpExcludedSeriesIds(prefs: Preferences): Set<String> {
        val raw = prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS]
        return if (raw != cachedNextUpExcludedSeriesIds.raw) {
            try {
                raw?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedNextUpExcludedSeriesIds = ParsedCache(raw, it) }
        } else cachedNextUpExcludedSeriesIds.value
    }

    private fun readHiddenCwItemIds(prefs: Preferences): Set<String> {
        val raw = prefs[Keys.HIDDEN_CW_ITEM_IDS]
        return if (raw != cachedHiddenCwItemIds.raw) {
            try {
                raw?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedHiddenCwItemIds = ParsedCache(raw, it) }
        } else cachedHiddenCwItemIds.value
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setHomeMode(mode: HomeMode) {
        dataStore.edit { it[Keys.HOME_MODE] = mode.name }
    }

    suspend fun setHomeHeroEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HOME_HERO_ENABLED] = enabled }
    }

    suspend fun setHomeBackdropEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HOME_BACKDROP_ENABLED] = enabled }
    }

    suspend fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) {
        dataStore.edit {
            it[Keys.HOME_ENABLED_SECTION_TYPES] = json.encodeToString(types.map { t -> t.name }.toSet())
        }
    }

    suspend fun setHomeSectionOrder(order: List<HomeSectionType>) {
        dataStore.edit {
            val normalized = buildList {
                addAll(order.filter { it in HomeSectionType.CONFIGURABLE }.distinct())
                addAll(HomeSectionType.CONFIGURABLE.filterNot { it in this })
            }
            it[Keys.HOME_SECTION_ORDER] = json.encodeToString(normalized.map { t -> t.name })
        }
    }

    suspend fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) {
        // Drop entries with empty disabled-sets so the map stays clean and
        // "fully enabled" libraries simply have no key.
        val cleaned = overrides.filterValues { it.isNotEmpty() }
        dataStore.edit {
            it[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(cleaned)
        }
    }

    suspend fun setPinnedHomeSections(sections: List<PinnedHomeSection>) {
        dataStore.edit { prefs ->
            prefs[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(sections)
        }
    }

    suspend fun addPinnedHomeSection(section: PinnedHomeSection) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_HOME_SECTIONS]?.let {
                try { json.decodeFromString<List<PinnedHomeSection>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            if (current.none { it.id == section.id }) {
                prefs[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(current + section)
            }
        }
    }

    suspend fun removePinnedHomeSection(sectionId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_HOME_SECTIONS]?.let {
                try { json.decodeFromString<List<PinnedHomeSection>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            prefs[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(current.filterNot { it.id == sectionId })
        }
    }

    suspend fun setHomeLayoutPresets(presets: List<HomeLayoutPreset>) {
        dataStore.edit { prefs ->
            prefs[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(presets)
        }
    }

    suspend fun saveHomeLayoutPreset(preset: HomeLayoutPreset) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_LAYOUT_PRESETS]?.let {
                try { json.decodeFromString<List<HomeLayoutPreset>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            val next = if (current.any { it.id == preset.id }) {
                current.map { if (it.id == preset.id) preset else it }
            } else {
                current + preset
            }
            prefs[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(next)
        }
    }

    suspend fun deleteHomeLayoutPreset(presetId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_LAYOUT_PRESETS]?.let {
                try { json.decodeFromString<List<HomeLayoutPreset>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            prefs[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(current.filterNot { it.id == presetId })
        }
    }

    suspend fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) {
        dataStore.edit { it[Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR] = behavior.name }
    }

    suspend fun setShowUnwatchedBadge(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_UNWATCHED_BADGE] = enabled }
    }

    suspend fun setHideWatchedItems(enabled: Boolean) {
        dataStore.edit { it[Keys.HIDE_WATCHED_ITEMS] = enabled }
    }

    suspend fun setShowWatchedCheckmark(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_WATCHED_CHECKMARK] = enabled }
    }

    suspend fun setShowExternalRatings(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_EXTERNAL_RATINGS] = enabled }
    }

    suspend fun setMergeContinueWatchingAndNextUp(enabled: Boolean) {
        dataStore.edit { it[Keys.MERGE_CONTINUE_WATCHING_NEXT_UP] = enabled }
    }

    suspend fun setNextUpMaxDays(days: Int) {
        dataStore.edit { it[Keys.NEXT_UP_MAX_DAYS] = days.coerceAtLeast(0) }
    }

    suspend fun setNextUpRewatching(enabled: Boolean) {
        dataStore.edit { it[Keys.NEXT_UP_REWATCHING] = enabled }
    }

    suspend fun setNextUpExcludedSeriesIds(ids: Set<String>) {
        dataStore.edit { it[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(ids) }
    }

    suspend fun excludeSeriesFromNextUp(seriesId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(current + seriesId)
        }
    }

    suspend fun includeSeriesInNextUp(seriesId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(current - seriesId)
        }
    }

    suspend fun setHiddenCwItemIds(ids: Set<String>) {
        dataStore.edit { it[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(ids) }
    }

    suspend fun hideCwItem(itemId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_CW_ITEM_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(current + itemId)
        }
    }

    suspend fun unhideCwItem(itemId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_CW_ITEM_IDS]?.let {
                try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            prefs[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(current - itemId)
        }
    }

    suspend fun unhideAllCwItems() {
        dataStore.edit { it.remove(Keys.HIDDEN_CW_ITEM_IDS) }
    }

    suspend fun setShowClockOnHome(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_CLOCK_ON_HOME] = enabled }
    }

    suspend fun setShowSettingsInHomeSearch(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_SETTINGS_IN_HOME_SEARCH] = enabled }
    }

    /**
     * Keys owned by this store, for factory-reset participation. This is the
     * home/discovery subset of the legacy `HOME_DISCOVERY` reset category — the
     * library-view/sort/filter + nav keys that were bundled in that category now
     * belong to [com.raulshma.jellyplay.core.datastore.library.LibraryStore] and
     * [com.raulshma.jellyplay.core.datastore.navigation.NavigationStore].
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.HOME_MODE, Keys.HOME_HERO_ENABLED, Keys.HOME_BACKDROP_ENABLED,
        Keys.HOME_ENABLED_SECTION_TYPES, Keys.HOME_SECTION_ORDER,
        Keys.HOME_LIBRARY_SECTION_OVERRIDES, Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS,
        Keys.SHOW_UNWATCHED_BADGE, Keys.HIDE_WATCHED_ITEMS,
        Keys.SHOW_WATCHED_CHECKMARK, Keys.SHOW_EXTERNAL_RATINGS,
        Keys.MERGE_CONTINUE_WATCHING_NEXT_UP, Keys.NEXT_UP_MAX_DAYS,
        Keys.NEXT_UP_REWATCHING, Keys.NEXT_UP_EXCLUDED_SERIES_IDS,
        Keys.HIDDEN_CW_ITEM_IDS, Keys.PINNED_HOME_SECTIONS,
        Keys.HOME_LAYOUT_PRESETS, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR,
        Keys.SHOW_CLOCK_ON_HOME, Keys.SHOW_SETTINGS_IN_HOME_SEARCH,
    )

    /**
     * Category reset participation: every key owned here sits in the single
     * legacy `HOME_DISCOVERY` bucket (the home section of the legacy
     * category map; the library/nav keys that shared that category are owned by
     * `LibraryStore` / `NavigationStore`). The legacy `HOME_HIDDEN_LIBRARY_SECTION_IDS`
     * key is included so a reset also drops the one-shot migration source.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.HOME_DISCOVERY -> listOf(
            Keys.HOME_MODE, Keys.HOME_HERO_ENABLED, Keys.HOME_BACKDROP_ENABLED,
            Keys.HOME_ENABLED_SECTION_TYPES, Keys.HOME_SECTION_ORDER,
            Keys.HOME_LIBRARY_SECTION_OVERRIDES, Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS,
            Keys.SHOW_UNWATCHED_BADGE, Keys.HIDE_WATCHED_ITEMS,
            Keys.SHOW_WATCHED_CHECKMARK, Keys.SHOW_EXTERNAL_RATINGS,
            Keys.MERGE_CONTINUE_WATCHING_NEXT_UP, Keys.NEXT_UP_MAX_DAYS,
            Keys.NEXT_UP_REWATCHING, Keys.NEXT_UP_EXCLUDED_SERIES_IDS,
            Keys.HIDDEN_CW_ITEM_IDS, Keys.PINNED_HOME_SECTIONS,
            Keys.HOME_LAYOUT_PRESETS, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR,
            Keys.SHOW_CLOCK_ON_HOME, Keys.SHOW_SETTINGS_IN_HOME_SEARCH,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the home keys owned by this store
     * from a decoded [UserPreferences]. The legacy `home_hidden_library_section_ids`
     * key is not written back — it exists only as a migration source. JSON lists
     * are written in the same shape this store's own setters use (name-string
     * sets / typed lists via [json]).
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.HOME_MODE] = userPreferences.homeMode.name
            it[Keys.HOME_HERO_ENABLED] = userPreferences.homeHeroEnabled
            it[Keys.HOME_BACKDROP_ENABLED] = userPreferences.homeBackdropEnabled
            it[Keys.HOME_ENABLED_SECTION_TYPES] = json.encodeToString(userPreferences.enabledHomeSectionTypes.map { section -> section.name }.toSet())
            it[Keys.HOME_SECTION_ORDER] = json.encodeToString(userPreferences.homeSectionOrder.map { section -> section.name })
            it[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(userPreferences.libraryHomeSectionOverrides)
            it[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(userPreferences.pinnedHomeSections)
            it[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(userPreferences.homeLayoutPresets)
            it[Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR] = userPreferences.continueWatchingClickBehavior.name
            it[Keys.SHOW_UNWATCHED_BADGE] = userPreferences.showUnwatchedBadge
            it[Keys.HIDE_WATCHED_ITEMS] = userPreferences.hideWatchedItems
            it[Keys.SHOW_WATCHED_CHECKMARK] = userPreferences.showWatchedCheckmark
            it[Keys.SHOW_EXTERNAL_RATINGS] = userPreferences.showExternalRatings
            it[Keys.MERGE_CONTINUE_WATCHING_NEXT_UP] = userPreferences.mergeContinueWatchingAndNextUp
            it[Keys.NEXT_UP_MAX_DAYS] = userPreferences.nextUpMaxDays
            it[Keys.NEXT_UP_REWATCHING] = userPreferences.nextUpRewatching
            it[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(userPreferences.nextUpExcludedSeriesIds)
            it[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(userPreferences.hiddenCwItemIds)
            it[Keys.SHOW_CLOCK_ON_HOME] = userPreferences.showClockOnHome
            it[Keys.SHOW_SETTINGS_IN_HOME_SEARCH] = userPreferences.showSettingsInHomeSearch
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (section types
     * and order encoded as name-string sets/lists via [json]).
     */
    suspend fun restore(slice: HomeDiscoverySlice) {
        dataStore.edit { it ->
            it[Keys.HOME_MODE] = slice.homeMode.name
            it[Keys.HOME_HERO_ENABLED] = slice.homeHeroEnabled
            it[Keys.HOME_BACKDROP_ENABLED] = slice.homeBackdropEnabled
            it[Keys.HOME_ENABLED_SECTION_TYPES] = json.encodeToString(slice.enabledHomeSectionTypes.map { section -> section.name }.toSet())
            it[Keys.HOME_SECTION_ORDER] = json.encodeToString(slice.homeSectionOrder.map { section -> section.name })
            it[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(slice.libraryHomeSectionOverrides)
            it[Keys.PINNED_HOME_SECTIONS] = json.encodeToString(slice.pinnedHomeSections)
            it[Keys.HOME_LAYOUT_PRESETS] = json.encodeToString(slice.homeLayoutPresets)
            it[Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR] = slice.continueWatchingClickBehavior.name
            it[Keys.SHOW_UNWATCHED_BADGE] = slice.showUnwatchedBadge
            it[Keys.HIDE_WATCHED_ITEMS] = slice.hideWatchedItems
            it[Keys.SHOW_WATCHED_CHECKMARK] = slice.showWatchedCheckmark
            it[Keys.SHOW_EXTERNAL_RATINGS] = slice.showExternalRatings
            it[Keys.MERGE_CONTINUE_WATCHING_NEXT_UP] = slice.mergeContinueWatchingAndNextUp
            it[Keys.NEXT_UP_MAX_DAYS] = slice.nextUpMaxDays
            it[Keys.NEXT_UP_REWATCHING] = slice.nextUpRewatching
            it[Keys.NEXT_UP_EXCLUDED_SERIES_IDS] = json.encodeToString(slice.nextUpExcludedSeriesIds)
            it[Keys.HIDDEN_CW_ITEM_IDS] = json.encodeToString(slice.hiddenCwItemIds)
            it[Keys.SHOW_CLOCK_ON_HOME] = slice.showClockOnHome
            it[Keys.SHOW_SETTINGS_IN_HOME_SEARCH] = slice.showSettingsInHomeSearch
        }
    }
}

/**
 * The home discovery preference slice. Plain data class. Defaults mirror the
 * projection defaults in [HomeDiscoveryStore.read].
 */
@Immutable
@Serializable
data class HomeDiscoverySlice(
    val homeMode: HomeMode = HomeMode.VIDEO,
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val showUnwatchedBadge: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val showWatchedCheckmark: Boolean = true,
    val showExternalRatings: Boolean = true,
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    val showClockOnHome: Boolean = false,
    val showSettingsInHomeSearch: Boolean = true,
)
