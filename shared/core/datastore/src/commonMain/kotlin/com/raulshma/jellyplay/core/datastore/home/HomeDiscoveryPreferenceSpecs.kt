package com.raulshma.jellyplay.core.datastore.home

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceStorage
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PreferenceResetCategory

/**
 * The home discovery domain's single preference declaration: one
 * [PreferenceSpec] row per key `HomeDiscoveryStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name AND the
 * canonical backup/export name, declared exactly once), the default, the reset
 * category, and the read/write encoding where the row carries one.
 *
 * `HomeDiscoveryStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the per-user read projection
 * delegates each plain knob to its row encoding through the per-user hook
 * below, the single-key setters and `restore` write through the rows'
 * namespace-rebuilt keys, and `resetKeysFor` filters the rows by reset
 * category — so a row cannot drift from the machinery that reads or writes it.
 *
 * **The per-user namespacing is the one home-domain wrinkle**: every key this
 * store owns persists as `u_<userId>::<canonical>` (see [UserNamespacedKeys]).
 * The transform is a spec-side KEY-REBUILD hook, not a second declaration:
 * [userKey] / [rawUserKey] rebuild the row's slot under a user namespace from
 * the row's ONE declared [PreferenceSpec.keyName] plus its declared storage
 * ([PreferenceSpec.typedKeyNamed]), so a namespaced key can never drift its
 * canonical declaration. [readBoolForUser] / [readIntForUser] /
 * [readEnumForUser] route the plain rows' reads (with their legacy string
 * fallback, consulted at the NAMESPACED name — the fallback name is the
 * namespaced slot, exactly as the hand-written per-user reads did) through the
 * same rows.
 *
 * The JSON-blob rows ([HOME_ENABLED_SECTION_TYPES], [HOME_SECTION_ORDER],
 * [HOME_LIBRARY_SECTION_OVERRIDES], [PINNED_HOME_SECTIONS],
 * [HOME_DISCOVER_ROWS], [HOME_LAYOUT_PRESETS], [NEXT_UP_EXCLUDED_SERIES_IDS],
 * [HIDDEN_CW_ITEM_IDS], [LAST_VIEWED_SEASON_BY_SERIES]) are
 * [PreferenceSpec.custom] declarations — the spec owns the wire name, default
 * and reset participation, while the store's per-user reader supplies the
 * codec: their JSON decode caches are PER-USER by construction (one reader
 * instance per active user, so a user switch can never serve another user's
 * cached parse), and their writes carry extra semantics (the enabled-set's
 * version stamp, the order normalization, the read-modify-write commands)
 * that a row encoding cannot express.
 *
 * Not derivable by design (the accepted residuals, the videoplayer/playback
 * precedent): the read-modify-write commands (`setSectionVisible`,
 * `moveSection`, `addPinnedHomeSection`, `upsertDiscoverRow`, …), the
 * `writeEnabledHomeSectionTypes` version-stamp fold and the first-user-claims
 * namespace migration stay hand-written in the store.
 *
 * Adding a preference: one row here, one [HomeDiscoverySlice] property, one
 * `slice()` read row, one `restore()` row (and its setter) — plus one
 * write-through test line; the derivation covers key identity, encodings,
 * reset lists and the migration copy lists, not the slice plumbing or its
 * coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object HomeDiscoveryPreferenceSpecs {

    // ------------------------------------------------------------------
    // Enabled-set schema versioning (declared beside the rows that read
    // and stamp them; existing entries are never edited — a future section
    // appends its shipped-at entry and bumps CURRENT).
    // ------------------------------------------------------------------

    /**
     * Schema version of `home_enabled_section_types`, stamped by every write
     * of that set. A persisted set read at an older version is unioned with
     * the sections shipped in each intervening version — newly-shipped
     * sections default to visible instead of staying invisible to every user
     * whose set predates them, while a user who then disables the section
     * keeps that choice.
     */
    const val HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION = 2

    /**
     * The version at which [HomeSectionType.CONTINUE_READING] shipped —
     * a persisted set stamped older than this unions it in on read.
     * Distinct from [HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION] on
     * purpose: a future section bump advances the latter and adds a new
     * shipped-at entry, never editing this one.
     */
    const val CONTINUE_READING_SHIPPED_AT_VERSION = 1

    /**
     * The version at which [HomeSectionType.DISCOVER] (the custom discover
     * rows block) shipped — same union-in mechanics as
     * [CONTINUE_READING_SHIPPED_AT_VERSION]: without the entry, every
     * user whose enabled-set predates the section would never see the
     * DISCOVER block render, no matter their row config.
     */
    const val DISCOVER_SHIPPED_AT_VERSION = 2

    // ------------------------------------------------------------------
    // Settings-search route kinds (the plain ids the feature layer maps to
    // its Routes) + the standalone entries + searchEntries list live at the
    // bottom; the searchable rows carry their [PreferenceSearchSpec] inline.
    // ------------------------------------------------------------------

    /** Route kind: rows that deep-link into the home settings screen. */
    const val ROUTE_HOME_SETTINGS = "home_settings"

    /** Route kind: the "Hidden from Next Up" management screen. */
    const val ROUTE_NEXT_UP_EXCLUDED = "next_up_excluded"

    /** Route kind: the pinned home sections drill-in. */
    const val ROUTE_PINNED_HOME_SECTIONS = "pinned_home_sections"

    /** Route kind: the home layout presets drill-in. */
    const val ROUTE_HOME_LAYOUT_PRESETS = "home_layout_presets"

    /** Route kind: the library home sections drill-in. */
    const val ROUTE_LIBRARY_HOME_SECTIONS = "library_home_sections"

    /** Route kind: the discover rows editor drill-in. */
    const val ROUTE_DISCOVER_ROWS = "discover_rows"

    // ------------------------------------------------------------------
    // Mode + hero/backdrop
    // ------------------------------------------------------------------

    val HOME_MODE: PreferenceSpec<HomeMode> = PreferenceSpec.enumRow(
        keyName = "home_mode",
        default = HomeMode.VIDEO,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_mode",
            titleKey = "ss_home_mode_title",
            subtitleKey = "ss_home_mode_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("home", "layout", "mode", "video", "music"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_HERO_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_hero_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "hero_section",
            titleKey = "ss_hero_section_title",
            subtitleKey = "ss_hero_section_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hero", "banner", "featured", "home", "carousel"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_BACKDROP_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_backdrop_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_backdrop",
            titleKey = "ss_home_backdrop_title",
            subtitleKey = "ss_home_backdrop_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("home", "backdrop", "artwork", "background", "blur", "wallpaper"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    // ------------------------------------------------------------------
    // Section config (enabled set + version stamp, order, overrides)
    // ------------------------------------------------------------------

    /**
     * The configurable-section enable set as a JSON name-string set. The
     * row declares the wire name, default and reset participation; the store's
     * per-user reader supplies the version-union decode (its parse cache keys
     * on the composite `"v<version>:<raw>"` — the stamp is a decode input,
     * see [HOME_ENABLED_SECTION_TYPES_VERSION]), and the store's
     * `writeEnabledHomeSectionTypes` owns the write (it must stamp the
     * version in the same edit — a row encoding cannot write two keys).
     */
    val HOME_ENABLED_SECTION_TYPES: PreferenceSpec<Set<HomeSectionType>> = PreferenceSpec.custom(
        keyName = "home_enabled_section_types",
        default = HomeSectionType.CONFIGURABLE.toSet(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /** The enabled-set's schema-version stamp ([HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION]). Not a slice field. */
    val HOME_ENABLED_SECTION_TYPES_VERSION: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_enabled_section_types_version",
        default = 0,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /**
     * The configurable-section render order as a JSON name-string list
     * (dual-format decode tolerates the legacy Set encoding; the store
     * normalizes on write). Store-supplied decode for the same per-user-cache
     * reason as [HOME_ENABLED_SECTION_TYPES].
     */
    val HOME_SECTION_ORDER: PreferenceSpec<List<HomeSectionType>> = PreferenceSpec.custom(
        keyName = "home_section_order",
        default = HomeSectionType.CONFIGURABLE,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /**
     * The per-library section overrides as a JSON map (library id → disabled
     * set). The legacy all-or-nothing `home_hidden_library_section_ids`
     * conversion happens in the flat layer (store-owned migration); this row
     * owns the converted map's declaration.
     */
    val HOME_LIBRARY_SECTION_OVERRIDES: PreferenceSpec<Map<String, Set<HomeSectionType>>> = PreferenceSpec.custom(
        keyName = "home_library_section_overrides",
        default = emptyMap(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "configure_libraries",
            titleKey = "ss_configure_libraries_title",
            subtitleKey = "ss_configure_libraries_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("library", "libraries", "latest", "recently", "added", "home", "row", "shelf", "hide", "show"),
            routeKind = ROUTE_LIBRARY_HOME_SECTIONS,
        ),
    )

    /**
     * Legacy all-or-nothing "hide library from home" key — the one
     * migration-SOURCE row: it is reset with the category and claimed by the
     * namespace migration's conversion fold, but is never copied into a
     * namespace and never read as a knob (excluded from [migrationRows]).
     */
    val HOME_HIDDEN_LIBRARY_SECTION_IDS: PreferenceSpec<String?> = PreferenceSpec.string(
        keyName = "home_hidden_library_section_ids",
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    val PINNED_HOME_SECTIONS: PreferenceSpec<List<PinnedHomeSection>> = PreferenceSpec.custom(
        keyName = "pinned_home_sections",
        default = emptyList(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "pinned_home_sections",
            titleKey = "ss_pinned_home_sections_title",
            subtitleKey = "ss_pinned_home_sections_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("pinned", "home", "collection", "playlist", "favorites", "genre", "studio", "shelf", "row"),
            routeKind = ROUTE_PINNED_HOME_SECTIONS,
        ),
    )

    /** The user's custom Discover rows, as one JSON list (list order = within-block render order). */
    val HOME_DISCOVER_ROWS: PreferenceSpec<List<DiscoverRowConfig>> = PreferenceSpec.custom(
        keyName = "home_discover_rows",
        default = emptyList(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "discover_rows",
            titleKey = "ss_discover_rows_title",
            subtitleKey = "ss_discover_rows_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("discover", "rows", "custom", "filters", "seerr", "jellyfin", "shelves", "random", "dice", "build", "recommendations"),
            routeKind = ROUTE_DISCOVER_ROWS,
        ),
    )

    val HOME_LAYOUT_PRESETS: PreferenceSpec<List<HomeLayoutPreset>> = PreferenceSpec.custom(
        keyName = "home_layout_presets",
        default = emptyList(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_layout_presets",
            titleKey = "ss_home_layout_presets_title",
            subtitleKey = "ss_home_layout_presets_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("preset", "layout", "home", "save", "load", "import", "export", "share", "reset", "backup", "configuration"),
            routeKind = ROUTE_HOME_LAYOUT_PRESETS,
        ),
    )

    val CONTINUE_WATCHING_CLICK_BEHAVIOR: PreferenceSpec<ContinueWatchingClickBehavior> = PreferenceSpec.enumRow(
        keyName = "continue_watching_click_behavior",
        default = ContinueWatchingClickBehavior.DETAILS,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "continue_watching_click",
            titleKey = "ss_continue_watching_click_title",
            subtitleKey = "ss_continue_watching_click_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("continue watching", "tap", "click", "resume", "play", "details"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    // ------------------------------------------------------------------
    // Watched / badge / ratings toggles — the card-display quartet. Their
    // search entries point at the Home settings hub's Cards group (the
    // finished Appearance → HomeSettings move): the knobs are home-discovery
    // state and render unconditionally there (no advanced gate), so the
    // specs declare no advanced flag.
    // ------------------------------------------------------------------

    val SHOW_UNWATCHED_BADGE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_unwatched_badge",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "show_unwatched_badge",
            titleKey = "ss_show_unwatched_badge_title",
            subtitleKey = "ss_show_unwatched_badge_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("unwatched", "badge", "indicator", "new", "marker"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HIDE_WATCHED_ITEMS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "hide_watched_items",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "hide_watched_items",
            titleKey = "ss_hide_watched_items_title",
            subtitleKey = "ss_hide_watched_items_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hide", "watched", "filter", "library", "clean"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val SHOW_WATCHED_CHECKMARK: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_watched_checkmark",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "show_watched_checkmark",
            titleKey = "ss_show_watched_checkmark_title",
            subtitleKey = "ss_show_watched_checkmark_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("watched", "checkmark", "badge", "indicator", "finished"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val SHOW_EXTERNAL_RATINGS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_external_ratings",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "show_external_ratings",
            titleKey = "ss_show_external_ratings_title",
            subtitleKey = "ss_show_external_ratings_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("ratings", "imdb", "tmdb", "critic", "score", "star"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val MERGE_CONTINUE_WATCHING_NEXT_UP: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "merge_continue_watching_next_up",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "merge_continue_next_up",
            titleKey = "ss_merge_continue_next_up_title",
            subtitleKey = "ss_merge_continue_next_up_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("merge", "combine", "continue watching", "next up", "single row"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    // ------------------------------------------------------------------
    // Next Up + Continue Watching curation
    // ------------------------------------------------------------------

    val NEXT_UP_MAX_DAYS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "next_up_max_days",
        default = 0,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "next_up_max_days",
            titleKey = "ss_next_up_max_days_title",
            subtitleKey = "ss_next_up_max_days_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("next up", "days", "time window", "recent", "max days", "filter"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val NEXT_UP_REWATCHING: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "next_up_rewatching",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "next_up_rewatching",
            titleKey = "ss_next_up_rewatching_title",
            subtitleKey = "ss_next_up_rewatching_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("next up", "rewatching", "rewatch", "repeat"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * Classic (pre-Jellyfin-12) home-row semantics (#168): Continue Watching
     * narrows to leaf items, latest-media rows pin to Series/Movie. Default
     * false — modern server behavior.
     */
    val CLASSIC_ROWS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "classic_rows",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "classic_rows",
            titleKey = "ss_classic_rows_title",
            subtitleKey = "ss_classic_rows_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("classic", "legacy", "jellyfin 12", "continue watching", "episodes", "series", "seasons", "recently added", "latest", "row", "behavior"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val NEXT_UP_EXCLUDED_SERIES_IDS: PreferenceSpec<Set<String>> = PreferenceSpec.custom(
        keyName = "next_up_excluded_series_ids",
        default = emptySet(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "next_up_hidden",
            titleKey = "ss_next_up_hidden_title",
            subtitleKey = "ss_next_up_hidden_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("next up", "hidden", "exclude", "restore", "series", "remove"),
            routeKind = ROUTE_NEXT_UP_EXCLUDED,
        ),
    )

    val HIDDEN_CW_ITEM_IDS: PreferenceSpec<Set<String>> = PreferenceSpec.custom(
        keyName = "hidden_cw_item_ids",
        default = emptySet(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "unhide_cw",
            titleKey = "ss_unhide_cw_title",
            subtitleKey = "ss_unhide_cw_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("unhide", "continue watching", "hidden", "reset", "show"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * Per-series last-viewed season tab (seriesId → seasonId). Lets the
     * series detail screen reopen on the season the user was browsing
     * instead of always the smart-play default. Stored as a JSON map.
     */
    val LAST_VIEWED_SEASON_BY_SERIES: PreferenceSpec<Map<String, String>> = PreferenceSpec.custom(
        keyName = "last_viewed_season_by_series",
        default = emptyMap(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    // ------------------------------------------------------------------
    // Home chrome
    // ------------------------------------------------------------------

    val SHOW_CLOCK_ON_HOME: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_clock_on_home",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "clock_home",
            titleKey = "ss_clock_home_title",
            subtitleKey = "ss_clock_home_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("clock", "time", "home", "wall", "current"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val SHOW_SETTINGS_IN_HOME_SEARCH: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_settings_in_home_search",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "settings_in_home_search",
            titleKey = "ss_settings_in_home_search_title",
            subtitleKey = "ss_settings_in_home_search_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("search", "settings", "home", "find", "discover", "quick", "shortcut"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HIDE_TOP_HEADER_ON_SCROLL: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "hide_top_header_on_scroll",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "hide_top_header",
            titleKey = "ss_hide_top_header_title",
            subtitleKey = "ss_hide_top_header_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("top header", "app bar", "home bar", "hide", "scroll", "auto hide", "collapse", "dock"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * Global one-time marker for the legacy flat-key → per-user-namespace
     * migration. Deliberately **global**, not per-user (it lives OUTSIDE the
     * namespace, never copied): the legacy flat values belonged to the one
     * user who used the install, so they are claimed exactly once — a
     * per-user marker would let every later user inherit them. Declared as a
     * boolean row so its wire name and reset participation live with
     * the rows; the store reads the raw typed slot (no legacy-string
     * fallback — the marker was always typed).
     */
    val HOME_NS_MIGRATED: PreferenceSpec<Boolean> = PreferenceSpec.boolean(
        keyName = "home_ns_migrated",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /**
     * Every row this store declares, in [HomeDiscoverySlice] property order
     * (with the version-stamp row beside its set row, the legacy
     * hidden-library migration source beside the overrides row it feeds, and
     * the global marker last). The reset derivation below and the
     * derivation-integrity test both iterate this — a row declared but
     * forgotten here falls out of reset coverage and is caught by the JVM
     * reset-coverage guard.
     */
    val all: List<PreferenceSpec<*>> = listOf(
        HOME_MODE,
        HOME_HERO_ENABLED,
        HOME_BACKDROP_ENABLED,
        HOME_ENABLED_SECTION_TYPES,
        HOME_ENABLED_SECTION_TYPES_VERSION,
        HOME_SECTION_ORDER,
        HOME_LIBRARY_SECTION_OVERRIDES,
        HOME_HIDDEN_LIBRARY_SECTION_IDS,
        PINNED_HOME_SECTIONS,
        HOME_DISCOVER_ROWS,
        HOME_LAYOUT_PRESETS,
        CONTINUE_WATCHING_CLICK_BEHAVIOR,
        SHOW_UNWATCHED_BADGE,
        HIDE_WATCHED_ITEMS,
        SHOW_WATCHED_CHECKMARK,
        SHOW_EXTERNAL_RATINGS,
        MERGE_CONTINUE_WATCHING_NEXT_UP,
        NEXT_UP_MAX_DAYS,
        NEXT_UP_REWATCHING,
        CLASSIC_ROWS,
        NEXT_UP_EXCLUDED_SERIES_IDS,
        HIDDEN_CW_ITEM_IDS,
        LAST_VIEWED_SEASON_BY_SERIES,
        SHOW_CLOCK_ON_HOME,
        SHOW_SETTINGS_IN_HOME_SEARCH,
        HIDE_TOP_HEADER_ON_SCROLL,
        HOME_NS_MIGRATED,
    )

    /**
     * Every canonical key this store owns, in its legacy flat form — derived
     * as every row except the global [HOME_NS_MIGRATED] marker (which lives
     * outside any namespace). Doubles as (a) the canonical-suffix set the
     * dynamic factory-reset strip matches against and (b) the legacy/
     * canonical layer that factory reset must also strip. Includes the legacy
     * [HOME_HIDDEN_LIBRARY_SECTION_IDS] migration source.
     */
    val legacyRows: List<PreferenceSpec<*>> = all.filterNot { it === HOME_NS_MIGRATED }

    val legacyKeys: List<Preferences.Key<*>> = legacyRows.map { it.typedKey() }

    /**
     * The rows the one-time FIRST-USER-CLAIMS namespace migration copies into
     * the active user's namespace — every [legacyRows] entry except the
     * [HOME_HIDDEN_LIBRARY_SECTION_IDS] migration source (which the
     * migration's conversion fold consumes and drops; it is never a copy
     * destination).
     */
    val migrationRows: List<PreferenceSpec<*>> =
        legacyRows.filterNot { it === HOME_HIDDEN_LIBRARY_SECTION_IDS }

    /**
     * The per-type copy lists the namespace migration iterates, derived from
     * [migrationRows]' declared storage (the store's `copyIntoNamespace`
     * walks each list with the matching typed parse — booleans via
     * `toBoolean`, ints via `toIntOrNull`, strings verbatim — exactly as the
     * hand-written predecessor did). The STRING list is typed
     * `PreferenceSpec<String>` because every row on it persists a string
     * slot (the enum rows store the constant name); only the rows' canonical
     * keys are read off it.
     */
    @Suppress("UNCHECKED_CAST")
    val booleanCopyRows: List<PreferenceSpec<Boolean>> =
        migrationRows.filter { it.storage == PreferenceStorage.BOOLEAN } as List<PreferenceSpec<Boolean>>

    @Suppress("UNCHECKED_CAST")
    val intCopyRows: List<PreferenceSpec<Int>> =
        migrationRows.filter { it.storage == PreferenceStorage.INT } as List<PreferenceSpec<Int>>

    @Suppress("UNCHECKED_CAST")
    val stringCopyRows: List<PreferenceSpec<String>> =
        migrationRows.filter { it.storage == PreferenceStorage.STRING } as List<PreferenceSpec<String>>

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key owned here
     * sits in the single `HOME_DISCOVERY` bucket. The facade aggregates these
     * lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, in the settings feature's
     * catalog order (the order the retired hand records carried): every row
     * above that carries a [PreferenceSearchSpec]. The settings feature
     * derives its spec-backed catalog rows from this list over its ordered
     * record spine — the home groups (display / next-up / layout / cards).
     */
    val searchEntries: List<PreferenceSearchSpec> = listOf(
        HOME_MODE.searchEntry(),
        HOME_HERO_ENABLED.searchEntry(),
        HOME_BACKDROP_ENABLED.searchEntry(),
        SHOW_CLOCK_ON_HOME.searchEntry(),
        HIDE_TOP_HEADER_ON_SCROLL.searchEntry(),
        SHOW_SETTINGS_IN_HOME_SEARCH.searchEntry(),
        CONTINUE_WATCHING_CLICK_BEHAVIOR.searchEntry(),
        CLASSIC_ROWS.searchEntry(),
        NEXT_UP_EXCLUDED_SERIES_IDS.searchEntry(),
        HIDDEN_CW_ITEM_IDS.searchEntry(),
        MERGE_CONTINUE_WATCHING_NEXT_UP.searchEntry(),
        NEXT_UP_MAX_DAYS.searchEntry(),
        NEXT_UP_REWATCHING.searchEntry(),
        PINNED_HOME_SECTIONS.searchEntry(),
        HOME_LAYOUT_PRESETS.searchEntry(),
        HOME_LIBRARY_SECTION_OVERRIDES.searchEntry(),
        HOME_DISCOVER_ROWS.searchEntry(),
        SHOW_UNWATCHED_BADGE.searchEntry(),
        SHOW_WATCHED_CHECKMARK.searchEntry(),
        HIDE_WATCHED_ITEMS.searchEntry(),
        SHOW_EXTERNAL_RATINGS.searchEntry(),
    )

    /** The projection hook for [searchEntries]: a row's declared search entry. */
    private fun PreferenceSpec<*>.searchEntry(): PreferenceSearchSpec =
        requireNotNull(search) { "row '$keyName' declares no search entry" }
}

// ----------------------------------------------------------------------
// Per-user key-rebuild hook: `u_<userId>::<keyName>` rebuilt from the ONE
// declared wire name plus the row's declared storage. These are the
// home-domain spellings of [PreferenceSpec.typedKeyNamed] — a namespaced
// slot is always the canonical row transformed by [UserNamespacedKeys],
// never a second key literal.
// ----------------------------------------------------------------------

/**
 * The row's typed slot rebuilt under [userId]'s namespace — plain
 * boolean/int rows read and write through this (the row's storage decides
 * the key factory; `Preferences.Key` equality is name-based, so the rebuilt
 * key interoperates with any hand-built key of the same name).
 */
internal fun <T> PreferenceSpec<T>.userKey(userId: String): Preferences.Key<T> =
    typedKeyNamed(UserNamespacedKeys.name(userId, keyName))

/**
 * The row's raw STRING slot rebuilt under [userId]'s namespace — the JSON /
 * enum rows' wire slot (enum rows persist the constant name, JSON rows the
 * encoded blob; both are strings on disk regardless of the row's value
 * type).
 */
internal fun PreferenceSpec<*>.rawUserKey(userId: String): Preferences.Key<String> =
    stringPreferencesKey(UserNamespacedKeys.name(userId, keyName))

/**
 * The row's boolean value under [userId]'s namespace, read through the
 * shared legacy-string fallback ([PreferenceCodec.readBool]) — the fallback
 * consults the NAMESPACED name (the pre-typed-era wire name of the
 * namespaced slot IS the namespaced name), keeping the fallback and the
 * typed key on the same single-declared wire name.
 */
internal fun PreferenceSpec<Boolean>.readBoolForUser(prefs: Preferences, userId: String): Boolean {
    val key = userKey(userId)
    return PreferenceCodec.readBool(prefs, key, key.name, default)
}

/**
 * The row's int value under [userId]'s namespace — same
 * legacy-string-fallback dance as [readBoolForUser], ints via
 * [PreferenceCodec.readInt].
 */
internal fun PreferenceSpec<Int>.readIntForUser(prefs: Preferences, userId: String): Int {
    val key = userKey(userId)
    return PreferenceCodec.readInt(prefs, key, key.name, default)
}

/**
 * The row's enum-by-name value under [userId]'s namespace: the stored
 * constant name parsed against [E], the declared default served on absence
 * or corruption (the repo-wide [toEnumOrNull] parse seam — the same encoding
 * the canonical [PreferenceSpec.enumRow] rows read with).
 */
internal inline fun <reified E : Enum<E>> PreferenceSpec<E>.readEnumForUser(
    prefs: Preferences,
    userId: String,
): E = prefs[rawUserKey(userId)].toEnumOrNull() ?: default
