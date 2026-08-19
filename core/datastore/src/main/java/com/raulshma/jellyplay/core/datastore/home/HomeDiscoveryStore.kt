package com.raulshma.jellyplay.core.datastore.home

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
 * search toggles, plus the home top-header hide-on-scroll toggle.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the read-modify-write JSON list/map invariants),
 * its read projection, its legacy migration, and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file, but every key
 * this store owns is namespaced per active user as `u_<userId>::<canonical>`
 * (see [ensureNamespacedMigration] for the one-time upgrade from the legacy
 * flat keys). Home configuration — layout, pins, section order, Next-Up
 * exclusions, hidden CW items — describes one user's home screen, so user B
 * must never inherit user A's values on a shared install. The canonical names
 * (the `Keys` strings) are unchanged and remain the stable backup/export
 * format: backups stay portable across users and devices.
 */
@Singleton
class HomeDiscoveryStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val identityStore: ServerIdentityStore,
) {
    private val scope = externalScope

    init {
        // One-shot legacy migration of the all-or-nothing "hide library from
        // home" Set<String> into the per-library section-override map. Runs once
        // at construction (idempotent — drops the legacy key once migrated) so
        // `read` stays a pure function of the snapshot. It operates on the
        // legacy flat layer; [ensureNamespacedMigration] folds the same
        // conversion in before claiming values, so whichever runs first (or
        // both) leaves a consistent state.
        scope.launch { migrateHiddenLibrarySectionIds() }
        // Per-user-namespace migration (see [ensureNamespacedMigration]): runs
        // the first time a user becomes active. The read projection below may
        // transiently emit defaults for that user before the migration edit
        // commits; the edit's own data emission re-derives the slice with the
        // claimed values, so the state settles without any write.
        scope.launch {
            identityStore.activeUserId.collect { userId ->
                val uid = userId?.takeIf { it.isNotBlank() } ?: return@collect
                ensureNamespacedMigration(uid)
            }
        }
    }

    internal suspend fun migrateHiddenLibrarySectionIds() {
        dataStore.edit { prefs -> migrateHiddenLibrarySectionIds(prefs) }
    }

    /** In-edit variant of [migrateHiddenLibrarySectionIds] — see its call sites. */
    private fun migrateHiddenLibrarySectionIds(prefs: MutablePreferences) {
        val legacyRaw = prefs[Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS] ?: return
        val overridesRaw = prefs[Keys.HOME_LIBRARY_SECTION_OVERRIDES]
        val overridesEmpty = overridesRaw == null
        if (!overridesEmpty) return // already migrated; leave as-is
        try {
            val legacyIds = json.decodeFromString<Set<String>>(legacyRaw)
            if (legacyIds.isEmpty()) return
            val migrated = legacyIds.associateWith {
                setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED)
            }
            prefs[Keys.HOME_LIBRARY_SECTION_OVERRIDES] = json.encodeToString(migrated)
        } catch (_: Exception) { return }
        prefs.remove(Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS)
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
        /**
         * Per-series last-viewed season tab (seriesId → seasonId). Lets the
         * series detail screen reopen on the season the user was browsing
         * instead of always the smart-play default. Stored as a JSON map.
         */
        val LAST_VIEWED_SEASON_BY_SERIES = stringPreferencesKey("last_viewed_season_by_series")
        val SHOW_CLOCK_ON_HOME = booleanPreferencesKey("show_clock_on_home")
        val SHOW_SETTINGS_IN_HOME_SEARCH = booleanPreferencesKey("show_settings_in_home_search")
        val HIDE_TOP_HEADER_ON_SCROLL = booleanPreferencesKey("hide_top_header_on_scroll")
        /**
         * Global one-time marker for the legacy flat-key → per-user-namespace
         * migration ([ensureNamespacedMigration]). Deliberately **global**, not
         * per-user: the legacy flat values belonged to the one user who used
         * the install, so they are claimed exactly once — a per-user marker
         * would let every later user inherit them.
         */
        val HOME_NS_MIGRATED = booleanPreferencesKey("home_ns_migrated")
    }

    // ------------------------------------------------------------------
    // Per-user key namespacing: u_<userId>::<canonical>
    // ------------------------------------------------------------------

    /** Namespaced key name for [userId]: `u_<userId>::<canonical>`. */
    private fun namespaced(userId: String, canonical: String): String = "u_$userId::$canonical"

    private fun userStringKey(userId: String, canonical: Preferences.Key<String>): Preferences.Key<String> =
        stringPreferencesKey(namespaced(userId, canonical.name))

    private fun userBooleanKey(userId: String, canonical: Preferences.Key<Boolean>): Preferences.Key<Boolean> =
        booleanPreferencesKey(namespaced(userId, canonical.name))

    private fun userIntKey(userId: String, canonical: Preferences.Key<Int>): Preferences.Key<Int> =
        intPreferencesKey(namespaced(userId, canonical.name))

    // Canonical keys by declared type. The migration copies each list with its
    // typed reader so a legacy STRING slot (pre-typed-migration install — the
    // typed-key migration is an unordered concurrent init launch, so this edit
    // may win the race) is parsed into the TYPED namespaced slot instead of
    // being skipped; readers disable the string fallback once the global
    // typed-migration flag is set, so a raw string copy would read as default.
    private val booleanLegacyKeys: List<Preferences.Key<Boolean>> = listOf(
        Keys.HOME_HERO_ENABLED, Keys.HOME_BACKDROP_ENABLED, Keys.SHOW_UNWATCHED_BADGE,
        Keys.HIDE_WATCHED_ITEMS, Keys.SHOW_WATCHED_CHECKMARK, Keys.SHOW_EXTERNAL_RATINGS,
        Keys.MERGE_CONTINUE_WATCHING_NEXT_UP, Keys.NEXT_UP_REWATCHING,
        Keys.SHOW_CLOCK_ON_HOME, Keys.SHOW_SETTINGS_IN_HOME_SEARCH, Keys.HIDE_TOP_HEADER_ON_SCROLL,
    )

    private val intLegacyKeys: List<Preferences.Key<Int>> = listOf(Keys.NEXT_UP_MAX_DAYS)

    private val stringLegacyKeys: List<Preferences.Key<String>> = listOf(
        Keys.HOME_MODE, Keys.HOME_ENABLED_SECTION_TYPES, Keys.HOME_SECTION_ORDER,
        Keys.HOME_LIBRARY_SECTION_OVERRIDES, Keys.PINNED_HOME_SECTIONS,
        Keys.HOME_LAYOUT_PRESETS, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR,
        Keys.NEXT_UP_EXCLUDED_SERIES_IDS, Keys.HIDDEN_CW_ITEM_IDS,
        Keys.LAST_VIEWED_SEASON_BY_SERIES,
    )

    /**
     * Every canonical key this store owns, in its legacy flat form. Doubles as
     * (a) the canonical-suffix set for the migration copy lists above and (b)
     * the legacy/canonical layer that factory reset must also strip. Includes
     * the legacy [Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS] migration source.
     */
    internal val legacyKeys: List<Preferences.Key<*>> =
        booleanLegacyKeys + intLegacyKeys + stringLegacyKeys + Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS

    /**
     * ONE-TIME, GLOBAL, FIRST-USER-CLAIMS migration from the legacy flat keys
     * to the active user's `u_<userId>::` namespace.
     *
     * Legacy (pre-namespacing) versions wrote home configuration into flat
     * keys shared by every account on the install — the values on disk
     * therefore belonged to the one user who had been using it. On the first
     * user activation after upgrade (home config is unreachable before
     * sign-in, so no read or write can precede it), every present legacy key is copied into the
     * CURRENTLY active user's namespace (never overwriting a namespaced value
     * that is already there), and the global [Keys.HOME_NS_MIGRATED] marker is
     * set — so the upgrading user keeps their configuration untouched, while
     * every later user starts clean instead of inheriting it. That is the
     * cross-user config leak this closes; a per-user marker would defeat it by
     * letting each user claim the same legacy values.
     *
     * Idempotent and crash-safe: the fast path skips when the marker is set,
     * the copies and the marker (set last) commit in one atomic DataStore edit,
     * and copies only fill absent namespaced keys — a crash at any point
     * re-runs to the same result.
     */
    internal suspend fun ensureNamespacedMigration(userId: String) {
        // A DataStore read/edit failure (IO error, corruption) must not escape
        // into the init collector's ApplicationScope coroutine — that scope has
        // no CoroutineExceptionHandler, so a throw would crash the app and kill
        // the migration collector for good. Swallow and degrade instead: the
        // marker is only set on success, so the next user activation retries.
        // (Silent-swallow is the module convention — see
        // ArrSecureCredentialsStore.getManualServers.)
        runCatching {
            if (dataStore.data.first()[Keys.HOME_NS_MIGRATED] == true) return
            dataStore.edit { prefs ->
                if (prefs[Keys.HOME_NS_MIGRATED] == true) return@edit
                // Fold the legacy hidden-library conversion in first so its
                // output (the flat overrides map) is claimed by this same pass
                // — ordering against the construction-time run then doesn't
                // matter (both are idempotent). The hidden-library key itself
                // is a migration source only, never a copy destination.
                migrateHiddenLibrarySectionIds(prefs)
                booleanLegacyKeys.forEach {
                    copyIntoNamespace(prefs, it, userId, ::booleanPreferencesKey) { raw -> raw.toBoolean() }
                }
                intLegacyKeys.forEach {
                    copyIntoNamespace(prefs, it, userId, ::intPreferencesKey) { raw -> raw.toIntOrNull() }
                }
                stringLegacyKeys.forEach {
                    copyIntoNamespace(prefs, it, userId, ::stringPreferencesKey) { raw -> raw }
                }
                prefs[Keys.HOME_NS_MIGRATED] = true
            }
        }
    }

    /**
     * Copies one legacy flat key into `u_<userId>::` — only if the
     * namespaced slot is absent. Mirrors [PreferenceCodec.readBool]'s
     * dual-read: typed slot first, then the legacy STRING form parsed via
     * [fromString], so an install whose typed-key migration had not run yet
     * still keeps its value instead of silently resetting to the default.
     * A null parse (or absent value) leaves the namespaced slot absent.
     * String (JSON blob) keys were always strings — [fromString] is the
     * identity there, so a plain copy suffices.
     *
     * `reified` + the `as T?` cast are load-bearing: [T] erases, so
     * `prefs[canonical]` alone inserts no runtime check and a legacy STRING
     * value stored under the same name would flow through as "typed" and be
     * written into the typed namespaced slot. The reified cast restores the
     * ClassCastException the per-type reads relied on.
     */
    private inline fun <reified T : Any> copyIntoNamespace(
        prefs: MutablePreferences,
        canonical: Preferences.Key<T>,
        userId: String,
        keyFactory: (String) -> Preferences.Key<T>,
        fromString: (String) -> T?,
    ) {
        val target = namespaced(userId, canonical.name)
        if (prefs.asMap().keys.any { it.name == target }) return
        val typed = try { prefs[canonical] as T? } catch (_: ClassCastException) { null }
        val value = typed ?: prefs[stringPreferencesKey(canonical.name)]?.let(fromString) ?: return
        prefs[keyFactory(target)] = value
    }

    // ------------------------------------------------------------------
    // Read projection
    // ------------------------------------------------------------------

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val homeDiscovery: StateFlow<HomeDiscoverySlice> = combine(
        identityStore.activeUserId,
        sharedPrefs,
    ) { userId, prefs ->
        val uid = userId?.takeIf { it.isNotBlank() }
        // Pre-login: no namespace exists yet — serve the default slice and
        // read nothing. (Migration runs from the init collector above, not
        // here, so the read projection stays side-effect free.)
        if (uid == null) HomeDiscoverySlice() else readerFor(uid).slice(prefs)
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, HomeDiscoverySlice())

    /**
     * Parse-cache-aware reader for the given user — reused across emissions
     * for the same user (the JSON parse caches are the point), recreated on
     * user switch so one user can never be served another user's cached
     * parse. [UserReader] instances are immutable once past construction, so
     * publishing through this volatile pair is safe.
     */
    @Volatile private var readerByUser: Pair<String, UserReader>? = null

    private fun readerFor(uid: String): UserReader {
        readerByUser?.let { (id, reader) -> if (id == uid) return reader }
        return UserReader(uid).also { readerByUser = uid to it }
    }

    /** Snapshot projection for [prefs], resolved against its embedded active user. */
    internal fun read(prefs: Preferences): HomeDiscoverySlice {
        val uid = identityStore.activeUserIdIn(prefs) ?: return HomeDiscoverySlice()
        return readerFor(uid).slice(prefs)
    }

    /**
     * Per-user read projection: the namespaced key set plus the JSON parse
     * caches for exactly one user. A fresh instance is created for every
     * active-user emission in [homeDiscovery], so a user switch can never serve
     * another user's cached parse — stale cross-user parse-cache hits are
     * precisely the leak class the namespacing closes.
     */
    private inner class UserReader(private val userId: String) {

        // Namespaced keys — same canonical names as the legacy flat keys, so
        // the backup/export format is unchanged.
        private val homeModeKey = userStringKey(userId, Keys.HOME_MODE)
        private val homeHeroEnabledKey = userBooleanKey(userId, Keys.HOME_HERO_ENABLED)
        private val homeBackdropEnabledKey = userBooleanKey(userId, Keys.HOME_BACKDROP_ENABLED)
        private val enabledSectionTypesKey = userStringKey(userId, Keys.HOME_ENABLED_SECTION_TYPES)
        private val sectionOrderKey = userStringKey(userId, Keys.HOME_SECTION_ORDER)
        private val librarySectionOverridesKey = userStringKey(userId, Keys.HOME_LIBRARY_SECTION_OVERRIDES)
        private val pinnedSectionsKey = userStringKey(userId, Keys.PINNED_HOME_SECTIONS)
        private val layoutPresetsKey = userStringKey(userId, Keys.HOME_LAYOUT_PRESETS)
        private val continueWatchingClickBehaviorKey = userStringKey(userId, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR)
        private val showUnwatchedBadgeKey = userBooleanKey(userId, Keys.SHOW_UNWATCHED_BADGE)
        private val hideWatchedItemsKey = userBooleanKey(userId, Keys.HIDE_WATCHED_ITEMS)
        private val showWatchedCheckmarkKey = userBooleanKey(userId, Keys.SHOW_WATCHED_CHECKMARK)
        private val showExternalRatingsKey = userBooleanKey(userId, Keys.SHOW_EXTERNAL_RATINGS)
        private val mergeCwNextUpKey = userBooleanKey(userId, Keys.MERGE_CONTINUE_WATCHING_NEXT_UP)
        private val nextUpMaxDaysKey = userIntKey(userId, Keys.NEXT_UP_MAX_DAYS)
        private val nextUpRewatchingKey = userBooleanKey(userId, Keys.NEXT_UP_REWATCHING)
        private val nextUpExcludedSeriesIdsKey = userStringKey(userId, Keys.NEXT_UP_EXCLUDED_SERIES_IDS)
        private val hiddenCwItemIdsKey = userStringKey(userId, Keys.HIDDEN_CW_ITEM_IDS)
        private val lastViewedSeasonBySeriesKey = userStringKey(userId, Keys.LAST_VIEWED_SEASON_BY_SERIES)
        private val showClockOnHomeKey = userBooleanKey(userId, Keys.SHOW_CLOCK_ON_HOME)
        private val showSettingsInHomeSearchKey = userBooleanKey(userId, Keys.SHOW_SETTINGS_IN_HOME_SEARCH)
        private val hideTopHeaderOnScrollKey = userBooleanKey(userId, Keys.HIDE_TOP_HEADER_ON_SCROLL)

        // Parse caches — per-user by construction: one UserReader per user.
        private var cachedEnabledHomeSectionTypes = ParsedCache<Set<HomeSectionType>>(null, HomeSectionType.CONFIGURABLE.toSet())
        private var cachedHomeSectionOrder = ParsedCache<List<HomeSectionType>>(null, HomeSectionType.CONFIGURABLE)
        private var cachedLibraryHomeSectionOverrides = ParsedCache<Map<String, Set<HomeSectionType>>>(null, emptyMap())
        private var cachedPinnedHomeSections = ParsedCache<List<PinnedHomeSection>>(null, emptyList())
        private var cachedHomeLayoutPresets = ParsedCache<List<HomeLayoutPreset>>(null, emptyList())
        private var cachedNextUpExcludedSeriesIds = ParsedCache<Set<String>>(null, emptySet())
        private var cachedHiddenCwItemIds = ParsedCache<Set<String>>(null, emptySet())
        private var cachedLastViewedSeasonBySeries = ParsedCache<Map<String, String>>(null, emptyMap())

        /** The [key.name] pass-through keeps PreferenceCodec's legacy string fallback pointed at the namespaced slot. */
        private fun readBool(prefs: Preferences, key: Preferences.Key<Boolean>, default: Boolean): Boolean =
            PreferenceCodec.readBool(prefs, key, key.name, default)

        private fun readInt(prefs: Preferences, key: Preferences.Key<Int>, default: Int): Int =
            PreferenceCodec.readInt(prefs, key, key.name, default)

        fun slice(prefs: Preferences): HomeDiscoverySlice = HomeDiscoverySlice(
            homeMode = readHomeMode(prefs),
            homeHeroEnabled = readBool(prefs, homeHeroEnabledKey, true),
            homeBackdropEnabled = readBool(prefs, homeBackdropEnabledKey, true),
            enabledHomeSectionTypes = readEnabledHomeSectionTypes(prefs),
            homeSectionOrder = readHomeSectionOrder(prefs),
            libraryHomeSectionOverrides = readLibraryHomeSectionOverrides(prefs),
            pinnedHomeSections = readPinnedHomeSections(prefs),
            homeLayoutPresets = readHomeLayoutPresets(prefs),
            continueWatchingClickBehavior = readContinueWatchingClickBehavior(prefs),
            showUnwatchedBadge = readBool(prefs, showUnwatchedBadgeKey, true),
            hideWatchedItems = readBool(prefs, hideWatchedItemsKey, false),
            showWatchedCheckmark = readBool(prefs, showWatchedCheckmarkKey, true),
            showExternalRatings = readBool(prefs, showExternalRatingsKey, true),
            mergeContinueWatchingAndNextUp = readBool(prefs, mergeCwNextUpKey, false),
            nextUpMaxDays = readInt(prefs, nextUpMaxDaysKey, 0),
            nextUpRewatching = readBool(prefs, nextUpRewatchingKey, false),
            nextUpExcludedSeriesIds = readNextUpExcludedSeriesIds(prefs),
            hiddenCwItemIds = readHiddenCwItemIds(prefs),
            lastViewedSeasonBySeries = readLastViewedSeasonBySeries(prefs),
            showClockOnHome = readBool(prefs, showClockOnHomeKey, false),
            showSettingsInHomeSearch = readBool(prefs, showSettingsInHomeSearchKey, true),
            hideTopHeaderOnScroll = readBool(prefs, hideTopHeaderOnScrollKey, false),
        )

        private fun readHomeMode(prefs: Preferences): HomeMode = try {
            HomeMode.valueOf(prefs[homeModeKey] ?: HomeMode.VIDEO.name)
        } catch (_: Exception) { HomeMode.VIDEO }

        private fun readContinueWatchingClickBehavior(prefs: Preferences): ContinueWatchingClickBehavior = try {
            ContinueWatchingClickBehavior.valueOf(prefs[continueWatchingClickBehaviorKey] ?: ContinueWatchingClickBehavior.DETAILS.name)
        } catch (_: Exception) { ContinueWatchingClickBehavior.DETAILS }

        private fun readEnabledHomeSectionTypes(prefs: Preferences): Set<HomeSectionType> = cachedJson(
            raw = prefs[enabledSectionTypesKey],
            cache = cachedEnabledHomeSectionTypes,
            default = HomeSectionType.CONFIGURABLE.toSet(),
            parse = { raw ->
                json.decodeFromString<Set<String>>(raw)
                    .mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                    .toSet()
            },
            cacheRef = { cachedEnabledHomeSectionTypes = it },
        )

        private fun readHomeSectionOrder(prefs: Preferences): List<HomeSectionType> = cachedJson(
            raw = prefs[sectionOrderKey],
            cache = cachedHomeSectionOrder,
            default = HomeSectionType.CONFIGURABLE,
            parse = { raw ->
                // Dual-format: the list is canonical, but the legacy format was
                // a Set — decode either.
                val parsed = try {
                    json.decodeFromString<List<String>>(raw)
                } catch (_: Exception) {
                    json.decodeFromString<Set<String>>(raw).toList()
                }
                val mapped = parsed.mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                buildList {
                    addAll(mapped)
                    addAll(HomeSectionType.CONFIGURABLE.filterNot { it in mapped })
                }
            },
            cacheRef = { cachedHomeSectionOrder = it },
        )

        /**
         * Reads the per-library section overrides from the active user's
         * namespace. The legacy all-or-nothing "hide library from home"
         * `Set<String>` conversion happens in the flat layer at construction
         * and inside [ensureNamespacedMigration]; this read just decodes the
         * typed override map.
         */
        private fun readLibraryHomeSectionOverrides(prefs: Preferences): Map<String, Set<HomeSectionType>> = cachedJson(
            raw = prefs[librarySectionOverridesKey],
            cache = cachedLibraryHomeSectionOverrides,
            default = emptyMap(),
            parse = { json.decodeFromString<Map<String, Set<HomeSectionType>>>(it) },
            cacheRef = { cachedLibraryHomeSectionOverrides = it },
        )

        private fun readPinnedHomeSections(prefs: Preferences): List<PinnedHomeSection> = cachedJson(
            raw = prefs[pinnedSectionsKey],
            cache = cachedPinnedHomeSections,
            default = emptyList(),
            parse = { json.decodeFromString<List<PinnedHomeSection>>(it) },
            cacheRef = { cachedPinnedHomeSections = it },
        )

        private fun readHomeLayoutPresets(prefs: Preferences): List<HomeLayoutPreset> = cachedJson(
            raw = prefs[layoutPresetsKey],
            cache = cachedHomeLayoutPresets,
            default = emptyList(),
            parse = { json.decodeFromString<List<HomeLayoutPreset>>(it) },
            cacheRef = { cachedHomeLayoutPresets = it },
        )

        private fun readNextUpExcludedSeriesIds(prefs: Preferences): Set<String> = cachedJson(
            raw = prefs[nextUpExcludedSeriesIdsKey],
            cache = cachedNextUpExcludedSeriesIds,
            default = emptySet(),
            parse = { json.decodeFromString<Set<String>>(it) },
            cacheRef = { cachedNextUpExcludedSeriesIds = it },
        )

        private fun readHiddenCwItemIds(prefs: Preferences): Set<String> = cachedJson(
            raw = prefs[hiddenCwItemIdsKey],
            cache = cachedHiddenCwItemIds,
            default = emptySet(),
            parse = { json.decodeFromString<Set<String>>(it) },
            cacheRef = { cachedHiddenCwItemIds = it },
        )

        private fun readLastViewedSeasonBySeries(prefs: Preferences): Map<String, String> = cachedJson(
            raw = prefs[lastViewedSeasonBySeriesKey],
            cache = cachedLastViewedSeasonBySeries,
            default = emptyMap(),
            parse = { json.decodeFromString<Map<String, String>>(it) },
            cacheRef = { cachedLastViewedSeasonBySeries = it },
        )
    }

    /**
     * Shared cached JSON decode for the simple map/set readers. Returns the
     * cached value when [raw] is unchanged; otherwise decodes via [parse]
     * (falling back to [default] on null or decode failure), publishes the new
     * [ParsedCache] through [cacheRef], and returns the value. Collapses the
     * per-key "compare-raw → try/decode → update-cache" boilerplate that the
     * JSON list/map readers would otherwise each repeat verbatim.
     */
    private fun <T : Any> cachedJson(
        raw: String?,
        cache: ParsedCache<T>,
        default: T,
        parse: (String) -> T,
        cacheRef: (ParsedCache<T>) -> Unit,
    ): T {
        if (raw == cache.raw) return cache.value
        val value = try { raw?.let(parse) ?: default } catch (_: Exception) { default }
        cacheRef(ParsedCache(raw, value))
        return value
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    /**
     * Runs [block] against the currently active user's namespace inside a
     * single DataStore edit. The user is resolved from the very snapshot being
     * edited (via [ServerIdentityStore.activeUserIdIn]) so the write is atomic
     * with respect to concurrent user switches. Pre-login (no active user) the
     * write is skipped: home configuration is unreachable before sign-in, so
     * there is no namespace to write into.
     */
    private suspend fun editForUser(block: (MutablePreferences, String) -> Unit) {
        dataStore.edit { prefs ->
            val userId = identityStore.activeUserIdIn(prefs) ?: return@edit
            block(prefs, userId)
        }
    }

    suspend fun setHomeMode(mode: HomeMode) = editForUser { prefs, userId ->
        prefs[userStringKey(userId, Keys.HOME_MODE)] = mode.name
    }

    suspend fun setHomeHeroEnabled(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.HOME_HERO_ENABLED)] = enabled
    }

    suspend fun setHomeBackdropEnabled(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.HOME_BACKDROP_ENABLED)] = enabled
    }

    suspend fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = editForUser { prefs, userId ->
        prefs[userStringKey(userId, Keys.HOME_ENABLED_SECTION_TYPES)] =
            json.encodeToString(types.map { t -> t.name }.toSet())
    }

    suspend fun setHomeSectionOrder(order: List<HomeSectionType>) = editForUser { prefs, userId ->
        val normalized = buildList {
            addAll(order.filter { it in HomeSectionType.CONFIGURABLE }.distinct())
            addAll(HomeSectionType.CONFIGURABLE.filterNot { it in this })
        }
        prefs[userStringKey(userId, Keys.HOME_SECTION_ORDER)] = json.encodeToString(normalized.map { t -> t.name })
    }

    suspend fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) = editForUser { prefs, userId ->
        // Drop entries with empty disabled-sets so the map stays clean and
        // "fully enabled" libraries simply have no key.
        val cleaned = overrides.filterValues { it.isNotEmpty() }
        prefs[userStringKey(userId, Keys.HOME_LIBRARY_SECTION_OVERRIDES)] = json.encodeToString(cleaned)
    }

    suspend fun setPinnedHomeSections(sections: List<PinnedHomeSection>) = editForUser { prefs, userId ->
        prefs[userStringKey(userId, Keys.PINNED_HOME_SECTIONS)] = json.encodeToString(sections)
    }

    suspend fun addPinnedHomeSection(section: PinnedHomeSection) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.PINNED_HOME_SECTIONS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<List<PinnedHomeSection>>(it) } catch (_: Exception) { emptyList() }
        } ?: emptyList()
        if (current.none { it.id == section.id }) {
            prefs[key] = json.encodeToString(current + section)
        }
    }

    suspend fun removePinnedHomeSection(sectionId: String) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.PINNED_HOME_SECTIONS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<List<PinnedHomeSection>>(it) } catch (_: Exception) { emptyList() }
        } ?: emptyList()
        prefs[key] = json.encodeToString(current.filterNot { it.id == sectionId })
    }

    suspend fun setHomeLayoutPresets(presets: List<HomeLayoutPreset>) = editForUser { prefs, userId ->
        prefs[userStringKey(userId, Keys.HOME_LAYOUT_PRESETS)] = json.encodeToString(presets)
    }

    suspend fun saveHomeLayoutPreset(preset: HomeLayoutPreset) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.HOME_LAYOUT_PRESETS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<List<HomeLayoutPreset>>(it) } catch (_: Exception) { emptyList() }
        } ?: emptyList()
        val next = if (current.any { it.id == preset.id }) {
            current.map { if (it.id == preset.id) preset else it }
        } else {
            current + preset
        }
        prefs[key] = json.encodeToString(next)
    }

    suspend fun deleteHomeLayoutPreset(presetId: String) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.HOME_LAYOUT_PRESETS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<List<HomeLayoutPreset>>(it) } catch (_: Exception) { emptyList() }
        } ?: emptyList()
        prefs[key] = json.encodeToString(current.filterNot { it.id == presetId })
    }

    suspend fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) = editForUser { prefs, userId ->
        prefs[userStringKey(userId, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR)] = behavior.name
    }

    suspend fun setShowUnwatchedBadge(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.SHOW_UNWATCHED_BADGE)] = enabled
    }

    suspend fun setHideWatchedItems(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.HIDE_WATCHED_ITEMS)] = enabled
    }

    suspend fun setShowWatchedCheckmark(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.SHOW_WATCHED_CHECKMARK)] = enabled
    }

    suspend fun setShowExternalRatings(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.SHOW_EXTERNAL_RATINGS)] = enabled
    }

    suspend fun setMergeContinueWatchingAndNextUp(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.MERGE_CONTINUE_WATCHING_NEXT_UP)] = enabled
    }

    suspend fun setNextUpMaxDays(days: Int) = editForUser { prefs, userId ->
        prefs[userIntKey(userId, Keys.NEXT_UP_MAX_DAYS)] = days.coerceAtLeast(0)
    }

    suspend fun setNextUpRewatching(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.NEXT_UP_REWATCHING)] = enabled
    }

    suspend fun setNextUpExcludedSeriesIds(ids: Set<String>) = editForUser { prefs, userId ->
        prefs[userStringKey(userId, Keys.NEXT_UP_EXCLUDED_SERIES_IDS)] = json.encodeToString(ids)
    }

    suspend fun excludeSeriesFromNextUp(seriesId: String) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.NEXT_UP_EXCLUDED_SERIES_IDS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
        } ?: emptySet()
        prefs[key] = json.encodeToString(current + seriesId)
    }

    suspend fun includeSeriesInNextUp(seriesId: String) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.NEXT_UP_EXCLUDED_SERIES_IDS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
        } ?: emptySet()
        prefs[key] = json.encodeToString(current - seriesId)
    }

    /**
     * Pins the last-viewed season for [seriesId] so the series detail screen
     * reopens on that season tab. Read-modify-write: copies the existing
     * series→season map and upserts the entry (overwrites if already present).
     */
    suspend fun setLastViewedSeason(seriesId: String, seasonId: String) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.LAST_VIEWED_SEASON_BY_SERIES)
        val current = prefs[key]?.let {
            try { json.decodeFromString<Map<String, String>>(it) } catch (_: Exception) { emptyMap() }
        } ?: emptyMap()
        prefs[key] = json.encodeToString(current + (seriesId to seasonId))
    }

    suspend fun setHiddenCwItemIds(ids: Set<String>) = editForUser { prefs, userId ->
        prefs[userStringKey(userId, Keys.HIDDEN_CW_ITEM_IDS)] = json.encodeToString(ids)
    }

    suspend fun hideCwItem(itemId: String) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.HIDDEN_CW_ITEM_IDS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
        } ?: emptySet()
        prefs[key] = json.encodeToString(current + itemId)
    }

    suspend fun unhideCwItem(itemId: String) = editForUser { prefs, userId ->
        val key = userStringKey(userId, Keys.HIDDEN_CW_ITEM_IDS)
        val current = prefs[key]?.let {
            try { json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
        } ?: emptySet()
        prefs[key] = json.encodeToString(current - itemId)
    }

    suspend fun unhideAllCwItems() = editForUser { prefs, userId ->
        prefs.remove(userStringKey(userId, Keys.HIDDEN_CW_ITEM_IDS))
    }

    suspend fun setShowClockOnHome(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.SHOW_CLOCK_ON_HOME)] = enabled
    }

    suspend fun setShowSettingsInHomeSearch(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.SHOW_SETTINGS_IN_HOME_SEARCH)] = enabled
    }

    suspend fun setHideTopHeaderOnScroll(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[userBooleanKey(userId, Keys.HIDE_TOP_HEADER_ON_SCROLL)] = enabled
    }

    /**
     * Keys owned by this store, for factory-reset participation. This is the
     * home/discovery subset of the legacy `HOME_DISCOVERY` reset category — the
     * library-view/sort/filter + nav keys that were bundled in that category now
     * belong to [com.raulshma.jellyplay.core.datastore.library.LibraryStore] and
     * [com.raulshma.jellyplay.core.datastore.navigation.NavigationStore].
     *
     * The static list covers the legacy/canonical flat keys plus the global
     * migration marker; the per-user namespaced keys are dynamic and are
     * stripped by [removeDynamicResetKeys] inside the reset edit.
     */
    internal val resetKeys: List<Preferences.Key<*>> = legacyKeys + Keys.HOME_NS_MIGRATED

    /**
     * Category reset participation: every key owned here sits in the single
     * legacy `HOME_DISCOVERY` bucket (the home section of the legacy category
     * map; the library/nav keys that shared that category are owned by
     * `LibraryStore` / `NavigationStore`). Delegates to [resetKeys] so the owned
     * key list lives in exactly one place — the legacy
     * `HOME_HIDDEN_LIBRARY_SECTION_IDS` migration source is included via it.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.HOME_DISCOVERY -> resetKeys
        else -> emptyList()
    }

    /**
     * Factory-reset participation for this store's **dynamic** keys. The static
     * [resetKeysFor] list cannot express `u_<userId>::<canonical>` entries (one
     * set per user that has ever signed in), so the reset machinery calls this
     * inside its edit: it strips every namespaced home key — for ANY user —
     * plus the global migration marker, alongside the static legacy/canonical
     * keys removed via [resetKeysFor]. Canonical-suffix matching keeps this
     * precise: an unrelated key that merely starts with `u_` is never touched.
     */
    internal fun removeDynamicResetKeys(category: PreferenceResetCategory, prefs: MutablePreferences) {
        if (category != PreferenceResetCategory.HOME_DISCOVERY) return
        val canonicalNames = legacyKeys.mapTo(mutableSetOf()) { it.name }
        prefs.asMap().keys
            .filter { key -> key.name.isNamespacedHomeKey(canonicalNames) }
            .forEach { prefs.remove(it) }
        prefs.remove(Keys.HOME_NS_MIGRATED)
    }

    /**
     * `u_<userId>::<canonical>` with a canonical suffix this store owns. Splits
     * on the LAST `::`: no canonical name contains `::`, but a user id might
     * (`setActiveUser` input is unvalidated) — splitting on the first separator
     * would mis-parse `u_a::b::home_mode` as canonical `b::home_mode` and never
     * strip that user's keys on factory reset. lastIndexOf matches the
     * construction side for every id, `::`-containing or not.
     */
    private fun String.isNamespacedHomeKey(canonicalNames: Set<String>): Boolean {
        if (!startsWith("u_")) return false
        val separator = lastIndexOf("::")
        if (separator <= 2) return false // "u_" alone is not a user id
        return substring(separator + 2) in canonicalNames
    }

    /**
     * Restore-backup participation: writes the home keys owned by this store
     * from a decoded [UserPreferences] into the CURRENT active user's
     * namespace — backups carry canonical (user-portable) values and are
     * applied on behalf of whoever restores them. The legacy
     * `home_hidden_library_section_ids` key is not written back — it exists
     * only as a migration source. JSON lists are written in the same shape
     * this store's own setters use (name-string sets / typed lists via [json]).
     * Pre-login the restore is skipped along with every other write
     * ([editForUser]).
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        editForUser { it, userId ->
            it[userStringKey(userId, Keys.HOME_MODE)] = userPreferences.homeMode.name
            it[userBooleanKey(userId, Keys.HOME_HERO_ENABLED)] = userPreferences.homeHeroEnabled
            it[userBooleanKey(userId, Keys.HOME_BACKDROP_ENABLED)] = userPreferences.homeBackdropEnabled
            it[userStringKey(userId, Keys.HOME_ENABLED_SECTION_TYPES)] = json.encodeToString(userPreferences.enabledHomeSectionTypes.map { section -> section.name }.toSet())
            it[userStringKey(userId, Keys.HOME_SECTION_ORDER)] = json.encodeToString(userPreferences.homeSectionOrder.map { section -> section.name })
            it[userStringKey(userId, Keys.HOME_LIBRARY_SECTION_OVERRIDES)] = json.encodeToString(userPreferences.libraryHomeSectionOverrides)
            it[userStringKey(userId, Keys.PINNED_HOME_SECTIONS)] = json.encodeToString(userPreferences.pinnedHomeSections)
            it[userStringKey(userId, Keys.HOME_LAYOUT_PRESETS)] = json.encodeToString(userPreferences.homeLayoutPresets)
            it[userStringKey(userId, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR)] = userPreferences.continueWatchingClickBehavior.name
            it[userBooleanKey(userId, Keys.SHOW_UNWATCHED_BADGE)] = userPreferences.showUnwatchedBadge
            it[userBooleanKey(userId, Keys.HIDE_WATCHED_ITEMS)] = userPreferences.hideWatchedItems
            it[userBooleanKey(userId, Keys.SHOW_WATCHED_CHECKMARK)] = userPreferences.showWatchedCheckmark
            it[userBooleanKey(userId, Keys.SHOW_EXTERNAL_RATINGS)] = userPreferences.showExternalRatings
            it[userBooleanKey(userId, Keys.MERGE_CONTINUE_WATCHING_NEXT_UP)] = userPreferences.mergeContinueWatchingAndNextUp
            it[userIntKey(userId, Keys.NEXT_UP_MAX_DAYS)] = userPreferences.nextUpMaxDays
            it[userBooleanKey(userId, Keys.NEXT_UP_REWATCHING)] = userPreferences.nextUpRewatching
            it[userStringKey(userId, Keys.NEXT_UP_EXCLUDED_SERIES_IDS)] = json.encodeToString(userPreferences.nextUpExcludedSeriesIds)
            it[userStringKey(userId, Keys.HIDDEN_CW_ITEM_IDS)] = json.encodeToString(userPreferences.hiddenCwItemIds)
            it[userBooleanKey(userId, Keys.SHOW_CLOCK_ON_HOME)] = userPreferences.showClockOnHome
            it[userBooleanKey(userId, Keys.SHOW_SETTINGS_IN_HOME_SEARCH)] = userPreferences.showSettingsInHomeSearch
            it[userBooleanKey(userId, Keys.HIDE_TOP_HEADER_ON_SCROLL)] = userPreferences.hideTopHeaderOnScroll
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore — into the CURRENT active user's namespace — using the same
     * encoding as [restorePreferences] (section types and order encoded as
     * name-string sets/lists via [json]).
     */
    suspend fun restore(slice: HomeDiscoverySlice) {
        editForUser { it, userId ->
            it[userStringKey(userId, Keys.HOME_MODE)] = slice.homeMode.name
            it[userBooleanKey(userId, Keys.HOME_HERO_ENABLED)] = slice.homeHeroEnabled
            it[userBooleanKey(userId, Keys.HOME_BACKDROP_ENABLED)] = slice.homeBackdropEnabled
            it[userStringKey(userId, Keys.HOME_ENABLED_SECTION_TYPES)] = json.encodeToString(slice.enabledHomeSectionTypes.map { section -> section.name }.toSet())
            it[userStringKey(userId, Keys.HOME_SECTION_ORDER)] = json.encodeToString(slice.homeSectionOrder.map { section -> section.name })
            it[userStringKey(userId, Keys.HOME_LIBRARY_SECTION_OVERRIDES)] = json.encodeToString(slice.libraryHomeSectionOverrides)
            it[userStringKey(userId, Keys.PINNED_HOME_SECTIONS)] = json.encodeToString(slice.pinnedHomeSections)
            it[userStringKey(userId, Keys.HOME_LAYOUT_PRESETS)] = json.encodeToString(slice.homeLayoutPresets)
            it[userStringKey(userId, Keys.CONTINUE_WATCHING_CLICK_BEHAVIOR)] = slice.continueWatchingClickBehavior.name
            it[userBooleanKey(userId, Keys.SHOW_UNWATCHED_BADGE)] = slice.showUnwatchedBadge
            it[userBooleanKey(userId, Keys.HIDE_WATCHED_ITEMS)] = slice.hideWatchedItems
            it[userBooleanKey(userId, Keys.SHOW_WATCHED_CHECKMARK)] = slice.showWatchedCheckmark
            it[userBooleanKey(userId, Keys.SHOW_EXTERNAL_RATINGS)] = slice.showExternalRatings
            it[userBooleanKey(userId, Keys.MERGE_CONTINUE_WATCHING_NEXT_UP)] = slice.mergeContinueWatchingAndNextUp
            it[userIntKey(userId, Keys.NEXT_UP_MAX_DAYS)] = slice.nextUpMaxDays
            it[userBooleanKey(userId, Keys.NEXT_UP_REWATCHING)] = slice.nextUpRewatching
            it[userStringKey(userId, Keys.NEXT_UP_EXCLUDED_SERIES_IDS)] = json.encodeToString(slice.nextUpExcludedSeriesIds)
            it[userStringKey(userId, Keys.HIDDEN_CW_ITEM_IDS)] = json.encodeToString(slice.hiddenCwItemIds)
            it[userBooleanKey(userId, Keys.SHOW_CLOCK_ON_HOME)] = slice.showClockOnHome
            it[userBooleanKey(userId, Keys.SHOW_SETTINGS_IN_HOME_SEARCH)] = slice.showSettingsInHomeSearch
            it[userBooleanKey(userId, Keys.HIDE_TOP_HEADER_ON_SCROLL)] = slice.hideTopHeaderOnScroll
            it[userStringKey(userId, Keys.LAST_VIEWED_SEASON_BY_SERIES)] = json.encodeToString(slice.lastViewedSeasonBySeries)
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
    /**
     * Per-series last-viewed season tab (seriesId → seasonId). Empty until the
     * user selects a season tab on a series detail screen; projected into
     * [com.raulshma.jellyplay.core.model.DetailPreferences] so the screen can
     * reopen on the browsed season. An active resume still takes precedence
     * (resolved in `SeasonStartResolver`).
     */
    val lastViewedSeasonBySeries: Map<String, String> = emptyMap(),
    val showClockOnHome: Boolean = false,
    val showSettingsInHomeSearch: Boolean = true,
    /**
     * Whether the home screen's top header dock auto-hides on scroll-down and
     * reappears on scroll-up. Default `false` — the dock stays pinned (current
     * behaviour) until the user opts in. Mirrors the floating nav-bar
     * `hideBottomNavOnScroll` toggle.
     */
    val hideTopHeaderOnScroll: Boolean = false,
)
