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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.dataDegradingToDefaults
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.model.HomeLayoutConfig
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.withDiscoverRowEnabled
import com.raulshma.jellyplay.core.model.withDiscoverRowMoved
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeHeroStyle
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * name, the per-user read projection and [restore] FOLD the slice rows (each
 * slice-bound row carries its namespaced read, restore write and slice lens
 * on the spec — the namespaced variant of the videoplayer derivation), the
 * setters write through the rows' namespace-rebuilt keys, [resetKeysFor]
 * filters the rows by reset category, and the namespace migration's per-type
 * copy lists are the rows filtered by declared storage. The read-modify-write
 * commands, the enabled-set version-union decode (whose per-user parse cache
 * a row encoding cannot carry — it spans two rows and fills three slice
 * fields, so it stays the fold's one hand-kept decode), the first-user-claims
 * migration and the v3 upgrade baseline ([ensureV3UpgradeBaseline]) stay
 * hand-written by decision — see the spec KDoc.
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
                // The v3 baseline MUST classify before the namespace migration
                // runs — after it, HOME_NS_MIGRATED is set on fresh installs
                // too and every one would misclassify as an upgrader. See
                // [ensureV3UpgradeBaseline].
                ensureV3UpgradeBaseline(uid)
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
        val HOME_HERO_STYLE = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_HERO_STYLE.keyName)
        val HOME_HERO_SLIDESHOW_INTERVAL_SEC = intPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_HERO_SLIDESHOW_INTERVAL_SEC.keyName)
        val HOME_HERO_VIDEO_ENABLED = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_HERO_VIDEO_ENABLED.keyName)
        val HOME_HERO_VIDEO_TV_ENABLED = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_HERO_VIDEO_TV_ENABLED.keyName)
        val HOME_BACKDROP_ENABLED = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_BACKDROP_ENABLED.keyName)
        val HOME_FOCUS_BACKDROP = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_FOCUS_BACKDROP.keyName)
        val HOME_STATS_CARD = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_STATS_CARD.keyName)
        val HOME_GENRE_RAIL = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_GENRE_RAIL.keyName)
        val HOME_PRESENCE_CHIPS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_PRESENCE_CHIPS.keyName)
        val HOME_NEW_SECTION_ANNOUNCEMENTS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_NEW_SECTION_ANNOUNCEMENTS.keyName)
        val HOME_ENABLED_SECTION_TYPES = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.keyName)
        val HOME_ENABLED_SECTION_TYPES_VERSION = intPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_VERSION.keyName)
        val HOME_SECTION_ORDER = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.keyName)
        val HOME_LIBRARY_SECTION_OVERRIDES = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.keyName)
        /** Legacy all-or-nothing "hide library from home" key — kept only to migrate. */
        val HOME_HIDDEN_LIBRARY_SECTION_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_HIDDEN_LIBRARY_SECTION_IDS.keyName)
        val PINNED_HOME_SECTIONS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.keyName)
        val HOME_DISCOVER_ROWS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.keyName)
        val HOME_LAYOUT_PRESETS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.keyName)
        val HOME_LAYOUT_AUTOPUSH = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_AUTOPUSH.keyName)
        val CONTINUE_WATCHING_CLICK_BEHAVIOR = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.CONTINUE_WATCHING_CLICK_BEHAVIOR.keyName)
        val SHOW_UNWATCHED_BADGE = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_UNWATCHED_BADGE.keyName)
        val HIDE_WATCHED_ITEMS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HIDE_WATCHED_ITEMS.keyName)
        val SHOW_WATCHED_CHECKMARK = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_WATCHED_CHECKMARK.keyName)
        val SHOW_EXTERNAL_RATINGS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_EXTERNAL_RATINGS.keyName)
        val SHOW_QUALITY_BADGES = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_QUALITY_BADGES.keyName)
        val SHOW_NEW_BADGES = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_NEW_BADGES.keyName)
        val HOME_TRICKPLAY_PREVIEW = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_TRICKPLAY_PREVIEW.keyName)
        val ANTI_SPOILER_HOME = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.ANTI_SPOILER_HOME.keyName)
        val MERGE_CONTINUE_WATCHING_NEXT_UP = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.MERGE_CONTINUE_WATCHING_NEXT_UP.keyName)
        val NEXT_UP_MAX_DAYS = intPreferencesKey(HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.keyName)
        val NEXT_UP_REWATCHING = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.NEXT_UP_REWATCHING.keyName)
        val CLASSIC_ROWS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.CLASSIC_ROWS.keyName)
        val EXCLUDE_ACTIVE_SESSIONS = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.EXCLUDE_ACTIVE_SESSIONS.keyName)
        val NEXT_UP_EXCLUDED_SERIES_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.keyName)
        val HIDDEN_CW_ITEM_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.keyName)
        val LAST_VIEWED_SEASON_BY_SERIES = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.keyName)
        val MY_MEDIA_HIDDEN_LIBRARY_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.keyName)
        val THEME_MUSIC_MUTED_SERIES_IDS = stringPreferencesKey(HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.keyName)
        val HOME_LAST_VISIT_EPOCH_MS = longPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_LAST_VISIT_EPOCH_MS.keyName)
        val HOME_ANNOUNCED_VERSION = intPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_ANNOUNCED_VERSION.keyName)
        val SHOW_CLOCK_ON_HOME = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME.keyName)
        val SHOW_SETTINGS_IN_HOME_SEARCH = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.SHOW_SETTINGS_IN_HOME_SEARCH.keyName)
        val HIDE_TOP_HEADER_ON_SCROLL = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HIDE_TOP_HEADER_ON_SCROLL.keyName)
        val HOME_NS_MIGRATED = booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_NS_MIGRATED.keyName)
        val HOME_V3_UPGRADE_BASELINE_DONE =
            booleanPreferencesKey(HomeDiscoveryPreferenceSpecs.HOME_V3_UPGRADE_BASELINE_DONE.keyName)
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
     * Runs a one-time home fold with the module's DataStore failure posture:
     * a read/edit failure (IO error, corruption) is swallowed and degraded —
     * the init collector runs in the ApplicationScope, which has no
     * CoroutineExceptionHandler, so a throw would crash the app and kill the
     * collector for good — and the fold's marker is only set on success, so
     * the next user activation retries (silent-swallow is the module
     * convention — see ArrSecureCredentialsStore.getManualServers).
     * Cancellation is rethrown, never swallowed: a canceled collector must
     * die at its suspension point, not dispatch its edit after the fact — a
     * post-cancel edit would write against whatever state the next
     * activation has since created.
     */
    private suspend fun runOneTimeFold(block: suspend () -> Unit) {
        runCatching { block() }.onFailure { if (it is CancellationException) throw it }
    }

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
     * re-runs to the same result. Runs under [runOneTimeFold]'s
     * swallow-and-degrade failure posture.
     */
    internal suspend fun ensureNamespacedMigration(userId: String) {
        runOneTimeFold {
            if (dataStore.data.first()[Keys.HOME_NS_MIGRATED] == true) return@runOneTimeFold
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
     * ONE-TIME, GLOBAL v3 upgrade baseline. UPCOMING and MY_MEDIA ship as
     * [HomeSectionType.CONFIGURABLE] default-set members, so an ABSENT
     * enabled-set serves both — indistinguishably, on a fresh install and on
     * an install updating from v0.11.4 whose user never touched section
     * config. The read-side union cannot help (an absent set is not a
     * persisted one), so this fold decides once per install: classify, and
     * for an UPGRADING install materialize the default set minus
     * [HomeDiscoveryPreferenceSpecs.V3_UPGRADE_EXCLUDED_SECTIONS] as the
     * first active user's persisted set, stamped current — their home keeps
     * exactly the rows it had, and enabling either section later is an
     * ordinary verbatim write. A user who already persisted a set is left
     * untouched (its v3 absence is respected by the union not carrying the
     * pair); a FRESH install gets no write at all and keeps serving the
     * default with the pair visible.
     *
     * Classification: [Keys.HOME_NS_MIGRATED] already set (any
     * namespacing-era install), or ANY legacy flat home key still on disk
     * (pre-namespacing installs — the copy fold never deletes, so the keys
     * are there whether or not the namespace migration has run). Both
     * signals must be consulted BEFORE [ensureNamespacedMigration] on the
     * same activation — the init collector orders them — because the
     * migration sets the marker on fresh installs too, and a fold that ran
     * after it would misclassify every fresh install as an upgrader.
     *
     * First-user-claims, matching the namespace migration: later users
     * activating on an updated install have no persisted set and start from
     * the spec default (pair visible) — the baseline claims for exactly the
     * user who was active at upgrade time. Idempotent and crash-safe in the
     * same shape: fast-path skip on the marker, classify + write + marker
     * (set last) in one atomic edit, a crash re-runs to the same result.
     * DataStore failures are swallowed (module convention — see
     * [runOneTimeFold]) — the marker is only set on success, so the next
     * activation retries.
     */
    internal suspend fun ensureV3UpgradeBaseline(userId: String) {
        runOneTimeFold {
            if (dataStore.data.first()[Keys.HOME_V3_UPGRADE_BASELINE_DONE] == true) return@runOneTimeFold
            dataStore.edit { prefs ->
                if (prefs[Keys.HOME_V3_UPGRADE_BASELINE_DONE] == true) return@edit
                val wasUpdating = prefs[Keys.HOME_NS_MIGRATED] == true ||
                    // Name-based across ALL slots: the legacy layer holds
                    // typed and pre-typed STRING forms under the same names.
                    prefs.asMap().keys.any { it.name in HomeDiscoveryPreferenceSpecs.legacyRowNames }
                if (wasUpdating) {
                    val setKeyName = HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.rawUserKey(userId).name
                    if (prefs.asMap().keys.none { it.name == setKeyName }) {
                        writeEnabledHomeSectionTypes(
                            prefs,
                            userId,
                            HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.default -
                                HomeDiscoveryPreferenceSpecs.V3_UPGRADE_EXCLUDED_SECTIONS,
                        )
                    }
                }
                prefs[Keys.HOME_V3_UPGRADE_BASELINE_DONE] = true
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

    // Declared BEFORE the eager `homeDiscovery` flow below: property
    // initialization runs in declaration order, and the eager collector's
    // first emission can reach [readerFor] on a dispatcher worker while the
    // constructor is still running — a field declared after the flow reads
    // null there (the launch-time NPE this ordering closes).

    /** The (userId, reader) pair [readerFor] publishes through a CAS. */
    private val readerByUser = AtomicReference<Pair<String, UserReader>?>(null)

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
     * The [readerByUser] field itself is declared above the eager flow —
     * this function must never see it half-initialized.
     */
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
     * ([HomeDiscoveryPreferenceSpecs.userKey] / `rawUserKey`); the slice
     * itself is the FOLD over [HomeDiscoveryPreferenceSpecs.sliceRows] — each
     * slice-bound row reads through its declared namespaced hook and lenses
     * into the slice — and this reader supplies the JSON rows' decodes
     * ([HomeDiscoveryUserJsonReads], whose per-user parse caches live here).
     * The one hand-kept decode inside the fold is the enabled-set's
     * version-union read, which spans two rows and fills three slice fields.
     */
    private inner class UserReader(private val userId: String) : HomeDiscoveryUserJsonReads {

        // Parse caches — per-user by construction: one UserReader per user.
        private var cachedEnabledHomeSectionTypes =
            ParsedCache<EnabledSectionTypesRead>(null, EnabledSectionTypesRead())
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
        private var cachedMyMediaHiddenLibraryIds =
            ParsedCache<Set<String>>(null, HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.default)
        private var cachedThemeMusicMutedSeriesIds =
            ParsedCache<Set<String>>(null, HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.default)

        fun slice(prefs: Preferences): HomeDiscoverySlice {
            // The hand-kept decode: the version-union fold reads TWO rows (the
            // enabled set + its version stamp) and fills THREE slice fields —
            // the set, the stored stamp and the union's contribution — which a
            // per-row fold cannot express; the pair is therefore absent from
            // [HomeDiscoveryPreferenceSpecs.sliceRows] by design.
            val enabledTypes = readEnabledHomeSectionTypes(prefs)
            var slice = HomeDiscoverySlice(
                enabledHomeSectionTypes = enabledTypes.sections,
                enabledHomeSectionTypesVersion = enabledTypes.storedVersion,
                sectionsAddedByUnion = enabledTypes.addedByUnion,
            )
            for (row in HomeDiscoveryPreferenceSpecs.sliceRows) {
                slice = row.readInto(this, prefs, userId, slice)
            }
            return slice
        }

        /**
         * Sections the versioned set shipped after versioning began, each
         * keyed by the schema version it shipped at — the union source
         * [readEnabledHomeSectionTypes] folds in for every set persisted
         * before that version. A future section appends its
         * `(shipped-at, section)` entry here and bumps
         * [HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION];
         * existing entries are never edited.
         *
         * v3 (UPCOMING + MY_MEDIA) is intentionally absent: those ship as
         * default-set members for fresh installs only — upgrading installs
         * are handled by [ensureV3UpgradeBaseline], and nothing must union
         * the pair into a persisted set.
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
         *
         * The read EXPOSES the union's contribution ([EnabledSectionTypesRead.addedByUnion])
         * alongside the effective set, so the announcement gate can name the
         * sections a version bump just turned on instead of silently adding
         * them. Same lenient decode as before — unknown names dropped, a
         * corrupt blob degrades to the default (which reports no union
         * addition — garbage never announces).
         */
        private fun readEnabledHomeSectionTypes(prefs: Preferences): EnabledSectionTypesRead {
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
                default = EnabledSectionTypesRead(),
                parse = { encoded ->
                    val persisted = json.decodeFromString<Set<String>>(encoded)
                        .mapNotNull { name -> HomeSectionType.entries.find { e -> e.name == name } }
                        .toSet()
                    val unioned = sectionsShippedByVersion
                        .filter { (shippedAt, _) -> version < shippedAt }
                        .map { (_, section) -> section }
                    EnabledSectionTypesRead(
                        sections = persisted + unioned,
                        storedVersion = version,
                        addedByUnion = unioned.filterNot { it in persisted }.toSet(),
                    )
                },
                cacheRef = { cachedEnabledHomeSectionTypes = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )
        }

        override fun readHomeSectionOrder(prefs: Preferences): List<HomeSectionType> = PreferenceCodec.cachedJson(
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
        override fun readLibraryHomeSectionOverrides(prefs: Preferences): Map<String, Set<HomeSectionType>> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.rawUserKey(userId)],
                cache = cachedLibraryHomeSectionOverrides,
                default = HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.default,
                parse = { json.decodeFromString<Map<String, Set<HomeSectionType>>>(it) },
                cacheRef = { cachedLibraryHomeSectionOverrides = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readPinnedHomeSections(prefs: Preferences): List<PinnedHomeSection> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)],
                cache = cachedPinnedHomeSections,
                default = HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.default,
                parse = { json.decodeFromString<List<PinnedHomeSection>>(it) },
                cacheRef = { cachedPinnedHomeSections = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readDiscoverRows(prefs: Preferences): List<DiscoverRowConfig> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)],
                cache = cachedDiscoverRows,
                default = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default,
                parse = { json.decodeFromString<List<DiscoverRowConfig>>(it) },
                cacheRef = { cachedDiscoverRows = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readHomeLayoutPresets(prefs: Preferences): List<HomeLayoutPreset> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.rawUserKey(userId)],
                cache = cachedHomeLayoutPresets,
                default = HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_PRESETS.default,
                parse = { json.decodeFromString<List<HomeLayoutPreset>>(it) },
                cacheRef = { cachedHomeLayoutPresets = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readNextUpExcludedSeriesIds(prefs: Preferences): Set<String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId)],
                cache = cachedNextUpExcludedSeriesIds,
                default = HomeDiscoveryPreferenceSpecs.NEXT_UP_EXCLUDED_SERIES_IDS.default,
                parse = { json.decodeFromString<Set<String>>(it) },
                cacheRef = { cachedNextUpExcludedSeriesIds = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readHiddenCwItemIds(prefs: Preferences): Set<String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.rawUserKey(userId)],
                cache = cachedHiddenCwItemIds,
                default = HomeDiscoveryPreferenceSpecs.HIDDEN_CW_ITEM_IDS.default,
                parse = { json.decodeFromString<Set<String>>(it) },
                cacheRef = { cachedHiddenCwItemIds = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readLastViewedSeasonBySeries(prefs: Preferences): Map<String, String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.rawUserKey(userId)],
                cache = cachedLastViewedSeasonBySeries,
                default = HomeDiscoveryPreferenceSpecs.LAST_VIEWED_SEASON_BY_SERIES.default,
                parse = { json.decodeFromString<Map<String, String>>(it) },
                cacheRef = { cachedLastViewedSeasonBySeries = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readMyMediaHiddenLibraryIds(prefs: Preferences): Set<String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.rawUserKey(userId)],
                cache = cachedMyMediaHiddenLibraryIds,
                default = HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.default,
                parse = { json.decodeFromString<Set<String>>(it) },
                cacheRef = { cachedMyMediaHiddenLibraryIds = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        override fun readThemeMusicMutedSeriesIds(prefs: Preferences): Set<String> =
            PreferenceCodec.cachedJson(
                raw = prefs[HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.rawUserKey(userId)],
                cache = cachedThemeMusicMutedSeriesIds,
                default = HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.default,
                parse = { json.decodeFromString<Set<String>>(it) },
                cacheRef = { cachedThemeMusicMutedSeriesIds = it },
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
     * there is no namespace to write into. Returns the post-edit snapshot —
     * [editLayoutForUser] reads the active user back out of it so a skipped
     * pre-login write never bumps the layout revision.
     */
    private suspend fun editForUser(block: (MutablePreferences, String) -> Unit): Preferences =
        dataStore.edit { prefs ->
            val userId = identityStore.activeUserIdIn(prefs) ?: return@edit
            block(prefs, userId)
        }

    /**
     * The layout-writing twin of [editForUser]: after the
     * edit commits (and only when it actually landed in a user namespace), the
     * monotonic [layoutRevision] bumps once. Every command that writes a
     * domain the [HomeLayoutConfig] payload carries routes through HERE, so
     * the autopush dirty signal can never drift from the write set — a plain
     * [editForUser] call is by construction not a layout write.
     */
    private suspend fun editLayoutForUser(block: (MutablePreferences, String) -> Unit) {
        val snapshot = editForUser(block)
        if (identityStore.activeUserIdIn(snapshot) != null) {
            _layoutRevision.value += 1
        }
    }

    /**
     * The layout-dirty signal. Bumped by [editLayoutForUser] after every
     * layout-writing command's edit commits — the
     * `HomeLayoutSyncController`'s debounced autopush collects it (armed by
     * the `home_layout_autopush` pref + sign-in state) and pushes the current
     * layout to the server's DisplayPreferences store. A monotonic counter,
     * not a payload: consumers only need "the layout changed again", and the
     * counter makes every distinct write observable through the StateFlow
     * conflation.
     */
    private val _layoutRevision = MutableStateFlow(0)
    val layoutRevision: StateFlow<Int> = _layoutRevision.asStateFlow()

    suspend fun setHomeMode(mode: HomeMode) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_MODE.rawUserKey(userId)] = mode.name
    }

    suspend fun setHomeHeroEnabled(enabled: Boolean) = editLayoutForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.userKey(userId)] = enabled
    }

    /** The hero block's presentation: carousel, slideshow, detailed spotlight or TV slider. */
    suspend fun setHomeHeroStyle(style: HomeHeroStyle) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_HERO_STYLE.rawUserKey(userId)] = style.name
    }

    /**
     * The slideshow hero's auto-advance pacing in seconds; clamped to the
     * row's declared 3–60 bounds (the setter is the clamp owner).
     */
    suspend fun setHomeHeroSlideshowIntervalSec(seconds: Int) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_HERO_SLIDESHOW_INTERVAL_SEC.userKey(userId)] =
            seconds.coerceIn(HomeDiscoveryPreferenceSpecs.HOME_HERO_SLIDESHOW_INTERVAL_SEC_BOUNDS)
    }

    suspend fun setHomeBackdropEnabled(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_BACKDROP_ENABLED.userKey(userId)] = enabled
    }

    /** Video hero billboard (touch/default form factors) — default off. */
    suspend fun setHeroVideoEnabled(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_HERO_VIDEO_ENABLED.userKey(userId)] = enabled
    }

    /** Video hero billboard (TV form factor) — default off. */
    suspend fun setHeroVideoTvEnabled(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_HERO_VIDEO_TV_ENABLED.userKey(userId)] = enabled
    }

    /** Focus-driven ambient backdrop (Android TV) — default off. */
    suspend fun setFocusBackdrop(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_FOCUS_BACKDROP.userKey(userId)] = enabled
    }

    /** Personal stats card — default off. */
    suspend fun setStatsCard(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_STATS_CARD.userKey(userId)] = enabled
    }

    /** Genre quick-access rail — default off. */
    suspend fun setGenreRail(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_GENRE_RAIL.userKey(userId)] = enabled
    }

    /** Watch-party / presence chips — default off (admin-gated surface). */
    suspend fun setPresenceChips(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_PRESENCE_CHIPS.userKey(userId)] = enabled
    }

    /** New-section announcement toast — default on. */
    suspend fun setNewSectionAnnouncements(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_NEW_SECTION_ANNOUNCEMENTS.userKey(userId)] = enabled
    }

    /** Layout DisplayPreferences auto-push — default off (MANUAL sync policy). */
    suspend fun setLayoutAutopush(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_LAYOUT_AUTOPUSH.userKey(userId)] = enabled
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

    suspend fun setEnabledHomeSectionTypes(types: Set<HomeSectionType>) = editLayoutForUser { prefs, userId ->
        writeEnabledHomeSectionTypes(prefs, userId, types)
    }

    suspend fun setHomeSectionOrder(order: List<HomeSectionType>) = editLayoutForUser { prefs, userId ->
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

    suspend fun setLibraryHomeSectionOverrides(overrides: Map<String, Set<HomeSectionType>>) = editLayoutForUser { prefs, userId ->
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
    suspend fun setSectionVisible(type: HomeSectionType, visible: Boolean) = editLayoutForUser { prefs, userId ->
        val updated = read(prefs).toSectionPrefs().withSectionVisible(type, visible)
        writeEnabledHomeSectionTypes(prefs, userId, updated.query.enabledSections)
    }

    suspend fun moveSection(type: HomeSectionType, up: Boolean) = editLayoutForUser { prefs, userId ->
        val updated = read(prefs).toSectionPrefs().withSectionMoved(type, up) ?: return@editLayoutForUser
        prefs[HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.rawUserKey(userId)] =
            json.encodeToString(normalizedSectionOrder(updated.homeSectionOrder).map { t -> t.name })
    }

    suspend fun setLibrarySectionVisible(
        libraryId: String,
        type: HomeSectionType,
        visible: Boolean,
    ) = editLayoutForUser { prefs, userId ->
        val updated = read(prefs).toSectionPrefs()
            .withLibrarySectionVisible(libraryId, type, visible)
        // Same empty-set drop as [setLibraryHomeSectionOverrides]: a library
        // whose disabled-set empties again simply loses its key.
        prefs[HomeDiscoveryPreferenceSpecs.HOME_LIBRARY_SECTION_OVERRIDES.rawUserKey(userId)] =
            json.encodeToString(updated.query.libraryHomeSectionOverrides.filterValues { it.isNotEmpty() })
    }

    suspend fun setPinnedHomeSections(sections: List<PinnedHomeSection>) = editLayoutForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)] = json.encodeToString(sections)
    }

    suspend fun addPinnedHomeSection(section: PinnedHomeSection) = editLayoutForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.default)
        if (current.none { it.id == section.id }) {
            prefs[key] = json.encodeToString(current + section)
        }
    }

    suspend fun removePinnedHomeSection(sectionId: String) = editLayoutForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.PINNED_HOME_SECTIONS.default)
        prefs[key] = json.encodeToString(current.filterNot { it.id == sectionId })
    }

    // ── Custom Discover rows ────────────────────────────────────────────────

    /** Wholesale replace (layout presets, reset). */
    suspend fun setDiscoverRows(rows: List<DiscoverRowConfig>) = editLayoutForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)] = json.encodeToString(rows)
    }

    /** Add-or-replace by id — the editor's save. */
    suspend fun upsertDiscoverRow(row: DiscoverRowConfig) = editLayoutForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        val next = if (current.any { it.id == row.id }) {
            current.map { if (it.id == row.id) row else it }
        } else {
            current + row
        }
        prefs[key] = json.encodeToString(next)
    }

    suspend fun removeDiscoverRow(rowId: String) = editLayoutForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        prefs[key] = json.encodeToString(current.filterNot { it.id == rowId })
    }

    suspend fun setDiscoverRowEnabled(rowId: String, enabled: Boolean) = editLayoutForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        prefs[key] = json.encodeToString(current.withDiscoverRowEnabled(rowId, enabled))
    }

    suspend fun moveDiscoverRow(rowId: String, up: Boolean) = editLayoutForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.HOME_DISCOVER_ROWS.default)
        val moved = current.withDiscoverRowMoved(rowId, up) ?: return@editLayoutForUser
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

    suspend fun setContinueWatchingClickBehavior(behavior: ContinueWatchingClickBehavior) = editLayoutForUser { prefs, userId ->
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

    /** Quality badges (4K / HDR / DV / Atmos) on cards — default on. */
    suspend fun setShowQualityBadges(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.SHOW_QUALITY_BADGES.userKey(userId)] = enabled
    }

    /** "NEW" badges with visit memory — default on. */
    suspend fun setShowNewBadges(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.SHOW_NEW_BADGES.userKey(userId)] = enabled
    }

    /** Trickplay preview on wide cards (TV focus) — default on, TV-only behavior. */
    suspend fun setTrickplayPreview(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_TRICKPLAY_PREVIEW.userKey(userId)] = enabled
    }

    /** Anti-spoiler home master switch — default off. */
    suspend fun setAntiSpoilerHome(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.ANTI_SPOILER_HOME.userKey(userId)] = enabled
    }

    suspend fun setMergeContinueWatchingAndNextUp(enabled: Boolean) = editLayoutForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.MERGE_CONTINUE_WATCHING_NEXT_UP.userKey(userId)] = enabled
    }

    suspend fun setNextUpMaxDays(days: Int) = editLayoutForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.userKey(userId)] = days.coerceAtLeast(0)
    }

    suspend fun setNextUpRewatching(enabled: Boolean) = editLayoutForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.NEXT_UP_REWATCHING.userKey(userId)] = enabled
    }

    suspend fun setClassicRows(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.CLASSIC_ROWS.userKey(userId)] = enabled
    }

    /** Continue watching must not offer items playing on another device — default off. */
    suspend fun setExcludeActiveSessions(enabled: Boolean) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.EXCLUDE_ACTIVE_SESSIONS.userKey(userId)] = enabled
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

    // ── My Media hidden libraries (inline curation) ──────────────────────

    /**
     * Wholesale replace of the hidden-from-My-Media library set (the section
     * config sheet's future "restore all" and backup restore).
     */
    suspend fun setMyMediaHiddenLibraryIds(ids: Set<String>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.rawUserKey(userId)] = json.encodeToString(ids)
    }

    /** Long-press a My Media tile → hide that library. Idempotent add. */
    suspend fun hideLibraryFromMyMedia(libraryId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.default)
        prefs[key] = json.encodeToString(current + libraryId)
    }

    /** Un-hide one library. Idempotent remove. */
    suspend fun showLibraryInMyMedia(libraryId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.default)
        prefs[key] = json.encodeToString(current - libraryId)
    }

    /** Bulk restore — every hidden My Media library shown again. */
    suspend fun clearMyMediaHiddenLibraries() = editForUser { prefs, userId ->
        prefs.remove(HomeDiscoveryPreferenceSpecs.MY_MEDIA_HIDDEN_LIBRARY_IDS.rawUserKey(userId))
    }

    // ── Theme-music per-series mute memory ─────────────────────────────

    /** Wholesale replace of the theme-music muted-series set (backup restore). */
    suspend fun setThemeMusicMutedSeriesIds(ids: Set<String>) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.rawUserKey(userId)] = json.encodeToString(ids)
    }

    /** The detail screen's mute chip wrote a per-series "never" memory. Idempotent add. */
    suspend fun muteThemeMusicForSeries(seriesId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.default)
        prefs[key] = json.encodeToString(current + seriesId)
    }

    /** The detail screen's unmute chip clears the per-series memory. Idempotent remove. */
    suspend fun unmuteThemeMusicForSeries(seriesId: String) = editForUser { prefs, userId ->
        val key = HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.rawUserKey(userId)
        val current = decodeOrDefault(prefs, key, HomeDiscoveryPreferenceSpecs.THEME_MUSIC_MUTED_SERIES_IDS.default)
        prefs[key] = json.encodeToString(current - seriesId)
    }

    // ── Internal home stamps (visit memory / announce-once) ─────────

    /**
     * Stamps the home visit time (epoch ms) — the home screen's dwell timer
     * writes it once per visit so items added later render the NEW badge.
     */
    suspend fun setHomeLastVisitEpochMs(epochMs: Long) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_LAST_VISIT_EPOCH_MS.userKey(userId)] = epochMs
    }

    /**
     * Stamps the enabled-set schema version whose new-section announcement was
     * shown (or dismissed) — the announce-once gate.
     */
    suspend fun setHomeAnnouncedVersion(version: Int) = editForUser { prefs, userId ->
        prefs[HomeDiscoveryPreferenceSpecs.HOME_ANNOUNCED_VERSION.userKey(userId)] = version
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
     * DataStore — into the CURRENT active user's namespace — by FOLDING the
     * slice rows ([HomeDiscoveryPreferenceSpecs.sliceRows], each row writing
     * through its declared namespaced write: typed slots, enum-by-name
     * strings, JSON blobs via [json]), plus the ONE hand-kept line beside the
     * fold — the enabled-set write, whose version stamp must land in the same
     * edit as the set ([writeEnabledHomeSectionTypes]; a per-row write cannot
     * stamp two keys), which is also why that row pair takes no slice row.
     */
    suspend fun restore(slice: HomeDiscoverySlice) {
        editLayoutForUser { prefs, userId ->
            for (row in HomeDiscoveryPreferenceSpecs.sliceRows) {
                row.writeFrom(slice, prefs, userId)
            }
            writeEnabledHomeSectionTypes(prefs, userId, slice.enabledHomeSectionTypes)
        }
    }

    /**
     * The restore-apply helper — ONE command that applies a whole
     * [HomeLayoutConfig] (the layout payload the presets machinery serializes
     * and the server sync carries) by routing through the SAME store setters
     * the local preset-restore path has always used. There is deliberately no
     * parallel raw-key write here: the setters' normalization
     * ([normalizedSectionOrder]), the enabled-set version stamp
     * ([writeEnabledHomeSectionTypes]) and the [layoutRevision] bumps all
     * stay theirs, so a restored layout is indistinguishable from a
     * hand-tuned one — to the UI projection, to backup, and to the autopush.
     *
     * Callers: the presets ViewModel's apply (import / load / reset) and the
     * Home Settings "Restore layout from server" action (the controller's
     * lenient decode feeds the config). Sequential edits, matching the
     * `editor.edit { … }` fan-out the preset path has always run — the
     * DataStore is the atomicity unit per key, and every intermediate state
     * is a valid layout.
     */
    suspend fun applyLayout(config: HomeLayoutConfig) {
        setEnabledHomeSectionTypes(config.enabledHomeSectionTypes)
        setHomeSectionOrder(config.homeSectionOrder)
        setLibraryHomeSectionOverrides(config.libraryHomeSectionOverrides)
        setMergeContinueWatchingAndNextUp(config.mergeContinueWatchingAndNextUp)
        setNextUpMaxDays(config.nextUpMaxDays)
        setNextUpRewatching(config.nextUpRewatching)
        setPinnedHomeSections(config.pinnedHomeSections)
        setDiscoverRows(config.discoverRows)
        setHomeHeroEnabled(config.homeHeroEnabled)
        setContinueWatchingClickBehavior(config.continueWatchingClickBehavior)
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
    /** The hero block's presentation: carousel, slideshow, detailed spotlight or TV slider. */
    val homeHeroStyle: HomeHeroStyle = HomeHeroStyle.CAROUSEL,
    /** The slideshow hero's auto-advance pacing in seconds (3–60). Default 8. */
    val homeHeroSlideshowIntervalSec: Int = 8,
    /** Video hero billboard (touch/default form factors). Default off — static hero. */
    val homeHeroVideoEnabled: Boolean = false,
    /** Video hero billboard (TV form factor). Default off. */
    val homeHeroVideoTvEnabled: Boolean = false,
    val homeBackdropEnabled: Boolean = true,
    /** Focus-driven ambient backdrop (Android TV). Default off. */
    val homeFocusBackdrop: Boolean = false,
    /** Personal stats card ("Your library week"). Default off. */
    val homeStatsCard: Boolean = false,
    /** Genre quick-access rail. Default off. */
    val homeGenreRail: Boolean = false,
    /** Watch-party / presence chips. Default off (admin-gated surface). */
    val homePresenceChips: Boolean = false,
    /** New-section announcement toast. Default on. */
    val homeNewSectionAnnouncements: Boolean = true,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    /**
     * The schema version the persisted enabled-set was last written at —
     * derived by the version-union read, never written back ([restore]
     * stamps CURRENT through the write fold). The announce-once gate
     * compares it against [homeAnnouncedVersion].
     */
    val enabledHomeSectionTypesVersion: Int = HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION,
    /** The sections the read's version-union contributed — empty once the persisted stamp is current. */
    val sectionsAddedByUnion: Set<HomeSectionType> = emptySet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    /** The user's custom Discover rows; empty until the user creates one. */
    val discoverRows: List<DiscoverRowConfig> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    /** Layout DisplayPreferences auto-push. Default off (MANUAL sync policy). */
    val homeLayoutAutopush: Boolean = false,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val showUnwatchedBadge: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val showWatchedCheckmark: Boolean = true,
    val showExternalRatings: Boolean = true,
    /** Quality badges (4K / HDR / DV / Atmos) on cards. Default on. */
    val showQualityBadges: Boolean = true,
    /** "NEW" badges for items added since the last home visit. Default on. */
    val showNewBadges: Boolean = true,
    /** Trickplay preview on wide cards (TV focus). Default on, TV-only behavior. */
    val homeTrickplayPreview: Boolean = true,
    /** Anti-spoiler home master switch. Default off. */
    val antiSpoilerHome: Boolean = false,
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    /**
     * Classic (pre-Jellyfin-12) home-row semantics. Default `false` —
     * modern server behavior. Projected into [HomeSectionQuery.classicRows].
     */
    val classicRows: Boolean = false,
    /** Exclude items with an active playback session from Continue Watching / Next Up. Default off. */
    val excludeActiveSessions: Boolean = false,
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
    /** Libraries hidden from the My Media tiles row (inline long-press curation). */
    val myMediaHiddenLibraryIds: Set<String> = emptySet(),
    /** Series whose ambient theme music stays muted ("never for this series" memory). */
    val themeMusicMutedSeriesIds: Set<String> = emptySet(),
    /** When the user last saw home (epoch ms; 0 = never stamped). NEW-badge threshold. */
    val homeLastVisitEpochMs: Long = 0L,
    /** The enabled-set schema version whose new-section announcement was last shown. */
    val homeAnnouncedVersion: Int = 0,
    val showClockOnHome: Boolean = false,
    val showSettingsInHomeSearch: Boolean = true,
    /**
     * Whether the home screen's top header dock auto-hides on scroll-down and
     * reappears on scroll-up. Default `false` — the dock stays pinned (current
     * behaviour) until the user opts in. Mirrors the floating nav-bar
     * `hideBottomNavOnScroll` toggle.
     */
    val hideTopHeaderOnScroll: Boolean = false,
) {
    /** Whether [libraryId] is hidden from the My Media tiles row. */
    fun isLibraryHiddenFromMyMedia(libraryId: String): Boolean = libraryId in myMediaHiddenLibraryIds

    /** Whether [seriesId]'s ambient theme music is muted by the per-series memory. */
    fun isThemeMusicMutedFor(seriesId: String): Boolean = seriesId in themeMusicMutedSeriesIds
}

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
        excludeActiveSessions = excludeActiveSessions,
        nextUpExcludedSeriesIds = nextUpExcludedSeriesIds,
        hiddenCwItemIds = hiddenCwItemIds,
        pinnedSections = pinnedHomeSections,
        discoverRows = discoverRows,
        // The tiles row's hidden-library curation rides the fetch query,
        // so the assembler's filter and the SWR cache key see it together.
        myMediaHiddenLibraryIds = myMediaHiddenLibraryIds,
    ),
    homeSectionOrder = homeSectionOrder,
    mergeContinueWatchingAndNextUp = mergeContinueWatchingAndNextUp,
)

/**
 * The version-union decode's outcome. [sections] is the effective
 * enabled set (what [HomeDiscoverySlice.enabledHomeSectionTypes] has
 * always carried); [storedVersion] is the schema stamp the persisted
 * set was written at; [addedByUnion] is the part of [sections] THE
 * UNION ITSELF contributed — non-empty exactly when the persisted set
 * predates a shipped section, which is the announce-once signal
 * (a section the user already had, or disabled after seeing it, never
 * appears here).
 */
private data class EnabledSectionTypesRead(
    val sections: Set<HomeSectionType> = HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES.default,
    val storedVersion: Int = HomeDiscoveryPreferenceSpecs.HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION,
    val addedByUnion: Set<HomeSectionType> = emptySet(),
)
