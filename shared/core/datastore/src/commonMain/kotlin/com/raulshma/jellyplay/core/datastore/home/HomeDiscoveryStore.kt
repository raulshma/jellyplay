@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.raulshma.jellyplay.core.datastore.home

import kotlin.concurrent.atomics.AtomicReference
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.dataDegradingToDefaults
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.withDiscoverRowEnabled
import com.raulshma.jellyplay.core.model.withDiscoverRowMoved
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

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
 * **Spec derivation**: every key this store owns is declared exactly once as a row in
 * [HomeDiscoveryPreferenceSpecs] (wire name, default, reset category, encoding
 * where the row carries one) and the machinery below is derived from those
 * rows — each [Keys] member rebuilds its row's typed key from the row's wire
 * name, the per-user read projection delegates each plain knob to its row
 * encoding through the spec-side per-user hook, the setters and [restore]
 * write through the rows' namespace-rebuilt keys, [resetKeysFor] filters the
 * rows by reset category, and the namespace migration's per-type copy lists
 * are the rows filtered by declared storage. The read-modify-write commands,
 * the enabled-set version-union decode (whose per-user parse cache a row
 * encoding cannot carry) and the first-user-claims migration stay hand-written
 * by decision — see the spec KDoc.
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
class HomeDiscoveryStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
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

    /**
     * Decode-or-default read for the read-modify-write setters: a missing key
     * OR an undecodable value (shape drift after a model change) yields
     * [fallback], so a corrupt entry can't wedge the write path — the same
     * tolerance the read projection's [PreferenceCodec.cachedJson] applies,
     * in-edition.
     */
    private inline fun <reified T> decodeOrDefault(
        prefs: Preferences,
        key: Preferences.Key<String>,
        fallback: T,
    ): T = prefs[key]?.let {
        try {
            json.decodeFromString<T>(it)
        } catch (_: Exception) {
            fallback
        }
    } ?: fallback

    /**
     * The store's DataStore keys, each derived from its
     * [HomeDiscoveryPreferenceSpecs] row — the member rebuilds the row's
     * typed key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key
     * of the same name). Kept as a plain object rather than folded into the
     * rows because it is the reflection anchor for the JVM reset-coverage
     * guard and the key-identity reference for the hand-written migration
     * logic below.
     */
    internal object Keys {
        val HOME_MODE = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_MODE.keyName)
        val HOME_HERO_ENABLED = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.keyName)
        val HOME_BACKDROP_ENABLED = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_BACKDROP_ENABLED.keyName)
        val HOME_ENABLED_SECTION_TYPES = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.keyName)
        val HOME_ENABLED_SECTION_TYPES_VERSION = intPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_VERSION.keyName)
        val HOME_SECTION_ORDER = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.keyName)
        val HOME_LIBRARY_SECTION_OVERRIDES = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.keyName)
        /** Legacy all-or-nothing "hide library from home" key — kept only to migrate. */
        val HOME_HIDDEN_LIBRARY_SECTION_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_HIDDEN_LIBRARY_SECTION_IDS.keyName)
        val PINNED_HOME_SECTIONS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.keyName)
        val HOME_DISCOVER_ROWS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.keyName)
        val HOME_LAYOUT_PRESETS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.keyName)
        val CONTINUE_WATCHING_CLICK_BEHAVIOR = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.CONTINUE_WATCHING_CLICK_BEHAVIOR.keyName)
        val SHOW_UNWATCHED_BADGE = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_UNWATCHED_BADGE.keyName)
        val HIDE_WATCHED_ITEMS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HIDE_WATCHED_ITEMS.keyName)
        val SHOW_WATCHED_CHECKMARK = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_WATCHED_CHECKMARK.keyName)
        val SHOW_EXTERNAL_RATINGS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_EXTERNAL_RATINGS.keyName)
        val MERGE_CONTINUE_WATCHING_NEXT_UP = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.MERGE_CONTINUE_WATCHING_NEXT_UP.keyName)
        val NEXT_UP_MAX_DAYS = intPreferencesKey(HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.keyName)
        val NEXT_UP_REWATCHING = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.NEXT_UP_REWATCHING.keyName)
        val CLASSIC_ROWS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.CLASSIC_ROWS.keyName)
        val NEXT_UP_EXCLUDED_SERIES_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.keyName)
        val HIDDEN_CW_ITEM_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.keyName)
        val LAST_VIEWED_SEASON_BY_SERIES = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.keyName)
        val SHOW_CLOCK_ON_HOME = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME.keyName)
        val SHOW_SETTINGS_IN_HOME_SEARCH = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_SETTINGS_IN_HOME_SEARCH.keyName)
        val HIDE_TOP_HEADER_ON_SCROLL = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HIDE_TOP_HEADER_ON_SCROLL.keyName)
        val HOME_NS_MIGRATED = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_NS_MIGRATED.keyName)
    }

    // ------------------------------------------------------------------
    // Per-user key namespacing: u_<userId>::<canonical>. The grammar itself
    // (name build + recognition) is [UserNamespacedKeys]' — the one shared
    // implementation (DownloadsStore's allow-list key namespaces through it
    // too). The per-row spellings live on the rows as the spec-side
    // key-rebuild hook ([HomeDiscoveryPreferenceSpecs.userKey] and friends),
    // so a namespaced slot is always the canonical row transformed by the
    // grammar — never a second key literal.
    // ------------------------------------------------------------------

    /**
     * Every canonical key this store owns, in its legacy flat form — derived
     * from the [HomeDiscoveryPreferenceSpecs] rows (every row except the
     * global `home_ns_migrated` marker). Doubles as (a) the canonical-suffix
     * set for the migration copy lists below and (b) the legacy/canonical
     * layer that factory reset must also strip. Includes the legacy
     * [Keys.HOME_HIDDEN_LIBRARY_SECTION_IDS] migration source.
     */
    internal val legacyKeys: List<Preferences.Key<*>> = HomeDiscoveryPreferenceSpecs.legacyKeys

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
                // The per-type copy lists are the spec rows filtered by their
                // declared storage — a row's parse (toBoolean / toIntOrNull /
                // verbatim) follows its slot type.
                HomeDiscoveryPreferenceSpecs.booleanCopyRows.forEach {
                    copyIntoNamespace(prefs, it.typedKey(), userId, ::booleanPreferencesKey) { raw -> raw.toBoolean() }
                }
                HomeDiscoveryPreferenceSpecs.intCopyRows.forEach {
                    copyIntoNamespace(prefs, it.typedKey(), userId, ::intPreferencesKey) { raw -> raw.toIntOrNull() }
                }
                HomeDiscoveryPreferenceSpecs.stringCopyRows.forEach {
                    copyIntoNamespace(prefs, it.typedKey(), userId, ::stringPreferencesKey) { raw -> raw }
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
        val target = UserNamespacedKeys.name(userId, canonical.name)
        if (prefs.asMap().keys.any { it.name == target }) return
        val typed = try { prefs[canonical] as T? } catch (_: ClassCastException) { null }
        val value = typed ?: prefs[stringPreferencesKey(canonical.name)]?.let(fromString) ?: return
        prefs[keyFactory(target)] = value
    }

    // ------------------------------------------------------------------
    // Read projection
    // ------------------------------------------------------------------

    private val sharedPrefs: Flow<Preferences> = dataStore.dataDegradingToDefaults()

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
     * publishing through this atomic reference is safe. The CAS in
     * [readerFor] makes the check-then-set atomic: concurrent collectors for
     * the same user converge on ONE reader instead of racing to publish
     * lookalikes (a lost race merely re-parsed — never a cross-user read).
     */
    private val readerByUser = AtomicReference<Pair<String, UserReader>?>(null)

    private fun readerFor(uid: String): UserReader {
        while (true) {
            val current = readerByUser.load()
            current?.let { (id, reader) -> if (id == uid) return reader }
            val next = UserReader(uid)
            if (readerByUser.compareAndSet(current, uid to next)) return next
        }
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
     *
     * Every namespaced key is rebuilt from its
     * [HomeDiscoveryPreferenceSpecs] row through the per-user hook
     * ([HomeDiscoveryPreferenceSpecs.userKey] / `rawUserKey`); the plain
     * knobs' reads delegate to the rows' encodings via the per-user read
     * helpers, and only the JSON blobs keep store-supplied codecs (their
     * per-user parse caches live here).
     */
    private inner class UserReader(private val userId: String) {

        // Parse caches — per-user by construction: one UserReader per user.
        private var cachedEnabledHomeSectionTypes =
            ParsedCache<Set<HomeSectionType>>(null, HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.default)
        private var cachedHomeSectionOrder =
            ParsedCache<List<HomeSectionType>>(null, HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.default)
        private var cachedLibraryHomeSectionOverrides =
            ParsedCache<Map<String, Set<HomeSectionType>>>(null, HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.default)
        private var cachedPinnedHomeSections =
            ParsedCache<List<PinnedHomeSection>>(null, HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.default)
        private var cachedDiscoverRows =
            ParsedCache<List<DiscoverRowConfig>>(null, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        private var cachedHomeLayoutPresets =
            ParsedCache<List<HomeLayoutPreset>>(null, HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.default)
        private var cachedNextUpExcludedSeriesIds =
            ParsedCache<Set<String>>(null, HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.default)
        private var cachedHiddenCwItemIds =
            ParsedCache<Set<String>>(null, HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.default)
        private var cachedLastViewedSeasonBySeries =
            ParsedCache<Map<String, String>>(null, HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.default)

        fun slice(prefs: Preferences): HomeDiscoverySlice = HomeDiscoverySlice(
            homeMode = HomeDiscoveryPreferenceSpecs.HOME_MODE.readEnumForUser(prefs, userId),
            homeHeroEnabled = HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.readBoolForUser(prefs, userId),
            homeBackdropEnabled = HomeDiscoveryPreferenceSpecs.HOME_BACKDROP_ENABLED.readBoolForUser(prefs, userId),
            enabledHomeSectionTypes = readEnabledHomeSectionTypes(prefs),
            homeSectionOrder = readHomeSectionOrder(prefs),
            libraryHomeSectionOverrides = readLibraryHomeSectionOverrides(prefs),
            pinnedHomeSections = readPinnedHomeSections(prefs),
            discoverRows = readDiscoverRows(prefs),
            homeLayoutPresets = readHomeLayoutPresets(prefs),
            continueWatchingClickBehavior = HomeDiscoveryPreferenceSpecs.CONTINUE_WATCHING_CLICK_BEHAVIOR
                .readEnumForUser(prefs, userId),
            showUnwatchedBadge = HomeDiscoveryPreferenceSpecs.SHOW_UNWATCHED_BADGE.readBoolForUser(prefs, userId),
            hideWatchedItems = HomeDiscoveryPreferenceSpecs.HIDE_WATCHED_ITEMS.readBoolForUser(prefs, userId),
            showWatchedCheckmark = HomeDiscoveryPreferenceSpecs.SHOW_WATCHED_CHECKMARK.readBoolForUser(prefs, userId),
            showExternalRatings = HomeDiscoveryPreferenceSpecs.SHOW_EXTERNAL_RATINGS.readBoolForUser(prefs, userId),
            mergeContinueWatchingAndNextUp = HomeDiscoveryPreferenceSpecs.MERGE_CONTINUE_WATCHING_NEXT_UP
                .readBoolForUser(prefs, userId),
            nextUpMaxDays = HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.readIntForUser(prefs, userId),
            nextUpRewatching = HomeDiscoveryPreferenceSpecs.NEXT_UP_REWATCHING.readBoolForUser(prefs, userId),
            classicRows = HomeDiscoveryPreferenceSpecs.CLASSIC_ROWS.readBoolForUser(prefs, userId),
            nextUpExcludedSeriesIds = readNextUpExcludedSeriesIds(prefs),
            hiddenCwItemIds = readHiddenCwItemIds(prefs),
            lastViewedSeasonBySeries = readLastViewedSeasonBySeries(prefs),
            showClockOnHome = HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME.readBoolForUser(prefs, userId),
            showSettingsInHomeSearch = HomeDiscoveryPreferenceSpecs.SHOW_SETTINGS_IN_HOME_SEARCH
                .readBoolForUser(prefs, userId),
            hideTopHeaderOnScroll = HomeDiscoveryPreferenceSpecs.HIDE_TOP_HEADER_ON_SCROLL.readBoolForUser(prefs, userId),
        )

        /**
         * Sections the versioned set shipped after versioning began, each
         * keyed by the schema version it shipped at — the union source
         * [readEnabledHomeSectionTypes] folds in for every set persisted
         * before that version. A future section appends its
         * `(shipped-at, section)` entry here and bumps
         * [HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION];
         * existing entries are never edited.
         */
        private val sectionsShippedByVersion: List<Pair<Int, HomeSectionType>> = listOf(
            // v1: reading-experience 2.0's Continue Reading row
            HomeDiscoveryPreferenceSpecs.CONTINUE_READING_SHIPPED_AT_VERSION to HomeSectionType.CONTINUE_READING,
            // v2: the custom discover rows block (Home Screen settings hub)
            HomeDiscoveryPreferenceSpecs.DISCOVER_SHIPPED_AT_VERSION to HomeSectionType.DISCOVER,
        )

        /**
         * The persisted set is decoded verbatim, then unioned with every
         * section shipped AFTER the version stamp the set was written at
         * ([HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_VERSION],
         * absent = predates the versioning itself): a set persisted before a section existed cannot
         * contain it, and reading it verbatim would keep the new section
         * invisible to exactly the users who ever touched section config. The
         * one-shot shape matters — every write of the set stamps the current
         * version, so absence from a CURRENT set is the user's disabled
         * choice, not a not-yet-shipped section, and stays respected.
         */
        private fun readEnabledHomeSectionTypes(prefs: Preferences): Set<HomeSectionType> {
            val raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.rawUserKey(userId)]
            // The union folds in the version stamp, so the parse cache keys on
            // BOTH: a pre-version read caches raw → raw∪{CONTINUE_READING},
            // and the CR disable that follows rewrites the SAME encoded set
            // (the write's read-modify-write drops exactly the unioned
            // member) under the new stamp. Keyed on raw alone, that write
            // would serve the stale unioned value until process restart.
            val version = prefs[HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_VERSION.userKey(userId)] ?: 0
            return PreferenceCodec.cachedJson(
                raw = raw,
                cacheKey = "v$version:$raw",
                cache = cachedEnabledHomeSectionTypes,
                default = HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.default,
                parse = { encoded ->
                    val persisted = json.decodeFromString<Set<String>>(encoded)
                        .mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                        .toSet()
                    persisted + sectionsShippedByVersion
                        .filter { (shippedAt, _) -> version < shippedAt }
                        .map { (_, section) -> section }
                },
                cacheRef = { cachedEnabledHomeSectionTypes = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )
        }

        private fun readHomeSectionOrder(prefs: Preferences): List<HomeSectionType> = PreferenceCodec.cachedJson(
            raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.rawUserKey(userId)],
            cache = cachedHomeSectionOrder,
            default = HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.default,
            parse = { raw ->
                // Dual-format: the list is canonical, but the legacy format was
                // a Set — decode either.
                val parsed = try {
                    json.decodeFromString<List<String>>(raw)
                } catch (_: Exception) {
                    json.decodeFromString<Set<String>>(raw).toList()
                }
                insertMissingAtDefaultPositions(
                    parsed.mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } },
                )
            },
            cacheRef = { cachedHomeSectionOrder = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

        /**
         * Inserts the configurable sections absent from a persisted order at
         * their DEFAULT-ORDER position instead of the tail: a newly shipped
         * section (Continue Reading at slot 2) joins its default
         * neighbourhood for every user whose persisted order predates it —
         * the order twin of the enabled-set version union, no stamp needed
         * because absence itself is the signal. The persisted relative order
         * is never disturbed: each missing section slots after the LAST
         * present section whose default index precedes it (non-configurable
         * strays do not constrain), or leads the list when none does.
         */
        private fun insertMissingAtDefaultPositions(persisted: List<HomeSectionType>): List<HomeSectionType> {
            if (persisted.isEmpty()) return HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.default
            val defaultIndex = HomeSectionType.CONFIGURABLE.withIndex().associate { (index, type) -> type to index }
            var result = persisted
            for (section in HomeSectionType.CONFIGURABLE.filterNot { it in persisted }) {
                val sectionDefaultIndex = defaultIndex.getValue(section)
                val insertAt = 1 + result.indexOfLast { present ->
                    defaultIndex[present]?.let { it < sectionDefaultIndex } == true
                }
                result = result.subList(0, insertAt) + section + result.subList(insertAt, result.size)
            }
            return result
        }

        /**
         * Reads the per-library section overrides from the active user's
         * namespace. The legacy all-or-nothing "hide library from home"
         * `Set<String>` conversion happens in the flat layer at construction
         * and inside [ensureNamespacedMigration]; this read just decodes the
         * typed override map.
         */
        private fun readLibraryHomeSectionOverrides(prefs: Preferences): Map<String, Set<HomeSectionType>> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.rawUserKey(userId)],
                cache = cachedLibraryHomeSectionOverrides,
                default = HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.default,
                parse = { json.decodeFromString<Map<String, Set<HomeSectionType>>>(it) },
                cacheRef = { cachedLibraryHomeSectionOverrides = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        private fun readPinnedHomeSections(prefs: Preferences): List<PinnedHomeSection> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)],
                cache = cachedPinnedHomeSections,
                default = HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.default,
                parse = { json.decodeFromString<List<PinnedHomeSection>>(it) },
                cacheRef = { cachedPinnedHomeSections = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        private fun readDiscoverRows(prefs: Preferences): List<DiscoverRowConfig> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)],
                cache = cachedDiscoverRows,
                default = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default,
                parse = { json.decodeFromString<List<DiscoverRowConfig>>(it) },
                cacheRef = { cachedDiscoverRows = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        private fun readHomeLayoutPresets(prefs: Preferences): List<HomeLayoutPreset> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.rawUserKey(userId)],
                cache = cachedHomeLayoutPresets,
                default = HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.default,
                parse = { json.decodeFromString<List<HomeLayoutPreset>>(it) },
                cacheRef = { cachedHomeLayoutPresets = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        private fun readNextUpExcludedSeriesIds(prefs: Preferences): Set<String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId)],
                cache = cachedNextUpExcludedSeriesIds,
                default = HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.default,
                parse = { json.decodeFromString<Set<String>>(it) },
                cacheRef = { cachedNextUpExcludedSeriesIds = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        private fun readHiddenCwItemIds(prefs: Preferences): Set<String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.rawUserKey(userId)],
                cache = cachedHiddenCwItemIds,
                default = HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.default,
                parse = { json.decodeFromString<Set<String>>(it) },
                cacheRef = { cachedHiddenCwItemIds = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        private fun readLastViewedSeasonBySeries(prefs: Preferences): Map<String, String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.rawUserKey(userId)],
                cache = cachedLastViewedSeasonBySeries,
                default = HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.default,
                parse = { json.decodeFromString<Map<String, String>>(it) },
                cacheRef = { cachedLastViewedSeasonBySeries = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )
    }

    // ------------------------------------------------------------------
    // Setters — every write goes through the row's namespace-rebuilt key
    // (the spec-side per-user hook), so the wire name stays declared once
    // on the row. The read-modify-write commands and the enabled-set's
    // version-stamp fold stay hand-written below.
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
        prefs[HomeDiscoveryPreferenceSpecs.HOME_MODE.rawUserKey(userId)] = mode.name
    }

    suspend fun setHomeHeroEnabled(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.userKey(userId)] = enabled
    }

    suspend fun setHomeBackdropEnabled(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_BACKDROP_ENABLED.userKey(userId)] = enabled
    }

    /**
     * The ONE in-edit write of the persisted enabled-section set: encodes
     * [types] and stamps the version row with the current version in the same
     * breath. The stamp is what makes the read side's version-union one-shot,
     * so no write path may encode the set without it — this fold exists so a
     * future writer cannot forget.
     */
    private fun writeEnabledHomeSectionTypes(prefs: MutablePreferences, userId: String, types: Set<HomeSectionType>) {
        prefs[HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.rawUserKey(userId)] =
            json.encodeToString(types.map { t -> t.name }.toSet())
        prefs[HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_VERSION.userKey(userId)] =
            HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION
    }

    suspend fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = editForUser { prefs, userId ->
        writeEnabledHomeSectionTypes(prefs, userId, types)
    }

    suspend fun setHomeSectionOrder(order: List<HomeSectionType>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.rawUserKey(userId)] =
            json.encodeToString(normalizedSectionOrder(order).map { t -> t.name })
    }

    /**
     * Keeps the persisted section order a permutation of the configurable
     * set: drops unknown types, dedupes, then appends any configurables the
     * list was missing (a newly-shipped section slots in at the end instead
     * of disappearing from the reorder UI).
     */
    private fun normalizedSectionOrder(order: List<HomeSectionType>): List<HomeSectionType> = buildList {
        addAll(order.filter { it in HomeSectionType.CONFIGURABLE }.distinct())
        addAll(HomeSectionType.CONFIGURABLE.filterNot { it in this })
    }

    suspend fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) = editForUser { prefs, userId ->
        // Drop entries with empty disabled-sets so the map stays clean and
        // "fully enabled" libraries simply have no key.
        val cleaned = overrides.filterValues { it.isNotEmpty() }
        prefs[HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.rawUserKey(userId)] = json.encodeToString(cleaned)
    }

    /**
     * The section-prefs WRITE COMMANDS — the sanctioned read-modify-write
     * path for section visibility, order and per-library overrides, applying
     * the shared [HomeSectionPrefs] algebra to the CURRENT persisted state.
     * Every feature (home's inline section-config sheet, Settings → Configure
     * Libraries, the Appearance toggle) routes through these; holding a local
     * toggle/override policy copy is how the two features drifted before.
     */
    suspend fun setSectionVisible(type: HomeSectionType, visible: Boolean) = editForUser { prefs, userId ->
        val updated = read(prefs).toSectionPrefs().withSectionVisible(type, visible)
        writeEnabledHomeSectionTypes(prefs, userId, updated.query.enabledSections)
    }

    suspend fun moveSection(type: HomeSectionType, up: Boolean) = editForUser { prefs, userId ->
        val updated = read(prefs).toSectionPrefs().withSectionMoved(type, up) ?: return@editForUser
        prefs[HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.rawUserKey(userId)] =
            json.encodeToString(normalizedSectionOrder(updated.homeSectionOrder).map { t -> t.name })
    }

    suspend fun setLibrarySectionVisible(
        libraryId: String,
        type: HomeSectionType,
        visible: Boolean,
    ) = editForUser { prefs, userId ->
        val updated = read(prefs).toSectionPrefs()
            .withLibrarySectionVisible(libraryId, type, visible)
        // Same empty-set drop as [setLibraryHomeSectionOverrides]: a library
        // whose disabled-set empties again simply loses its key.
        prefs[HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.rawUserKey(userId)] =
            json.encodeToString(updated.query.libraryHomeSectionOverrides.filterValues { it.isNotEmpty() })
    }

    suspend fun setPinnedHomeSections(sections: List<PinnedHomeSection>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)] = json.encodeToString(sections)
    }

    suspend fun addPinnedHomeSection(section: PinnedHomeSection) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.default)
        if (current.none { it.id == section.id }) {
            prefs[key] = json.encodeToString(current + section)
        }
    }

    suspend fun removePinnedHomeSection(sectionId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.default)
        prefs[key] = json.encodeToString(current.filterNot { it.id == sectionId })
    }

    // ── Custom Discover rows ────────────────────────────────────────────────

    /** Wholesale replace (layout presets, reset). */
    suspend fun setDiscoverRows(rows: List<DiscoverRowConfig>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)] = json.encodeToString(rows)
    }

    /** Add-or-replace by id — the editor's save. */
    suspend fun upsertDiscoverRow(row: DiscoverRowConfig) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        val next = if (current.any { it.id == row.id }) {
            current.map { if (it.id == row.id) row else it }
        } else {
            current + row
        }
        prefs[key] = json.encodeToString(next)
    }

    suspend fun removeDiscoverRow(rowId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        prefs[key] = json.encodeToString(current.filterNot { it.id == rowId })
    }

    suspend fun setDiscoverRowEnabled(rowId: String, enabled: Boolean) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        prefs[key] = json.encodeToString(current.withDiscoverRowEnabled(rowId, enabled))
    }

    suspend fun moveDiscoverRow(rowId: String, up: Boolean) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        val moved = current.withDiscoverRowMoved(rowId, up) ?: return@editForUser
        prefs[key] = json.encodeToString(moved)
    }

    suspend fun setHomeLayoutPresets(presets: List<HomeLayoutPreset>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.rawUserKey(userId)] = json.encodeToString(presets)
    }

    suspend fun saveHomeLayoutPreset(preset: HomeLayoutPreset) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.default)
        val next = if (current.any { it.id == preset.id }) {
            current.map { if (it.id == preset.id) preset else it }
        } else {
            current + preset
        }
        prefs[key] = json.encodeToString(next)
    }

    suspend fun deleteHomeLayoutPreset(presetId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.default)
        prefs[key] = json.encodeToString(current.filterNot { it.id == presetId })
    }

    suspend fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.CONTINUE_WATCHING_CLICK_BEHAVIOR.rawUserKey(userId)] = behavior.name
    }

    suspend fun setShowUnwatchedBadge(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.SHOW_UNWATCHED_BADGE.userKey(userId)] = enabled
    }

    suspend fun setHideWatchedItems(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HIDE_WATCHED_ITEMS.userKey(userId)] = enabled
    }

    suspend fun setShowWatchedCheckmark(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.SHOW_WATCHED_CHECKMARK.userKey(userId)] = enabled
    }

    suspend fun setShowExternalRatings(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.SHOW_EXTERNAL_RATINGS.userKey(userId)] = enabled
    }

    suspend fun setMergeContinueWatchingAndNextUp(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.MERGE_CONTINUE_WATCHING_NEXT_UP.userKey(userId)] = enabled
    }

    suspend fun setNextUpMaxDays(days: Int) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.userKey(userId)] = days.coerceAtLeast(0)
    }

    suspend fun setNextUpRewatching(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_REWATCHING.userKey(userId)] = enabled
    }

    suspend fun setClassicRows(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.CLASSIC_ROWS.userKey(userId)] = enabled
    }

    suspend fun setNextUpExcludedSeriesIds(ids: Set<String>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId)] = json.encodeToString(ids)
    }

    suspend fun excludeSeriesFromNextUp(seriesId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.default)
        prefs[key] = json.encodeToString(current + seriesId)
    }

    suspend fun includeSeriesInNextUp(seriesId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.default)
        prefs[key] = json.encodeToString(current - seriesId)
    }

    /** Bulk restore — every Next Up exclusion dropped at once (the "Restore all" action). */
    suspend fun clearNextUpExclusions() = editForUser { prefs, userId ->
        prefs.remove(HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId))
    }

    /**
     * Pins the last-viewed season for [seriesId] so the series detail screen
     * reopens on that season tab. Read-modify-write: copies the existing
     * series→season map and upserts the entry (overwrites if already present).
     */
    suspend fun setLastViewedSeason(seriesId: String, seasonId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.default)
        prefs[key] = json.encodeToString(current + (seriesId to seasonId))
    }

    suspend fun setHiddenCwItemIds(ids: Set<String>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.rawUserKey(userId)] = json.encodeToString(ids)
    }

    suspend fun hideCwItem(itemId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.default)
        prefs[key] = json.encodeToString(current + itemId)
    }

    suspend fun unhideCwItem(itemId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.default)
        prefs[key] = json.encodeToString(current - itemId)
    }

    suspend fun unhideAllCwItems() = editForUser { prefs, userId ->
        prefs.remove(HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.rawUserKey(userId))
    }

    suspend fun setShowClockOnHome(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME.userKey(userId)] = enabled
    }

    suspend fun setShowSettingsInHomeSearch(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.SHOW_SETTINGS_IN_HOME_SEARCH.userKey(userId)] = enabled
    }

    suspend fun setHideTopHeaderOnScroll(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HIDE_TOP_HEADER_ON_SCROLL.userKey(userId)] = enabled
    }

    /**
     * Keys owned by this store, for factory-reset participation. This is the
     * home/discovery subset of the legacy `HOME_DISCOVERY` reset category — the
     * library-view/sort/filter + nav keys that were bundled in that category now
     * belong to [com.raulshma.jellyplay.core.datastore.library.LibraryStore] and
     * [com.raulshma.jellyplay.core.datastore.navigation.NavigationStore].
     *
     * Derived from the [HomeDiscoveryPreferenceSpecs] rows (the union of the
     * [resetKeysFor] category lists). The static list covers the legacy/
     * canonical flat keys plus the global migration marker; the per-user
     * namespaced keys are dynamic and are stripped by [removeDynamicResetKeys]
     * inside the reset edit.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [HomeDiscoveryPreferenceSpecs] rows whose declared
     * reset category matches, mapped to their derived keys. Every key owned
     * here sits in the single legacy `HOME_DISCOVERY` bucket (the home
     * section of the legacy category map; the library/nav keys that shared
     * that category are owned by `LibraryStore` / `NavigationStore`). The
     * `HOME_HIDDEN_LIBRARY_SECTION_IDS` migration source is included via its
     * row.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        HomeDiscoveryPreferenceSpecs.resetKeysFor(category)

    /**
     * Factory-reset participation for this store's **dynamic** keys. The static
     * [resetKeysFor] list cannot express `u_<userId>::<canonical>` entries (one
     * set per user that has ever signed in), so the reset machinery calls this
     * inside its edit: it strips every namespaced home key — for ANY user —
     * plus the global migration marker, alongside the static legacy/canonical
     * keys removed via [resetKeysFor]. Canonical-suffix matching
     * ([UserNamespacedKeys.isNamespaced]) keeps this precise: an unrelated key
     * that merely starts with `u_` is never touched.
     */
    internal fun removeDynamicResetKeys(category: PreferenceResetCategory, prefs: MutablePreferences) {
        if (category != PreferenceResetCategory.HOME_DISCOVERY) return
        val canonicalNames = legacyKeys.mapTo(mutableSetOf()) { it.name }
        prefs.asMap().keys
            .filter { key -> UserNamespacedKeys.isNamespaced(key.name, canonicalNames) }
            .forEach { prefs.remove(it) }
        prefs.remove(Keys.HOME_NS_MIGRATED)
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore — into the CURRENT active user's namespace — through the
     * rows' namespace-rebuilt keys, using the same encoding as
     * `restorePreferences` (section types and order encoded as name-string
     * sets/lists via [json]).
     */
    suspend fun restore(slice: HomeDiscoverySlice) {
        editForUser { prefs, userId ->
            prefs[HomeDiscoveryPreferenceSpecs.HOME_MODE.rawUserKey(userId)] = slice.homeMode.name
            prefs[HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.userKey(userId)] = slice.homeHeroEnabled
            prefs[HomeDiscoveryPreferenceSpecs.HOME_BACKDROP_ENABLED.userKey(userId)] = slice.homeBackdropEnabled
            writeEnabledHomeSectionTypes(prefs, userId, slice.enabledHomeSectionTypes)
            prefs[HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.rawUserKey(userId)] =
                json.encodeToString(slice.homeSectionOrder.map { section -> section.name })
            prefs[HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.rawUserKey(userId)] =
                json.encodeToString(slice.libraryHomeSectionOverrides)
            prefs[HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)] =
                json.encodeToString(slice.pinnedHomeSections)
            prefs[HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)] =
                json.encodeToString(slice.discoverRows)
            prefs[HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.rawUserKey(userId)] =
                json.encodeToString(slice.homeLayoutPresets)
            prefs[HomeDiscoveryPreferenceSpecs.CONTINUE_WATCHING_CLICK_BEHAVIOR.rawUserKey(userId)] =
                slice.continueWatchingClickBehavior.name
            prefs[HomeDiscoveryPreferenceSpecs.SHOW_UNWATCHED_BADGE.userKey(userId)] = slice.showUnwatchedBadge
            prefs[HomeDiscoveryPreferenceSpecs.HIDE_WATCHED_ITEMS.userKey(userId)] = slice.hideWatchedItems
            prefs[HomeDiscoveryPreferenceSpecs.SHOW_WATCHED_CHECKMARK.userKey(userId)] = slice.showWatchedCheckmark
            prefs[HomeDiscoveryPreferenceSpecs.SHOW_EXTERNAL_RATINGS.userKey(userId)] = slice.showExternalRatings
            prefs[HomeDiscoveryPreferenceSpecs.MERGE_CONTINUE_WATCHING_NEXT_UP.userKey(userId)] =
                slice.mergeContinueWatchingAndNextUp
            prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.userKey(userId)] = slice.nextUpMaxDays
            prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_REWATCHING.userKey(userId)] = slice.nextUpRewatching
            prefs[HomeDiscoveryPreferenceSpecs.CLASSIC_ROWS.userKey(userId)] = slice.classicRows
            prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId)] =
                json.encodeToString(slice.nextUpExcludedSeriesIds)
            prefs[HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.rawUserKey(userId)] =
                json.encodeToString(slice.hiddenCwItemIds)
            prefs[HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME.userKey(userId)] = slice.showClockOnHome
            prefs[HomeDiscoveryPreferenceSpecs.SHOW_SETTINGS_IN_HOME_SEARCH.userKey(userId)] = slice.showSettingsInHomeSearch
            prefs[HomeDiscoveryPreferenceSpecs.HIDE_TOP_HEADER_ON_SCROLL.userKey(userId)] = slice.hideTopHeaderOnScroll
            prefs[HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.rawUserKey(userId)] =
                json.encodeToString(slice.lastViewedSeasonBySeries)
        }
    }
}

/**
 * The home discovery preference slice. Plain data class. Defaults mirror the
 * projection defaults in [HomeDiscoveryStore.read] (declared on the
 * [HomeDiscoveryPreferenceSpecs] rows).
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
    /** The user's custom Discover rows; empty until the user creates one. */
    val discoverRows: List<DiscoverRowConfig> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val showUnwatchedBadge: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val showWatchedCheckmark: Boolean = true,
    val showExternalRatings: Boolean = true,
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    /**
     * Classic (pre-Jellyfin-12) home-row semantics (#168). Default `false` —
     * modern server behavior. Projected into [HomeSectionQuery.classicRows].
     */
    val classicRows: Boolean = false,
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

/**
 * The slice as one [HomeSectionPrefs] — the single field mapping, shared by
 * the store's command methods (their read side) and HomeViewModel's prefs
 * collector, so a new [HomeSectionQuery] input can never drift between the
 * two (a second hand-copied mapping is how the write policies drifted).
 */
fun HomeDiscoverySlice.toSectionPrefs(): HomeSectionPrefs = HomeSectionPrefs(
    query = HomeSectionQuery(
        enabledSections = enabledHomeSectionTypes,
        libraryHomeSectionOverrides = libraryHomeSectionOverrides,
        nextUpRewatching = nextUpRewatching,
        nextUpMaxDays = nextUpMaxDays,
        classicRows = classicRows,
        nextUpExcludedSeriesIds = nextUpExcludedSeriesIds,
        hiddenCwItemIds = hiddenCwItemIds,
        pinnedSections = pinnedHomeSections,
        discoverRows = discoverRows,
    ),
    homeSectionOrder = homeSectionOrder,
    mergeContinueWatchingAndNextUp = mergeContinueWatchingAndNextUp,
)
