package com.raulshma.jellyplay.core.datastore.home

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceStorage
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeCardScale
import com.raulshma.jellyplay.core.model.HomeCwProgressStyle
import com.raulshma.jellyplay.core.model.HomeDiscoverPattern
import com.raulshma.jellyplay.core.model.HomeHeroStyle
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeRefreshCadence
import com.raulshma.jellyplay.core.model.NewsletterBannerWindow
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
 * each row's typed key from its wire name, the per-user read projection and
 * `restore` FOLD the slice rows below (each slice-bound row carries its
 * namespaced read, its inverse namespaced write and its [HomeDiscoverySlice]
 * lens — the namespaced variant of the videoplayer derivation), the single-key
 * setters write through the rows' namespace-rebuilt keys, and `resetKeysFor`
 * filters the rows by reset category — so a row cannot drift from the
 * machinery that reads or writes it.
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
 * namespaced slot) through the same rows.
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
 * slice row in [sliceRows] (the namespaced read, the restore write and the
 * slice lens — the three bindings the store's folds iterate), and its setter —
 * plus one write-through test line; the derivation covers key identity,
 * encodings, the read/restore folds, reset lists and the migration copy
 * lists, not the slice plumbing or its coverage.
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
     *
     * v3 is the deliberate exception: UPCOMING and MY_MEDIA ship as
     * [HomeSectionType.CONFIGURABLE] default-set members (fresh installs see
     * them) but must NOT appear unasked on an upgrading install — so there
     * are no shipped-at entries at 3. Instead the store's one-time v3
     * upgrade baseline ([HOME_V3_UPGRADE_BASELINE_DONE]) materializes the
     * default set minus [V3_UPGRADE_EXCLUDED_SECTIONS] for a pre-v0.11.5
     * install whose user never persisted a set, and sets predating v3 simply
     * stay verbatim (nothing unions the pair in). A future section that
     * should default visible everywhere appends its shipped-at entry and
     * bumps CURRENT as before.
     */
    const val HOME_ENABLED_SECTION_TYPES_CURRENT_VERSION = 3

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

    /**
     * The sections an UPGRADING install's baselined enabled-set omits —
     * the v3 pair, UPCOMING and MY_MEDIA. They ship as CONFIGURABLE
     * default-set members (a fresh install's absent set serves the default
     * and shows both), but a home screen that predates them must not grow
     * two new rows unasked: the store's one-time v3 upgrade baseline
     * writes `HOME_ENABLED_SECTION_TYPES.default - this` as the first
     * active user's persisted set when the install predates v0.11.5. A
     * user who then enables either keeps that choice — every later write
     * is verbatim.
     */
    val V3_UPGRADE_EXCLUDED_SECTIONS: Set<HomeSectionType> =
        setOf(HomeSectionType.UPCOMING, HomeSectionType.MY_MEDIA)

    // ------------------------------------------------------------------
    // Settings-search route kinds (the plain ids the feature layer maps to
    // its Routes) + the standalone entries + searchEntries list live at the
    // bottom; the searchable rows carry their [PreferenceSearchSpec] inline.
    // ------------------------------------------------------------------

    /** Route kind: rows that deep-link into the home settings screen. */
    const val ROUTE_HOME_SETTINGS = "home_settings"

    /** Route kind: the "Hidden from Next Up" management screen. */
    /** Route kind: the Next Up hidden-series drill-in. */
    const val ROUTE_NEXT_UP_EXCLUDED = "next_up_excluded"

    /** Route kind: the My Media hidden-libraries drill-in. */
    const val ROUTE_MY_MEDIA_HIDDEN = "my_media_hidden"

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

    /** The hero block's presentation: paged carousel, auto-advancing slideshow, spotlight card or TV slider. */
    val HOME_HERO_STYLE: PreferenceSpec<HomeHeroStyle> = PreferenceSpec.enumRow(
        keyName = "home_hero_style",
        default = HomeHeroStyle.CAROUSEL,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_hero_style",
            titleKey = "ss_home_hero_style_title",
            subtitleKey = "ss_home_hero_style_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hero", "style", "carousel", "slideshow", "detailed", "slider", "layout", "banner"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The [HomeHeroStyle.SLIDESHOW] auto-advance pacing in seconds. */
    val HOME_HERO_SLIDESHOW_INTERVAL_SEC: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_hero_slideshow_interval_sec",
        default = 8,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 3,
        max = 60,
        search = PreferenceSearchSpec(
            id = "home_hero_slideshow_interval",
            titleKey = "ss_home_hero_slideshow_interval_title",
            subtitleKey = "ss_home_hero_slideshow_interval_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("slideshow", "interval", "seconds", "hero", "timing", "rotate", "advance", "speed"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The clamp every [HOME_HERO_SLIDESHOW_INTERVAL_SEC] read/write shares. */
    val HOME_HERO_SLIDESHOW_INTERVAL_SEC_BOUNDS: ClosedRange<Int> = 3..60

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

    /**
     * Video hero billboard (touch/default form factors): a muted looping
     * clip (ThemeVideo → trailer-embed fallback) in the hero on dwell. Default
     * off — the static artwork hero stays the shipped behavior. The TV form
     * factor has its own switch ([HOME_HERO_VIDEO_TV_ENABLED]).
     */
    val HOME_HERO_VIDEO_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_hero_video_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_hero_video",
            titleKey = "settings_home_hero_video",
            subtitleKey = "settings_home_hero_video_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hero", "video", "trailer", "theme", "ambient", "motion", "billboard", "clip"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The TV form-factor twin — same clip pipeline, separate switch. */
    val HOME_HERO_VIDEO_TV_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_hero_video_tv_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_hero_video_tv",
            titleKey = "settings_home_hero_video_tv",
            subtitleKey = "settings_home_hero_video_tv_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hero", "video", "trailer", "tv", "android tv", "ambient", "motion", "billboard"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * Focus-driven ambient backdrop (Android TV): the focused card
     * repaints the home ambient backdrop from its BlurHash/backdrop. Default
     * off — the backdrop stays hero-driven.
     */
    val HOME_FOCUS_BACKDROP: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_focus_backdrop",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_focus_backdrop",
            titleKey = "settings_home_focus_backdrop",
            subtitleKey = "settings_home_focus_backdrop_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("focus", "backdrop", "ambient", "immersive", "tv", "android tv", "blur", "background"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** Personal stats card ("Your library week") between hero and the first row. */
    val HOME_STATS_CARD: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_stats_card",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_stats_card",
            titleKey = "settings_home_stats_card",
            subtitleKey = "settings_home_stats_card_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("stats", "statistics", "week", "watch", "played", "library", "card"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** Genre quick-access rail (chips above the first row). */
    val HOME_GENRE_RAIL: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_genre_rail",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_genre_rail",
            titleKey = "settings_home_genre_rail",
            subtitleKey = "settings_home_genre_rail_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("genre", "chips", "rail", "browse", "categories", "quick access"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** Watch-party / presence chips ("Alex is watching Dune — join"). Admin-gated surface. */
    val HOME_PRESENCE_CHIPS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_presence_chips",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_presence_chips",
            titleKey = "settings_home_presence_chips",
            subtitleKey = "settings_home_presence_chips_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("presence", "watch party", "friends", "sessions", "join", "watching", "now playing"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * New-section announcement toast: when a version bump auto-enables a
     * section, say so once instead of silently. Default on — the announcement
     * IS the discoverability fix; the stamped
     * [HOME_ANNOUNCED_VERSION] makes it once per version bump.
     */
    val HOME_NEW_SECTION_ANNOUNCEMENTS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_new_section_announcements",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_new_section_announcements",
            titleKey = "settings_new_section_announcements",
            subtitleKey = "settings_new_section_announcements_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("new", "section", "announcement", "toast", "update", "customize", "notification"),
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

    /**
     * Server-persisted home layout: after any layout-writing command,
     * push the layout to the server's DisplayPreferences (debounced). Default
     * off — the MANUAL sync policy (explicit upload/restore actions) is the
     * shipped behavior; no auto-pull exists either way.
     */
    val HOME_LAYOUT_AUTOPUSH: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_layout_autopush",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_layout_autopush",
            titleKey = "settings_home_layout_autopush",
            subtitleKey = "settings_home_layout_autopush_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("layout", "sync", "server", "display preferences", "upload", "push", "cross-device", "roam"),
            routeKind = ROUTE_HOME_SETTINGS,
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
    // Card-display expansion: quality/new badges, TV
    // trickplay preview and the anti-spoiler master switch. Consumed through
    // the CardDisplayPreferences composition local (provisioned from the
    // HomeDiscoverySlice beside the quartet above); their rows live in the
    // home hub's Cards group.
    // ------------------------------------------------------------------

    /** Quality badges (4K / HDR / DV / Atmos) on poster + wide cards. */
    val SHOW_QUALITY_BADGES: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_quality_badges",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "show_quality_badges",
            titleKey = "settings_show_quality_badges",
            subtitleKey = "settings_show_quality_badges_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("quality", "4k", "hdr", "dolby", "vision", "atmos", "badge", "resolution", "codec"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** "NEW" badges for items added since the last home visit. */
    val SHOW_NEW_BADGES: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_new_badges",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "show_new_badges",
            titleKey = "settings_show_new_badges",
            subtitleKey = "settings_show_new_badges_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("new", "badge", "dot", "recently", "added", "indicator", "marker"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * Trickplay preview on wide (resume/next-up) cards — TV-only v1: the
     * focused card shows the trickplay tile at the resume position. Default
     * on (a pure enhancement); the row is hidden on non-TV form factors (the
     * [PreferencePlatformRule.ANDROID_ONLY] tag keeps the search hit off
     * desktop, the row's TV gate hides it on touch) and the behavior hard-gates
     * on performance mode.
     */
    val HOME_TRICKPLAY_PREVIEW: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_trickplay_preview",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_trickplay_preview",
            titleKey = "settings_home_trickplay_preview",
            subtitleKey = "settings_home_trickplay_preview_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("trickplay", "preview", "hover", "focus", "scrub", "thumbnails", "chapter"),
            routeKind = ROUTE_HOME_SETTINGS,
            platformRule = PreferencePlatformRule.ANDROID_ONLY,
        ),
    )

    /**
     * Anti-spoiler home mode — the one master switch that hides
     * watch-state spoilers across home (progress bars, "Xm left", watched
     * checkmarks/unwatched counts, episode titles on resume cards). Default
     * off; when ON, the watched-checkmark/unwatched-badge knobs above are
     * force-suppressed at the render sites regardless of their own settings.
     */
    val ANTI_SPOILER_HOME: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "anti_spoiler_home",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "anti_spoiler_home",
            titleKey = "settings_anti_spoiler_home",
            subtitleKey = "settings_anti_spoiler_home_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("spoiler", "progress", "watched"),
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
        min = 0,
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
     * Classic (pre-Jellyfin-12) home-row semantics: Continue Watching
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

    /**
     * Active-session-aware Continue Watching: don't offer "continue" for an
     * item currently playing on another device (server
     * `excludeActiveSessions` resume-query param). Default false — current
     * behavior preserved.
     */
    val EXCLUDE_ACTIVE_SESSIONS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_cw_exclude_active_sessions",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_cw_exclude_active_sessions",
            titleKey = "settings_exclude_active_sessions",
            subtitleKey = "settings_exclude_active_sessions_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("continue watching", "next up", "active", "session", "playing", "device", "resume", "currently"),
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
    // Home internals + inline-curation slices. The internal stamps are
    // plain rows with no search entry (never surfaced); the two slices are
    // JSON string sets with no settings row yet — their management UI ships
    // with the features that consume them (the home config sheet, the
    // detail-screen mute chip), so the specs stay faceless.
    // ------------------------------------------------------------------

    /**
     * Visit memory: when the user last saw home (epoch ms, stamped by the
     * home screen's dwell timer). Items added after this render the NEW
     * badge. Internal bookkeeping — no settings row.
     */
    val HOME_LAST_VISIT_EPOCH_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "home_last_visit_epoch_ms",
        default = 0L,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /**
     * Announce-once stamp: the enabled-set schema version whose
     * new-section announcement was last shown (or dismissed). Internal
     * bookkeeping — no settings row.
     */
    val HOME_ANNOUNCED_VERSION: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_announced_version",
        default = 0,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /**
     * "My Media" inline curation: libraries hidden from the My Media tiles
     * row (long-press a tile → hide). Stored as a JSON string-id set; managed
     * inline plus the section config sheet, and restored through the
     * hidden-libraries drill-in (the settings-search surface).
     */
    val MY_MEDIA_HIDDEN_LIBRARY_IDS: PreferenceSpec<Set<String>> = PreferenceSpec.custom(
        keyName = "my_media_hidden_library_ids",
        default = emptySet(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "hidden_libraries",
            titleKey = "ss_hidden_libraries_title",
            subtitleKey = "ss_hidden_libraries_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("my media", "hidden", "library", "tiles", "restore", "show", "unhide"),
            routeKind = ROUTE_MY_MEDIA_HIDDEN,
        ),
    )

    /**
     * Per-series theme-music memory: series whose ambient theme music the
     * user muted on the detail screen ("never for this series"). Stored as a
     * JSON string-id set that DetailPreferences projects into its mute chip.
     */
    val THEME_MUSIC_MUTED_SERIES_IDS: PreferenceSpec<Set<String>> = PreferenceSpec.custom(
        keyName = "theme_music_muted_series_ids",
        default = emptySet(),
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

    // ------------------------------------------------------------------
    // Home display QoL (greeting, see-all, card chrome, clock, dock, banner)
    // ------------------------------------------------------------------

    val HOME_GREETING_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_greeting_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_greeting",
            titleKey = "ss_home_greeting_title",
            subtitleKey = "ss_home_greeting_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("greeting", "hello", "good morning", "welcome", "user name", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_HIDE_SEE_ALL: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_hide_see_all",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_hide_see_all",
            titleKey = "ss_home_hide_see_all_title",
            subtitleKey = "ss_home_hide_see_all_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("see all", "pill", "row header", "hide", "minimal", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_CARD_SCALE: PreferenceSpec<HomeCardScale> = PreferenceSpec.enumRow(
        keyName = "home_card_scale",
        default = HomeCardScale.DEFAULT,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_card_scale",
            titleKey = "ss_home_card_scale_title",
            subtitleKey = "ss_home_card_scale_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("card", "poster", "size", "scale", "width", "rows", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_CARD_CORNER_RADIUS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_card_corner_radius",
        default = 12,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 0,
        max = 28,
        search = PreferenceSearchSpec(
            id = "home_card_corner_radius",
            titleKey = "ss_home_card_corner_radius_title",
            subtitleKey = "ss_home_card_corner_radius_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("corner", "radius", "rounded", "cards", "shape", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_SHOW_MODE_SWITCH: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_show_mode_switch",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_show_mode_switch",
            titleKey = "ss_home_show_mode_switch_title",
            subtitleKey = "ss_home_show_mode_switch_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("mode", "switch", "video", "music", "toggle", "dock", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_CLOCK_SHOW_DATE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_clock_show_date",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_clock_show_date",
            titleKey = "ss_home_clock_show_date_title",
            subtitleKey = "ss_home_clock_show_date_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("clock", "date", "calendar", "day", "home", "header"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_NEWSLETTER_BANNER_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_newsletter_banner_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_newsletter_banner",
            titleKey = "ss_home_newsletter_banner_title",
            subtitleKey = "ss_home_newsletter_banner_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("newsletter", "banner", "digest", "hide", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_ALWAYS_SHOW_USER_SWITCHER: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_always_show_user_switcher",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_always_show_user_switcher",
            titleKey = "ss_home_always_show_user_switcher_title",
            subtitleKey = "ss_home_always_show_user_switcher_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("user", "switcher", "avatar", "profile", "accounts", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_CW_PROGRESS_STYLE: PreferenceSpec<HomeCwProgressStyle> = PreferenceSpec.enumRow(
        keyName = "home_cw_progress_style",
        default = HomeCwProgressStyle.DEFAULT,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_cw_progress_style",
            titleKey = "ss_home_cw_progress_style_title",
            subtitleKey = "ss_home_cw_progress_style_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("progress", "bar", "percent", "continue watching", "resume", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_PRESENCE_CHIPS_LIMIT: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_presence_chips_limit",
        default = 4,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 2,
        max = 8,
        search = PreferenceSearchSpec(
            id = "home_presence_chips_limit",
            titleKey = "ss_home_presence_chips_limit_title",
            subtitleKey = "ss_home_presence_chips_limit_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("presence", "chips", "watch party", "sessions", "limit", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_GENRE_RAIL_LIMIT: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_genre_rail_limit",
        default = 24,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 6,
        max = 36,
        search = PreferenceSearchSpec(
            id = "home_genre_rail_limit",
            titleKey = "ss_home_genre_rail_limit_title",
            subtitleKey = "ss_home_genre_rail_limit_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("genre", "rail", "chips", "categories", "limit", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_ASK_CONTINUE_REMEMBER: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_ask_continue_remember",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_ask_continue_remember",
            titleKey = "ss_home_ask_continue_remember_title",
            subtitleKey = "ss_home_ask_continue_remember_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("ask", "continue watching", "remember", "choice", "tap behavior", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_ROWS_ANIMATIONS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_rows_animations",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_rows_animations",
            titleKey = "ss_home_rows_animations_title",
            subtitleKey = "ss_home_rows_animations_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("entrance", "animation", "fade", "sections", "rows", "motion", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    val HOME_OPAQUE_DOCK: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_opaque_dock",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_opaque_dock",
            titleKey = "ss_home_opaque_dock_title",
            subtitleKey = "ss_home_opaque_dock_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("dock", "opaque", "translucent", "header", "readability", "capsule", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    // ------------------------------------------------------------------
    // Home QoL wave 3 (orderable pre-section items, badge/refresh/hero/
    // discover/newsletter/presence/trickplay/search/tiles/stats knobs).
    // The CSV rows' canonical token sets + shared normalizers are declared
    // FIRST (object initialization is declaration order — the rows below
    // read the canonical sets as their defaults). The slice rows' reads and
    // the store's setters both route through the normalizers, so a persisted
    // value and a freshly-set one normalize identically.
    // ------------------------------------------------------------------

    /** The pre-section block's canonical item order — the shipped render order. */
    val HOME_PRE_SECTION_ORDER_CANONICAL: List<String> =
        listOf("stats_card", "genre_rail", "newsletter_banner")

    /** The sections the NEW badge renders on, as shipped — the arrival trio. */
    val HOME_NEW_BADGE_SECTIONS_CANONICAL: List<HomeSectionType> =
        listOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED, HomeSectionType.UPCOMING)

    /** The stats card's metrics, as shipped — all three visible. */
    val HOME_STATS_METRICS_CANONICAL: List<String> =
        listOf("played", "unplayed", "minutes")

    /**
     * The [HOME_PRE_SECTION_ORDER] CSV's normalization: lowercase, split,
     * trim, dedupe, drop unknown tokens, then append any canonical items the
     * list was missing in canonical order. An empty result (no known token)
     * degrades to the canonical order.
     */
    internal fun normalizePreSectionOrder(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return HOME_PRE_SECTION_ORDER_CANONICAL
        val known = raw.split(',').mapNotNull { token ->
            HOME_PRE_SECTION_ORDER_CANONICAL.find { it == token.trim().lowercase() }
        }.distinct()
        return if (known.isEmpty()) {
            HOME_PRE_SECTION_ORDER_CANONICAL
        } else {
            known + HOME_PRE_SECTION_ORDER_CANONICAL.filterNot { it in known }
        }
    }

    /**
     * The [HOME_NEW_BADGE_SECTIONS] CSV's normalization: split, trim, drop
     * names that are not [HomeSectionType] constants, dedupe. A null/absent
     * slot serves the canonical arrival trio; an explicitly empty CSV badges
     * nothing (the "Nowhere" preset).
     */
    internal fun normalizeNewBadgeSections(raw: String?): Set<HomeSectionType> {
        if (raw == null) return HOME_NEW_BADGE_SECTIONS_CANONICAL.toSet()
        return raw.split(',').mapNotNull { token ->
            HomeSectionType.entries.find { it.name == token.trim().uppercase() }
        }.toSet()
    }

    /**
     * The [HOME_STATS_METRICS] CSV's normalization: lowercase, split, trim,
     * dedupe, drop unknown tokens; an empty result degrades to the canonical
     * all-three set.
     */
    internal fun normalizeStatsMetrics(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return HOME_STATS_METRICS_CANONICAL.toSet()
        val known = raw.split(',').mapNotNull { token ->
            HOME_STATS_METRICS_CANONICAL.find { it == token.trim().lowercase() }
        }.toSet()
        return if (known.isEmpty()) HOME_STATS_METRICS_CANONICAL.toSet() else known
    }

    /**
     * The pre-section block's user order as a lowercase token CSV — the
     * orderable items between the hero and the first section row (stats
     * card / genre rail / newsletter banner; the presence chips and the
     * system banners keep their fixed slots). The read and the setter share
     * [normalizePreSectionOrder]: lowercase, dedupe, drop unknown tokens,
     * append the missing ones in canonical order. Declared as a `custom`
     * row over the parsed token list (the JSON-blob precedent — the wire
     * slot is the CSV string; the row's value type is what the slice
     * carries).
     */
    val HOME_PRE_SECTION_ORDER: PreferenceSpec<List<String>> = PreferenceSpec.custom(
        keyName = "home_pre_section_order",
        default = HOME_PRE_SECTION_ORDER_CANONICAL,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_pre_section_order",
            titleKey = "ss_home_pre_section_order_title",
            subtitleKey = "ss_home_pre_section_order_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("pre-section", "order", "stats", "genre", "newsletter", "banner", "reorder", "arrange", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * The sections whose cards render the NEW badge — the hardcoded arrival
     * trio opened up to a per-section gate. The read and the setter share
     * [normalizeNewBadgeSections]: drop unknown names, dedupe, absent slot →
     * canonical default (an explicit empty set badges nothing). Declared as
     * a `custom` row over the parsed section set — the wire slot is the CSV
     * string, the row's value type is what the slice carries.
     */
    val HOME_NEW_BADGE_SECTIONS: PreferenceSpec<Set<HomeSectionType>> = PreferenceSpec.custom(
        keyName = "home_new_badge_sections",
        default = HOME_NEW_BADGE_SECTIONS_CANONICAL.toSet(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_new_badge_sections",
            titleKey = "ss_home_new_badge_sections_title",
            subtitleKey = "ss_home_new_badge_sections_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("new", "badge", "sections", "latest", "recently", "added", "upcoming", "gate", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The home feed's automatic refresh cadence (the periodic re-fetch loop). */
    val HOME_REFRESH_CADENCE: PreferenceSpec<HomeRefreshCadence> = PreferenceSpec.enumRow(
        keyName = "home_refresh_cadence",
        default = HomeRefreshCadence.DEFAULT,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_refresh_cadence",
            titleKey = "ss_home_refresh_cadence_title",
            subtitleKey = "ss_home_refresh_cadence_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("refresh", "cadence", "interval", "automatic", "eager", "relaxed", "manual", "pull to refresh", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * The hero carousel's idle auto-rotation dwell in milliseconds
     * (3000–30000). The slideshow style's own pacing
     * ([HOME_HERO_SLIDESHOW_INTERVAL_SEC]) takes precedence while that style
     * is selected.
     */
    val HOME_HERO_IDLE_ROTATION_MS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_hero_idle_rotation_ms",
        default = 8000,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 3000,
        max = 30000,
        search = PreferenceSearchSpec(
            id = "home_hero_idle_rotation_ms",
            titleKey = "ss_home_hero_idle_rotation_title",
            subtitleKey = "ss_home_hero_idle_rotation_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hero", "rotation", "idle", "dwell", "milliseconds", "advance", "carousel", "speed", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The clamp every [HOME_HERO_IDLE_ROTATION_MS] read/write shares. */
    val HOME_HERO_IDLE_ROTATION_MS_BOUNDS: ClosedRange<Int> = 3000..30000

    /**
     * Whether hero rotation defers while the home list is scrolling (the
     * shipped 2 s re-check window). Off = rotation ticks on its dwell even
     * mid-scroll.
     */
    val HERO_SCROLL_DEFER: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "hero_scroll_defer",
        default = true,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "hero_scroll_defer",
            titleKey = "ss_hero_scroll_defer_title",
            subtitleKey = "ss_hero_scroll_defer_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hero", "rotation", "scroll", "defer", "pause", "while scrolling", "carousel", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The discover rows' partition pattern (equal-size vs paced rhythms). */
    val HOME_DISCOVER_PATTERN: PreferenceSpec<HomeDiscoverPattern> = PreferenceSpec.enumRow(
        keyName = "home_discover_pattern",
        default = HomeDiscoverPattern.DEFAULT,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_discover_pattern",
            titleKey = "ss_home_discover_pattern_title",
            subtitleKey = "ss_home_discover_pattern_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("discover", "row", "pattern", "partition", "uniform", "compact", "layout", "cards", "seerr", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * The newsletter banner's weekday window, stacking on the
     * [HOME_NEWSLETTER_BANNER_ENABLED] on/off toggle: DEFAULT keeps the
     * plugin's weekday logic, ALWAYS shows the banner every day, WEEKENDS
     * restricts it to Saturday/Sunday.
     */
    val HOME_NEWSLETTER_WINDOW: PreferenceSpec<NewsletterBannerWindow> = PreferenceSpec.enumRow(
        keyName = "home_newsletter_window",
        default = NewsletterBannerWindow.DEFAULT,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_newsletter_window",
            titleKey = "ss_home_newsletter_window_title",
            subtitleKey = "ss_home_newsletter_window_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("newsletter", "banner", "window", "weekday", "weekend", "digest", "always", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The presence chips' foreground poll cadence in seconds (15–300). */
    val HOME_PRESENCE_POLL_SECONDS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_presence_poll_seconds",
        default = 60,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 15,
        max = 300,
        search = PreferenceSearchSpec(
            id = "home_presence_poll_seconds",
            titleKey = "ss_home_presence_poll_title",
            subtitleKey = "ss_home_presence_poll_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("presence", "poll", "seconds", "interval", "watch party", "sessions", "cadence", "refresh", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The clamp every [HOME_PRESENCE_POLL_SECONDS] read/write shares. */
    val HOME_PRESENCE_POLL_SECONDS_BOUNDS: ClosedRange<Int> = 15..300

    /**
     * How long the TV trickplay peek holds above the progress bar before
     * fading out, in milliseconds (1000–5000).
     */
    val HOME_TRICKPLAY_PEEK_MS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_trickplay_peek_ms",
        default = 2500,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 1000,
        max = 5000,
        search = PreferenceSearchSpec(
            id = "home_trickplay_peek_ms",
            titleKey = "ss_home_trickplay_peek_title",
            subtitleKey = "ss_home_trickplay_peek_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("trickplay", "peek", "hold", "preview", "thumbnail", "hover", "focus", "milliseconds", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The clamp every [HOME_TRICKPLAY_PEEK_MS] read/write shares. */
    val HOME_TRICKPLAY_PEEK_MS_BOUNDS: ClosedRange<Int> = 1000..5000

    /**
     * The home search overlay's results-pane height cap in dp (280–640).
     */
    val HOME_SEARCH_RESULTS_HEIGHT_DP: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_search_results_height_dp",
        default = 400,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 280,
        max = 640,
        search = PreferenceSearchSpec(
            id = "home_search_results_height",
            titleKey = "ss_home_search_results_height_title",
            subtitleKey = "ss_home_search_results_height_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("search", "results", "height", "overlay", "dropdown", "pane", "size", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The clamp every [HOME_SEARCH_RESULTS_HEIGHT_DP] read/write shares. */
    val HOME_SEARCH_RESULTS_HEIGHT_DP_BOUNDS: ClosedRange<Int> = 280..640

    /**
     * The My Media tiles row's visible-column count (0–6). 0 keeps the
     * shipped chassis width (the tiles row scrolls at its natural card
     * size); 1–6 size the tiles so exactly that many fit the screen width.
     */
    val HOME_MY_MEDIA_COLUMNS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_my_media_columns",
        default = 0,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 0,
        max = 6,
        search = PreferenceSearchSpec(
            id = "home_my_media_columns",
            titleKey = "ss_home_my_media_columns_title",
            subtitleKey = "ss_home_my_media_columns_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("my media", "tiles", "columns", "grid", "width", "libraries", "row", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The clamp every [HOME_MY_MEDIA_COLUMNS] read/write shares. */
    val HOME_MY_MEDIA_COLUMNS_BOUNDS: ClosedRange<Int> = 0..6

    /**
     * The stats card's visible metrics over played / unplayed / minutes —
     * unselected metrics hide from the card. The read and the setter share
     * [normalizeStatsMetrics]: drop unknown tokens, dedupe, empty → canonical
     * default (all three). Declared as a `custom` row over the parsed
     * metric set — the wire slot is the CSV string, the row's value type is
     * what the slice carries.
     */
    val HOME_STATS_METRICS: PreferenceSpec<Set<String>> = PreferenceSpec.custom(
        keyName = "home_stats_metrics",
        default = HOME_STATS_METRICS_CANONICAL.toSet(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_stats_metrics",
            titleKey = "ss_home_stats_metrics_title",
            subtitleKey = "ss_home_stats_metrics_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("stats", "card", "metrics", "played", "unplayed", "minutes", "watch time", "hide", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * The NEW-badge arrival window in days (1–30): arrivals older than the
     * cutoff stop badgeing even though they postdate a long-absent user's
     * last visit. 30 (the widest the clamp allows) is the closest the
     * clamp range offers to the shipped unbounded visit-based predicate.
     */
    val HOME_NEW_BADGE_DAYS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "home_new_badge_days",
        default = 30,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        min = 1,
        max = 30,
        search = PreferenceSearchSpec(
            id = "home_new_badge_days",
            titleKey = "ss_home_new_badge_days_title",
            subtitleKey = "ss_home_new_badge_days_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("new", "badge", "days", "window", "arrival", "threshold", "recently", "expire", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /** The clamp every [HOME_NEW_BADGE_DAYS] read/write shares. */
    val HOME_NEW_BADGE_DAYS_BOUNDS: ClosedRange<Int> = 1..30

    /**
     * Unwatched counters in the section-row headers: renders the row's
     * unwatched-item count beside the title. Purely derivable from the
     * loaded state (no new server queries) — only the arrival rows, whose
     * fetch projects play state, show a counter, and the count covers the
     * row's loaded items.
     */
    val HOME_ROW_UNWATCHED_COUNTERS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_row_unwatched_counters",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_row_unwatched_counters",
            titleKey = "ss_home_row_unwatched_counters_title",
            subtitleKey = "ss_home_row_unwatched_counters_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("unwatched", "counter", "count", "row", "header", "title", "badge", "home"),
            routeKind = ROUTE_HOME_SETTINGS,
        ),
    )

    /**
     * The hero starts at a random candidate instead of the first on each
     * home mount. Default off — the shipped first-item start.
     */
    val HOME_HERO_RANDOM_START: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "home_hero_random_start",
        default = false,
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        search = PreferenceSearchSpec(
            id = "home_hero_random_start",
            titleKey = "ss_home_hero_random_start_title",
            subtitleKey = "ss_home_hero_random_start_subtitle",
            categoryKey = "ss_cat_home",
            keywords = listOf("hero", "random", "start", "shuffle", "first", "carousel", "surprise", "home"),
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
     * One-time marker for the v3 upgrade baseline — the store fold that
     * classifies an install as predating v0.11.5 ([HOME_NS_MIGRATED] already
     * set, or any legacy flat home key still on disk) and materializes the
     * default enabled-set minus [V3_UPGRADE_EXCLUDED_SECTIONS] for the first
     * active user. Deliberately **global** like [HOME_NS_MIGRATED]: the
     * fresh-vs-updated question is per-INSTALL, not per-user, and a later
     * user activating on an updated install starts from the spec default
     * (first-user-claims semantics — the baseline claims for exactly the one
     * user who was active at upgrade time). Declared as a boolean row so its
     * wire name and reset participation live with the rows; lives OUTSIDE any
     * namespace, never a migration copy destination.
     */
    val HOME_V3_UPGRADE_BASELINE_DONE: PreferenceSpec<Boolean> = PreferenceSpec.boolean(
        keyName = "home_v3_upgrade_baseline_done",
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
        HOME_HERO_STYLE,
        HOME_HERO_SLIDESHOW_INTERVAL_SEC,
        HOME_HERO_VIDEO_ENABLED,
        HOME_HERO_VIDEO_TV_ENABLED,
        HOME_BACKDROP_ENABLED,
        HOME_FOCUS_BACKDROP,
        HOME_STATS_CARD,
        HOME_GENRE_RAIL,
        HOME_PRESENCE_CHIPS,
        HOME_NEW_SECTION_ANNOUNCEMENTS,
        HOME_ENABLED_SECTION_TYPES,
        HOME_ENABLED_SECTION_TYPES_VERSION,
        HOME_SECTION_ORDER,
        HOME_LIBRARY_SECTION_OVERRIDES,
        HOME_HIDDEN_LIBRARY_SECTION_IDS,
        PINNED_HOME_SECTIONS,
        HOME_DISCOVER_ROWS,
        HOME_LAYOUT_PRESETS,
        HOME_LAYOUT_AUTOPUSH,
        CONTINUE_WATCHING_CLICK_BEHAVIOR,
        SHOW_UNWATCHED_BADGE,
        HIDE_WATCHED_ITEMS,
        SHOW_WATCHED_CHECKMARK,
        SHOW_EXTERNAL_RATINGS,
        SHOW_QUALITY_BADGES,
        SHOW_NEW_BADGES,
        HOME_TRICKPLAY_PREVIEW,
        ANTI_SPOILER_HOME,
        MERGE_CONTINUE_WATCHING_NEXT_UP,
        NEXT_UP_MAX_DAYS,
        NEXT_UP_REWATCHING,
        CLASSIC_ROWS,
        EXCLUDE_ACTIVE_SESSIONS,
        NEXT_UP_EXCLUDED_SERIES_IDS,
        HIDDEN_CW_ITEM_IDS,
        LAST_VIEWED_SEASON_BY_SERIES,
        MY_MEDIA_HIDDEN_LIBRARY_IDS,
        THEME_MUSIC_MUTED_SERIES_IDS,
        HOME_LAST_VISIT_EPOCH_MS,
        HOME_ANNOUNCED_VERSION,
        SHOW_CLOCK_ON_HOME,
        SHOW_SETTINGS_IN_HOME_SEARCH,
        HIDE_TOP_HEADER_ON_SCROLL,
        HOME_GREETING_ENABLED,
        HOME_HIDE_SEE_ALL,
        HOME_CARD_SCALE,
        HOME_CARD_CORNER_RADIUS,
        HOME_SHOW_MODE_SWITCH,
        HOME_CLOCK_SHOW_DATE,
        HOME_NEWSLETTER_BANNER_ENABLED,
        HOME_ALWAYS_SHOW_USER_SWITCHER,
        HOME_CW_PROGRESS_STYLE,
        HOME_PRESENCE_CHIPS_LIMIT,
        HOME_GENRE_RAIL_LIMIT,
        HOME_ASK_CONTINUE_REMEMBER,
        HOME_ROWS_ANIMATIONS,
        HOME_OPAQUE_DOCK,
        HOME_PRE_SECTION_ORDER,
        HOME_NEW_BADGE_SECTIONS,
        HOME_REFRESH_CADENCE,
        HOME_HERO_IDLE_ROTATION_MS,
        HERO_SCROLL_DEFER,
        HOME_DISCOVER_PATTERN,
        HOME_NEWSLETTER_WINDOW,
        HOME_PRESENCE_POLL_SECONDS,
        HOME_TRICKPLAY_PEEK_MS,
        HOME_SEARCH_RESULTS_HEIGHT_DP,
        HOME_MY_MEDIA_COLUMNS,
        HOME_STATS_METRICS,
        HOME_NEW_BADGE_DAYS,
        HOME_ROW_UNWATCHED_COUNTERS,
        HOME_HERO_RANDOM_START,
        HOME_NS_MIGRATED,
        HOME_V3_UPGRADE_BASELINE_DONE,
    )

    /**
     * Every canonical key this store owns, in its legacy flat form — derived
     * as every row except the global markers ([HOME_NS_MIGRATED],
     * [HOME_V3_UPGRADE_BASELINE_DONE], which live OUTSIDE any namespace).
     * Doubles as (a) the canonical-suffix set the
     * dynamic factory-reset strip matches against and (b) the legacy/
     * canonical layer that factory reset must also strip. Includes the legacy
     * [HOME_HIDDEN_LIBRARY_SECTION_IDS] migration source.
     */
    val legacyRows: List<PreferenceSpec<*>> =
        all.filterNot { it === HOME_NS_MIGRATED || it === HOME_V3_UPGRADE_BASELINE_DONE }

    /**
     * The install-global bookkeeping markers' wire names — exactly the rows
     * [legacyRows] excludes, derived as that complement so a future global
     * marker joins automatically. [PreferenceSyncPolicy.EXCLUDED_BOOKKEEPING_KEYS]
     * consumes this: the markers persist under their raw canonical names
     * (outside every `u_<userId>::` namespace), so un-excluded they would
     * sync across devices and let one device's one-time fold claim, or skip,
     * another's.
     */
    val globalMarkerKeys: Set<String> =
        all.filterNot { it in legacyRows }.map { it.keyName }.toSet()

    val legacyKeys: List<Preferences.Key<*>> = legacyRows.map { it.typedKey() }

    /**
     * [legacyRows]' wire names as a set — the "is this key a legacy flat home
     * key" test the v3 upgrade baseline's classification walks DataStore's
     * raw key map against, precomputed once instead of per edit.
     */
    val legacyRowNames: Set<String> = legacyRows.map { it.keyName }.toSet()

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
     * `toBoolean`, ints via `toIntOrNull`, strings verbatim). The STRING list
     * is typed
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
     * The slice-bound rows — the ONE registration of the per-user read rows
     * and restore rows, in [all] order: every row whose value projects into a
     * [HomeDiscoverySlice] field, each carrying its namespaced read
     * ([HomeDiscoverySliceRow.readInto]), its inverse namespaced restore
     * write ([HomeDiscoverySliceRow.writeFrom]) and the slice lens. The
     * store's read projection and restore FOLD these (the namespaced variant
     * of the videoplayer derivation), so a slice field cannot drift the row
     * that reads or writes it. Excluded by design — each documented on its
     * row above:
     *  - [HOME_ENABLED_SECTION_TYPES] + [HOME_ENABLED_SECTION_TYPES_VERSION]:
     *    the version-union decode (and its restore-side version stamp) reads
     *    and writes BOTH keys as one fold, and feeds THREE slice fields (the
     *    set, its stored stamp, the union's contribution);
     *  - [HOME_HIDDEN_LIBRARY_SECTION_IDS]: the migration source, never a knob;
     *  - [HOME_NS_MIGRATED] / [HOME_V3_UPGRADE_BASELINE_DONE]: global
     *    markers, outside every namespace, never slice fields.
     *
     * The JVM `HomeDiscoverySpecDerivationTest` pins this list to exactly
     * `all` minus those exclusions, in `all` order — a row added to [all]
     * without a slice row (or vice versa) fails there.
     */
    internal val sliceRows: List<HomeDiscoverySliceRow<*>> = listOf(
        HomeDiscoverySliceRow(
            spec = HOME_MODE,
            get = { it.homeMode },
            set = { s, v -> s.copy(homeMode = v) },
            readForUser = { _, prefs, userId -> HOME_MODE.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_MODE.rawUserKey(userId)] = v.name },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HERO_ENABLED,
            get = { it.homeHeroEnabled },
            set = { s, v -> s.copy(homeHeroEnabled = v) },
            readForUser = { _, prefs, userId -> HOME_HERO_ENABLED.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_HERO_ENABLED.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HERO_STYLE,
            get = { it.homeHeroStyle },
            set = { s, v -> s.copy(homeHeroStyle = v) },
            readForUser = { _, prefs, userId -> HOME_HERO_STYLE.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_HERO_STYLE.rawUserKey(userId)] = v.name },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HERO_SLIDESHOW_INTERVAL_SEC,
            get = { it.homeHeroSlideshowIntervalSec },
            set = { s, v -> s.copy(homeHeroSlideshowIntervalSec = v) },
            readForUser = { _, prefs, userId -> HOME_HERO_SLIDESHOW_INTERVAL_SEC.readIntForUser(prefs, userId) },
            // The restore-side clamp (the setter keeps its own — each
            // remains the clamp owner at its site).
            writeForUser = { prefs, userId, v ->
                prefs[HOME_HERO_SLIDESHOW_INTERVAL_SEC.userKey(userId)] =
                    v.coerceIn(HOME_HERO_SLIDESHOW_INTERVAL_SEC_BOUNDS)
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HERO_VIDEO_ENABLED,
            get = { it.homeHeroVideoEnabled },
            set = { s, v -> s.copy(homeHeroVideoEnabled = v) },
            readForUser = { _, prefs, userId -> HOME_HERO_VIDEO_ENABLED.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_HERO_VIDEO_ENABLED.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HERO_VIDEO_TV_ENABLED,
            get = { it.homeHeroVideoTvEnabled },
            set = { s, v -> s.copy(homeHeroVideoTvEnabled = v) },
            readForUser = { _, prefs, userId -> HOME_HERO_VIDEO_TV_ENABLED.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_HERO_VIDEO_TV_ENABLED.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_BACKDROP_ENABLED,
            get = { it.homeBackdropEnabled },
            set = { s, v -> s.copy(homeBackdropEnabled = v) },
            readForUser = { _, prefs, userId -> HOME_BACKDROP_ENABLED.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_BACKDROP_ENABLED.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_FOCUS_BACKDROP,
            get = { it.homeFocusBackdrop },
            set = { s, v -> s.copy(homeFocusBackdrop = v) },
            readForUser = { _, prefs, userId -> HOME_FOCUS_BACKDROP.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_FOCUS_BACKDROP.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_STATS_CARD,
            get = { it.homeStatsCard },
            set = { s, v -> s.copy(homeStatsCard = v) },
            readForUser = { _, prefs, userId -> HOME_STATS_CARD.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_STATS_CARD.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_GENRE_RAIL,
            get = { it.homeGenreRail },
            set = { s, v -> s.copy(homeGenreRail = v) },
            readForUser = { _, prefs, userId -> HOME_GENRE_RAIL.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_GENRE_RAIL.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_PRESENCE_CHIPS,
            get = { it.homePresenceChips },
            set = { s, v -> s.copy(homePresenceChips = v) },
            readForUser = { _, prefs, userId -> HOME_PRESENCE_CHIPS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_PRESENCE_CHIPS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_NEW_SECTION_ANNOUNCEMENTS,
            get = { it.homeNewSectionAnnouncements },
            set = { s, v -> s.copy(homeNewSectionAnnouncements = v) },
            readForUser = { _, prefs, userId -> HOME_NEW_SECTION_ANNOUNCEMENTS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_NEW_SECTION_ANNOUNCEMENTS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_SECTION_ORDER,
            get = { it.homeSectionOrder },
            set = { s, v -> s.copy(homeSectionOrder = v) },
            readForUser = { reads, prefs, _ -> reads.readHomeSectionOrder(prefs) },
            // Verbatim name-list encode — restore does NOT normalize; that is
            // the setter's job.
            writeForUser = { prefs, userId, v ->
                prefs[HOME_SECTION_ORDER.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v.map { it.name })
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_LIBRARY_SECTION_OVERRIDES,
            get = { it.libraryHomeSectionOverrides },
            set = { s, v -> s.copy(libraryHomeSectionOverrides = v) },
            readForUser = { reads, prefs, _ -> reads.readLibraryHomeSectionOverrides(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[HOME_LIBRARY_SECTION_OVERRIDES.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = PINNED_HOME_SECTIONS,
            get = { it.pinnedHomeSections },
            set = { s, v -> s.copy(pinnedHomeSections = v) },
            readForUser = { reads, prefs, _ -> reads.readPinnedHomeSections(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[PINNED_HOME_SECTIONS.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_DISCOVER_ROWS,
            get = { it.discoverRows },
            set = { s, v -> s.copy(discoverRows = v) },
            readForUser = { reads, prefs, _ -> reads.readDiscoverRows(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[HOME_DISCOVER_ROWS.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_LAYOUT_PRESETS,
            get = { it.homeLayoutPresets },
            set = { s, v -> s.copy(homeLayoutPresets = v) },
            readForUser = { reads, prefs, _ -> reads.readHomeLayoutPresets(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[HOME_LAYOUT_PRESETS.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_LAYOUT_AUTOPUSH,
            get = { it.homeLayoutAutopush },
            set = { s, v -> s.copy(homeLayoutAutopush = v) },
            readForUser = { _, prefs, userId -> HOME_LAYOUT_AUTOPUSH.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_LAYOUT_AUTOPUSH.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = CONTINUE_WATCHING_CLICK_BEHAVIOR,
            get = { it.continueWatchingClickBehavior },
            set = { s, v -> s.copy(continueWatchingClickBehavior = v) },
            readForUser = { _, prefs, userId -> CONTINUE_WATCHING_CLICK_BEHAVIOR.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v ->
                prefs[CONTINUE_WATCHING_CLICK_BEHAVIOR.rawUserKey(userId)] = v.name
            },
        ),
        HomeDiscoverySliceRow(
            spec = SHOW_UNWATCHED_BADGE,
            get = { it.showUnwatchedBadge },
            set = { s, v -> s.copy(showUnwatchedBadge = v) },
            readForUser = { _, prefs, userId -> SHOW_UNWATCHED_BADGE.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[SHOW_UNWATCHED_BADGE.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HIDE_WATCHED_ITEMS,
            get = { it.hideWatchedItems },
            set = { s, v -> s.copy(hideWatchedItems = v) },
            readForUser = { _, prefs, userId -> HIDE_WATCHED_ITEMS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HIDE_WATCHED_ITEMS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = SHOW_WATCHED_CHECKMARK,
            get = { it.showWatchedCheckmark },
            set = { s, v -> s.copy(showWatchedCheckmark = v) },
            readForUser = { _, prefs, userId -> SHOW_WATCHED_CHECKMARK.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[SHOW_WATCHED_CHECKMARK.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = SHOW_EXTERNAL_RATINGS,
            get = { it.showExternalRatings },
            set = { s, v -> s.copy(showExternalRatings = v) },
            readForUser = { _, prefs, userId -> SHOW_EXTERNAL_RATINGS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[SHOW_EXTERNAL_RATINGS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = SHOW_QUALITY_BADGES,
            get = { it.showQualityBadges },
            set = { s, v -> s.copy(showQualityBadges = v) },
            readForUser = { _, prefs, userId -> SHOW_QUALITY_BADGES.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[SHOW_QUALITY_BADGES.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = SHOW_NEW_BADGES,
            get = { it.showNewBadges },
            set = { s, v -> s.copy(showNewBadges = v) },
            readForUser = { _, prefs, userId -> SHOW_NEW_BADGES.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[SHOW_NEW_BADGES.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_TRICKPLAY_PREVIEW,
            get = { it.homeTrickplayPreview },
            set = { s, v -> s.copy(homeTrickplayPreview = v) },
            readForUser = { _, prefs, userId -> HOME_TRICKPLAY_PREVIEW.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_TRICKPLAY_PREVIEW.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = ANTI_SPOILER_HOME,
            get = { it.antiSpoilerHome },
            set = { s, v -> s.copy(antiSpoilerHome = v) },
            readForUser = { _, prefs, userId -> ANTI_SPOILER_HOME.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[ANTI_SPOILER_HOME.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = MERGE_CONTINUE_WATCHING_NEXT_UP,
            get = { it.mergeContinueWatchingAndNextUp },
            set = { s, v -> s.copy(mergeContinueWatchingAndNextUp = v) },
            readForUser = { _, prefs, userId -> MERGE_CONTINUE_WATCHING_NEXT_UP.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[MERGE_CONTINUE_WATCHING_NEXT_UP.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = NEXT_UP_MAX_DAYS,
            get = { it.nextUpMaxDays },
            set = { s, v -> s.copy(nextUpMaxDays = v) },
            readForUser = { _, prefs, userId -> NEXT_UP_MAX_DAYS.readIntForUser(prefs, userId) },
            // Verbatim on restore (no clamp) — the setter owns the `0` floor.
            writeForUser = { prefs, userId, v -> prefs[NEXT_UP_MAX_DAYS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = NEXT_UP_REWATCHING,
            get = { it.nextUpRewatching },
            set = { s, v -> s.copy(nextUpRewatching = v) },
            readForUser = { _, prefs, userId -> NEXT_UP_REWATCHING.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[NEXT_UP_REWATCHING.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = CLASSIC_ROWS,
            get = { it.classicRows },
            set = { s, v -> s.copy(classicRows = v) },
            readForUser = { _, prefs, userId -> CLASSIC_ROWS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[CLASSIC_ROWS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = EXCLUDE_ACTIVE_SESSIONS,
            get = { it.excludeActiveSessions },
            set = { s, v -> s.copy(excludeActiveSessions = v) },
            readForUser = { _, prefs, userId -> EXCLUDE_ACTIVE_SESSIONS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[EXCLUDE_ACTIVE_SESSIONS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = NEXT_UP_EXCLUDED_SERIES_IDS,
            get = { it.nextUpExcludedSeriesIds },
            set = { s, v -> s.copy(nextUpExcludedSeriesIds = v) },
            readForUser = { reads, prefs, _ -> reads.readNextUpExcludedSeriesIds(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[NEXT_UP_EXCLUDED_SERIES_IDS.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = HIDDEN_CW_ITEM_IDS,
            get = { it.hiddenCwItemIds },
            set = { s, v -> s.copy(hiddenCwItemIds = v) },
            readForUser = { reads, prefs, _ -> reads.readHiddenCwItemIds(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[HIDDEN_CW_ITEM_IDS.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = LAST_VIEWED_SEASON_BY_SERIES,
            get = { it.lastViewedSeasonBySeries },
            set = { s, v -> s.copy(lastViewedSeasonBySeries = v) },
            readForUser = { reads, prefs, _ -> reads.readLastViewedSeasonBySeries(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[LAST_VIEWED_SEASON_BY_SERIES.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = MY_MEDIA_HIDDEN_LIBRARY_IDS,
            get = { it.myMediaHiddenLibraryIds },
            set = { s, v -> s.copy(myMediaHiddenLibraryIds = v) },
            readForUser = { reads, prefs, _ -> reads.readMyMediaHiddenLibraryIds(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[MY_MEDIA_HIDDEN_LIBRARY_IDS.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = THEME_MUSIC_MUTED_SERIES_IDS,
            get = { it.themeMusicMutedSeriesIds },
            set = { s, v -> s.copy(themeMusicMutedSeriesIds = v) },
            readForUser = { reads, prefs, _ -> reads.readThemeMusicMutedSeriesIds(prefs) },
            writeForUser = { prefs, userId, v ->
                prefs[THEME_MUSIC_MUTED_SERIES_IDS.rawUserKey(userId)] = PreferenceCodec.json.encodeToString(v)
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_LAST_VISIT_EPOCH_MS,
            get = { it.homeLastVisitEpochMs },
            set = { s, v -> s.copy(homeLastVisitEpochMs = v) },
            // Plain typed slot, no legacy-string fallback — a brand-new key
            // with no pre-typed-era value on disk (it rides no migration copy
            // list either).
            readForUser = { _, prefs, userId ->
                prefs[HOME_LAST_VISIT_EPOCH_MS.userKey(userId)] ?: HOME_LAST_VISIT_EPOCH_MS.default
            },
            writeForUser = { prefs, userId, v -> prefs[HOME_LAST_VISIT_EPOCH_MS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_ANNOUNCED_VERSION,
            get = { it.homeAnnouncedVersion },
            set = { s, v -> s.copy(homeAnnouncedVersion = v) },
            // Plain typed slot — same spelling as the visit stamp above.
            readForUser = { _, prefs, userId ->
                prefs[HOME_ANNOUNCED_VERSION.userKey(userId)] ?: HOME_ANNOUNCED_VERSION.default
            },
            writeForUser = { prefs, userId, v -> prefs[HOME_ANNOUNCED_VERSION.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = SHOW_CLOCK_ON_HOME,
            get = { it.showClockOnHome },
            set = { s, v -> s.copy(showClockOnHome = v) },
            readForUser = { _, prefs, userId -> SHOW_CLOCK_ON_HOME.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[SHOW_CLOCK_ON_HOME.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = SHOW_SETTINGS_IN_HOME_SEARCH,
            get = { it.showSettingsInHomeSearch },
            set = { s, v -> s.copy(showSettingsInHomeSearch = v) },
            readForUser = { _, prefs, userId -> SHOW_SETTINGS_IN_HOME_SEARCH.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[SHOW_SETTINGS_IN_HOME_SEARCH.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HIDE_TOP_HEADER_ON_SCROLL,
            get = { it.hideTopHeaderOnScroll },
            set = { s, v -> s.copy(hideTopHeaderOnScroll = v) },
            readForUser = { _, prefs, userId -> HIDE_TOP_HEADER_ON_SCROLL.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HIDE_TOP_HEADER_ON_SCROLL.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_GREETING_ENABLED,
            get = { it.homeGreetingEnabled },
            set = { s, v -> s.copy(homeGreetingEnabled = v) },
            readForUser = { _, prefs, userId -> HOME_GREETING_ENABLED.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_GREETING_ENABLED.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HIDE_SEE_ALL,
            get = { it.homeHideSeeAll },
            set = { s, v -> s.copy(homeHideSeeAll = v) },
            readForUser = { _, prefs, userId -> HOME_HIDE_SEE_ALL.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_HIDE_SEE_ALL.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_CARD_SCALE,
            get = { it.homeCardScale },
            set = { s, v -> s.copy(homeCardScale = v) },
            readForUser = { _, prefs, userId -> HOME_CARD_SCALE.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_CARD_SCALE.rawUserKey(userId)] = v.name },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_CARD_CORNER_RADIUS,
            get = { it.homeCardCornerRadius },
            set = { s, v -> s.copy(homeCardCornerRadius = v) },
            readForUser = { _, prefs, userId -> HOME_CARD_CORNER_RADIUS.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_CARD_CORNER_RADIUS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_SHOW_MODE_SWITCH,
            get = { it.homeShowModeSwitch },
            set = { s, v -> s.copy(homeShowModeSwitch = v) },
            readForUser = { _, prefs, userId -> HOME_SHOW_MODE_SWITCH.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_SHOW_MODE_SWITCH.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_CLOCK_SHOW_DATE,
            get = { it.homeClockShowDate },
            set = { s, v -> s.copy(homeClockShowDate = v) },
            readForUser = { _, prefs, userId -> HOME_CLOCK_SHOW_DATE.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_CLOCK_SHOW_DATE.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_NEWSLETTER_BANNER_ENABLED,
            get = { it.homeNewsletterBannerEnabled },
            set = { s, v -> s.copy(homeNewsletterBannerEnabled = v) },
            readForUser = { _, prefs, userId -> HOME_NEWSLETTER_BANNER_ENABLED.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_NEWSLETTER_BANNER_ENABLED.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_ALWAYS_SHOW_USER_SWITCHER,
            get = { it.homeAlwaysShowUserSwitcher },
            set = { s, v -> s.copy(homeAlwaysShowUserSwitcher = v) },
            readForUser = { _, prefs, userId -> HOME_ALWAYS_SHOW_USER_SWITCHER.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_ALWAYS_SHOW_USER_SWITCHER.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_CW_PROGRESS_STYLE,
            get = { it.homeCwProgressStyle },
            set = { s, v -> s.copy(homeCwProgressStyle = v) },
            readForUser = { _, prefs, userId -> HOME_CW_PROGRESS_STYLE.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_CW_PROGRESS_STYLE.rawUserKey(userId)] = v.name },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_PRESENCE_CHIPS_LIMIT,
            get = { it.homePresenceChipsLimit },
            set = { s, v -> s.copy(homePresenceChipsLimit = v) },
            readForUser = { _, prefs, userId -> HOME_PRESENCE_CHIPS_LIMIT.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_PRESENCE_CHIPS_LIMIT.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_GENRE_RAIL_LIMIT,
            get = { it.homeGenreRailLimit },
            set = { s, v -> s.copy(homeGenreRailLimit = v) },
            readForUser = { _, prefs, userId -> HOME_GENRE_RAIL_LIMIT.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_GENRE_RAIL_LIMIT.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_ASK_CONTINUE_REMEMBER,
            get = { it.homeAskContinueRemember },
            set = { s, v -> s.copy(homeAskContinueRemember = v) },
            readForUser = { _, prefs, userId -> HOME_ASK_CONTINUE_REMEMBER.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_ASK_CONTINUE_REMEMBER.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_ROWS_ANIMATIONS,
            get = { it.homeRowsAnimations },
            set = { s, v -> s.copy(homeRowsAnimations = v) },
            readForUser = { _, prefs, userId -> HOME_ROWS_ANIMATIONS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_ROWS_ANIMATIONS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_OPAQUE_DOCK,
            get = { it.homeOpaqueDock },
            set = { s, v -> s.copy(homeOpaqueDock = v) },
            readForUser = { _, prefs, userId -> HOME_OPAQUE_DOCK.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_OPAQUE_DOCK.userKey(userId)] = v },
        ),
        // CSV rows: the read normalizes through the shared helpers, restore
        // re-encodes the normalized value verbatim (comma-joined).
        HomeDiscoverySliceRow(
            spec = HOME_PRE_SECTION_ORDER,
            get = { it.homePreSectionOrder },
            set = { s, v -> s.copy(homePreSectionOrder = v) },
            readForUser = { _, prefs, userId ->
                normalizePreSectionOrder(prefs[HOME_PRE_SECTION_ORDER.rawUserKey(userId)])
            },
            writeForUser = { prefs, userId, v ->
                prefs[HOME_PRE_SECTION_ORDER.rawUserKey(userId)] = v.joinToString(",")
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_NEW_BADGE_SECTIONS,
            get = { it.homeNewBadgeSections },
            set = { s, v -> s.copy(homeNewBadgeSections = v) },
            readForUser = { _, prefs, userId ->
                normalizeNewBadgeSections(prefs[HOME_NEW_BADGE_SECTIONS.rawUserKey(userId)])
            },
            writeForUser = { prefs, userId, v ->
                prefs[HOME_NEW_BADGE_SECTIONS.rawUserKey(userId)] = v.joinToString(",") { it.name }
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_REFRESH_CADENCE,
            get = { it.homeRefreshCadence },
            set = { s, v -> s.copy(homeRefreshCadence = v) },
            readForUser = { _, prefs, userId -> HOME_REFRESH_CADENCE.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_REFRESH_CADENCE.rawUserKey(userId)] = v.name },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HERO_IDLE_ROTATION_MS,
            get = { it.homeHeroIdleRotationMs },
            set = { s, v -> s.copy(homeHeroIdleRotationMs = v) },
            readForUser = { _, prefs, userId -> HOME_HERO_IDLE_ROTATION_MS.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_HERO_IDLE_ROTATION_MS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HERO_SCROLL_DEFER,
            get = { it.heroScrollDefer },
            set = { s, v -> s.copy(heroScrollDefer = v) },
            readForUser = { _, prefs, userId -> HERO_SCROLL_DEFER.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HERO_SCROLL_DEFER.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_DISCOVER_PATTERN,
            get = { it.homeDiscoverPattern },
            set = { s, v -> s.copy(homeDiscoverPattern = v) },
            readForUser = { _, prefs, userId -> HOME_DISCOVER_PATTERN.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_DISCOVER_PATTERN.rawUserKey(userId)] = v.name },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_NEWSLETTER_WINDOW,
            get = { it.homeNewsletterWindow },
            set = { s, v -> s.copy(homeNewsletterWindow = v) },
            readForUser = { _, prefs, userId -> HOME_NEWSLETTER_WINDOW.readEnumForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_NEWSLETTER_WINDOW.rawUserKey(userId)] = v.name },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_PRESENCE_POLL_SECONDS,
            get = { it.homePresencePollSeconds },
            set = { s, v -> s.copy(homePresencePollSeconds = v) },
            readForUser = { _, prefs, userId -> HOME_PRESENCE_POLL_SECONDS.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_PRESENCE_POLL_SECONDS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_TRICKPLAY_PEEK_MS,
            get = { it.homeTrickplayPeekMs },
            set = { s, v -> s.copy(homeTrickplayPeekMs = v) },
            readForUser = { _, prefs, userId -> HOME_TRICKPLAY_PEEK_MS.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_TRICKPLAY_PEEK_MS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_SEARCH_RESULTS_HEIGHT_DP,
            get = { it.homeSearchResultsHeightDp },
            set = { s, v -> s.copy(homeSearchResultsHeightDp = v) },
            readForUser = { _, prefs, userId -> HOME_SEARCH_RESULTS_HEIGHT_DP.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_SEARCH_RESULTS_HEIGHT_DP.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_MY_MEDIA_COLUMNS,
            get = { it.homeMyMediaColumns },
            set = { s, v -> s.copy(homeMyMediaColumns = v) },
            readForUser = { _, prefs, userId -> HOME_MY_MEDIA_COLUMNS.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_MY_MEDIA_COLUMNS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_STATS_METRICS,
            get = { it.homeStatsMetrics },
            set = { s, v -> s.copy(homeStatsMetrics = v) },
            readForUser = { _, prefs, userId ->
                normalizeStatsMetrics(prefs[HOME_STATS_METRICS.rawUserKey(userId)])
            },
            writeForUser = { prefs, userId, v ->
                prefs[HOME_STATS_METRICS.rawUserKey(userId)] = v.joinToString(",")
            },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_NEW_BADGE_DAYS,
            get = { it.homeNewBadgeDays },
            set = { s, v -> s.copy(homeNewBadgeDays = v) },
            readForUser = { _, prefs, userId -> HOME_NEW_BADGE_DAYS.readIntForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_NEW_BADGE_DAYS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_ROW_UNWATCHED_COUNTERS,
            get = { it.homeRowUnwatchedCounters },
            set = { s, v -> s.copy(homeRowUnwatchedCounters = v) },
            readForUser = { _, prefs, userId -> HOME_ROW_UNWATCHED_COUNTERS.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_ROW_UNWATCHED_COUNTERS.userKey(userId)] = v },
        ),
        HomeDiscoverySliceRow(
            spec = HOME_HERO_RANDOM_START,
            get = { it.homeHeroRandomStart },
            set = { s, v -> s.copy(homeHeroRandomStart = v) },
            readForUser = { _, prefs, userId -> HOME_HERO_RANDOM_START.readBoolForUser(prefs, userId) },
            writeForUser = { prefs, userId, v -> prefs[HOME_HERO_RANDOM_START.userKey(userId)] = v },
        ),
    )

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
     * catalog order: every row
     * above that carries a [PreferenceSearchSpec]. The settings feature
     * derives its spec-backed catalog rows from this list over its ordered
     * record spine — the home groups (display / next-up / layout / cards).
     */
    val searchEntries: List<PreferenceSearchSpec> = listOf(
        HOME_MODE.searchEntry(),
        HOME_HERO_ENABLED.searchEntry(),
        HOME_HERO_STYLE.searchEntry(),
        HOME_HERO_SLIDESHOW_INTERVAL_SEC.searchEntry(),
        HOME_HERO_VIDEO_ENABLED.searchEntry(),
        HOME_HERO_VIDEO_TV_ENABLED.searchEntry(),
        HOME_BACKDROP_ENABLED.searchEntry(),
        HOME_FOCUS_BACKDROP.searchEntry(),
        SHOW_CLOCK_ON_HOME.searchEntry(),
        HIDE_TOP_HEADER_ON_SCROLL.searchEntry(),
        HOME_GREETING_ENABLED.searchEntry(),
        HOME_HIDE_SEE_ALL.searchEntry(),
        HOME_CARD_SCALE.searchEntry(),
        HOME_CARD_CORNER_RADIUS.searchEntry(),
        HOME_SHOW_MODE_SWITCH.searchEntry(),
        HOME_CLOCK_SHOW_DATE.searchEntry(),
        HOME_NEWSLETTER_BANNER_ENABLED.searchEntry(),
        HOME_ALWAYS_SHOW_USER_SWITCHER.searchEntry(),
        HOME_CW_PROGRESS_STYLE.searchEntry(),
        HOME_PRESENCE_CHIPS_LIMIT.searchEntry(),
        HOME_GENRE_RAIL_LIMIT.searchEntry(),
        HOME_ASK_CONTINUE_REMEMBER.searchEntry(),
        HOME_ROWS_ANIMATIONS.searchEntry(),
        HOME_OPAQUE_DOCK.searchEntry(),
        HOME_PRE_SECTION_ORDER.searchEntry(),
        HOME_NEW_BADGE_SECTIONS.searchEntry(),
        HOME_REFRESH_CADENCE.searchEntry(),
        HOME_HERO_IDLE_ROTATION_MS.searchEntry(),
        HERO_SCROLL_DEFER.searchEntry(),
        HOME_DISCOVER_PATTERN.searchEntry(),
        HOME_NEWSLETTER_WINDOW.searchEntry(),
        HOME_PRESENCE_POLL_SECONDS.searchEntry(),
        HOME_TRICKPLAY_PEEK_MS.searchEntry(),
        HOME_SEARCH_RESULTS_HEIGHT_DP.searchEntry(),
        HOME_MY_MEDIA_COLUMNS.searchEntry(),
        HOME_STATS_METRICS.searchEntry(),
        HOME_NEW_BADGE_DAYS.searchEntry(),
        HOME_ROW_UNWATCHED_COUNTERS.searchEntry(),
        HOME_HERO_RANDOM_START.searchEntry(),
        SHOW_SETTINGS_IN_HOME_SEARCH.searchEntry(),
        HOME_STATS_CARD.searchEntry(),
        HOME_GENRE_RAIL.searchEntry(),
        HOME_PRESENCE_CHIPS.searchEntry(),
        HOME_NEW_SECTION_ANNOUNCEMENTS.searchEntry(),
        CONTINUE_WATCHING_CLICK_BEHAVIOR.searchEntry(),
        CLASSIC_ROWS.searchEntry(),
        EXCLUDE_ACTIVE_SESSIONS.searchEntry(),
        NEXT_UP_EXCLUDED_SERIES_IDS.searchEntry(),
        HIDDEN_CW_ITEM_IDS.searchEntry(),
        MY_MEDIA_HIDDEN_LIBRARY_IDS.searchEntry(),
        MERGE_CONTINUE_WATCHING_NEXT_UP.searchEntry(),
        NEXT_UP_MAX_DAYS.searchEntry(),
        NEXT_UP_REWATCHING.searchEntry(),
        PINNED_HOME_SECTIONS.searchEntry(),
        HOME_LAYOUT_PRESETS.searchEntry(),
        HOME_LAYOUT_AUTOPUSH.searchEntry(),
        HOME_LIBRARY_SECTION_OVERRIDES.searchEntry(),
        HOME_DISCOVER_ROWS.searchEntry(),
        SHOW_UNWATCHED_BADGE.searchEntry(),
        SHOW_WATCHED_CHECKMARK.searchEntry(),
        HIDE_WATCHED_ITEMS.searchEntry(),
        SHOW_EXTERNAL_RATINGS.searchEntry(),
        SHOW_QUALITY_BADGES.searchEntry(),
        SHOW_NEW_BADGES.searchEntry(),
        HOME_TRICKPLAY_PREVIEW.searchEntry(),
        ANTI_SPOILER_HOME.searchEntry(),
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

// ----------------------------------------------------------------------
// The slice rows: the per-user read rows + restore rows the store's
// projection and restore fold. The namespaced variant of the videoplayer
// derivation — no reflection, every binding is a declared lambda, and the
// JVM `HomeDiscoverySpecDerivationTest` pins the row list to the spec rows.
// ----------------------------------------------------------------------

/**
 * The per-user JSON decode surface the slice-bound JSON rows read through —
 * implemented by the store's per-user reader ([HomeDiscoveryStore]'s
 * `UserReader`), which owns the parse caches (one reader instance per active
 * user, so a user switch can never serve another user's cached parse). The
 * spec tier depends on this interface, not the store, so the slice rows stay
 * store-blind.
 */
internal interface HomeDiscoveryUserJsonReads {
    fun readHomeSectionOrder(prefs: Preferences): List<HomeSectionType>

    fun readLibraryHomeSectionOverrides(prefs: Preferences): Map<String, Set<HomeSectionType>>

    fun readPinnedHomeSections(prefs: Preferences): List<PinnedHomeSection>

    fun readDiscoverRows(prefs: Preferences): List<DiscoverRowConfig>

    fun readHomeLayoutPresets(prefs: Preferences): List<HomeLayoutPreset>

    fun readNextUpExcludedSeriesIds(prefs: Preferences): Set<String>

    fun readHiddenCwItemIds(prefs: Preferences): Set<String>

    fun readLastViewedSeasonBySeries(prefs: Preferences): Map<String, String>

    fun readMyMediaHiddenLibraryIds(prefs: Preferences): Set<String>

    fun readThemeMusicMutedSeriesIds(prefs: Preferences): Set<String>
}

/**
 * One slice-bound row ([HomeDiscoveryPreferenceSpecs.sliceRows]): the
 * [PreferenceSpec] plus the plain-data bindings the store's read projection
 * and restore iterate — the namespaced read, its inverse namespaced restore
 * write, and the [HomeDiscoverySlice] lens. The lambda bodies spell the
 * per-row bindings — the per-user hooks [readBoolForUser] /
 * [readIntForUser] / [readEnumForUser], plain typed slots, the reader's JSON
 * decodes and the restore-side clamps — and folding over the rows applies
 * them in order. The class closes over [T] — the fold
 * steps ([readInto] / [writeFrom]) are the star-projection-safe entry points,
 * so the folds need no cast (the no-reflection rule keeps its no-cast
 * corollary).
 */
internal class HomeDiscoverySliceRow<T>(
    val spec: PreferenceSpec<T>,
    private val get: (HomeDiscoverySlice) -> T,
    private val set: (HomeDiscoverySlice, T) -> HomeDiscoverySlice,
    private val readForUser: (reads: HomeDiscoveryUserJsonReads, prefs: Preferences, userId: String) -> T,
    private val writeForUser: (prefs: MutablePreferences, userId: String, value: T) -> Unit,
) {
    /** Fold step for the read projection: reads the row under [userId]'s namespace and lenses it into [slice]. */
    fun readInto(
        reads: HomeDiscoveryUserJsonReads,
        prefs: Preferences,
        userId: String,
        slice: HomeDiscoverySlice,
    ): HomeDiscoverySlice = set(slice, readForUser(reads, prefs, userId))

    /** Fold step for restore: writes the row's [slice] value under [userId]'s namespace. */
    fun writeFrom(
        slice: HomeDiscoverySlice,
        prefs: MutablePreferences,
        userId: String,
    ) {
        writeForUser(prefs, userId, get(slice))
    }
}
