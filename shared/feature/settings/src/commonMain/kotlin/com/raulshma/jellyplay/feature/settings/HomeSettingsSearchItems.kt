package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_home
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_classic_rows_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_classic_rows_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_configure_libraries_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_configure_libraries_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_discover_rows_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_discover_rows_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hero_section_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hero_section_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_top_header_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_top_header_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_watched_items_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_watched_items_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_backdrop_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_backdrop_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_layout_presets_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_layout_presets_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_hidden_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_hidden_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_external_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_external_ratings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_unwatched_badge_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_unwatched_badge_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_watched_checkmark_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_watched_checkmark_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_unhide_cw_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_unhide_cw_title

/**
 * The single-source row ids of the Home settings screen's search declarations.
 * Every consumer — the `SettingsRowRecord` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists exactly
 * once. The values are the persisted deep-link/recents contract (they carried
 * over verbatim from their former Appearance home): they change only
 * deliberately, here.
 */
internal object HomeSettingsIds {
    const val HOME_MODE = "home_mode"
    const val HERO_SECTION = "hero_section"
    const val HOME_BACKDROP = "home_backdrop"
    const val CLOCK_HOME = "clock_home"
    const val HIDE_TOP_HEADER = "hide_top_header"
    const val SETTINGS_IN_HOME_SEARCH = "settings_in_home_search"
    const val CONTINUE_WATCHING_CLICK = "continue_watching_click"
    const val CLASSIC_ROWS = "classic_rows"
    const val NEXT_UP_HIDDEN = "next_up_hidden"
    const val UNHIDE_CW = "unhide_cw"
    const val MERGE_CONTINUE_NEXT_UP = "merge_continue_next_up"
    const val NEXT_UP_MAX_DAYS = "next_up_max_days"
    const val NEXT_UP_REWATCHING = "next_up_rewatching"
    const val PINNED_HOME_SECTIONS = "pinned_home_sections"
    const val HOME_LAYOUT_PRESETS = "home_layout_presets"
    const val CONFIGURE_LIBRARIES = "configure_libraries"
    const val DISCOVER_ROWS = "discover_rows"

    // The card-display quartet, moved off AppearanceSettingsScreen's
    // "Library & Cards" group (PS-4): home-discovery knobs whose ids ARE the
    // pre-move deep-link/recents contract, carried over verbatim so existing
    // recents keep resolving — only the owning screen changed.
    const val SHOW_UNWATCHED_BADGE = "show_unwatched_badge"
    const val SHOW_WATCHED_CHECKMARK = "show_watched_checkmark"
    const val HIDE_WATCHED_ITEMS = "hide_watched_items"
    const val SHOW_EXTERNAL_RATINGS = "show_external_ratings"
}

// ═══════════════════════════════════════════════════════════════════════
// The spec-derived derivation inputs: every row
// of this screen is backed by a [HomeDiscoveryPreferenceSpecs] spec entry —
// the semantics (ids, keywords, category keys, route kinds) are declared
// once next to the store; this file declares only the id → resource/icon
// binding tables, the routeKind → Route map, and the per-group derivation
// calls. The record lists below stay the ordered spine — the catalog order
// and the [rowTitle]/[rowIcon] screen faces.
// ═══════════════════════════════════════════════════════════════════════

private val searchRoutes: Map<String, Route> = mapOf(
    HomeDiscoveryPreferenceSpecs.ROUTE_HOME_SETTINGS to Route.HomeSettings(),
    HomeDiscoveryPreferenceSpecs.ROUTE_NEXT_UP_EXCLUDED to Route.NextUpExcluded,
    HomeDiscoveryPreferenceSpecs.ROUTE_PINNED_HOME_SECTIONS to Route.PinnedHomeSections(),
    HomeDiscoveryPreferenceSpecs.ROUTE_HOME_LAYOUT_PRESETS to Route.HomeLayoutPresets(),
    HomeDiscoveryPreferenceSpecs.ROUTE_LIBRARY_HOME_SECTIONS to Route.LibraryHomeSections(),
    HomeDiscoveryPreferenceSpecs.ROUTE_DISCOVER_ROWS to Route.DiscoverRows(),
)

private val homeSpecEntries: List<PreferenceSearchSpec> = HomeDiscoveryPreferenceSpecs.searchEntries

private fun specsFor(bindings: List<SettingsSearchBinding>): List<PreferenceSearchSpec> {
    val ids = bindings.map { it.id }.toSet()
    val matched = homeSpecEntries.filter { it.id in ids }
    val missing = ids - matched.map { it.id }.toSet()
    require(missing.isEmpty()) { "settings-search binding ids without a spec entry: $missing" }
    return matched
}

private val homeCategory = CoreUiRes.string.ss_cat_home

/** The display group's binding table — the search faces of its ten rows. */
private val homeDisplayBindings = listOf(
    SettingsSearchBinding(HomeSettingsIds.HOME_MODE, Res.string.ss_home_mode_title, Res.string.ss_home_mode_subtitle, homeCategory, Tabler.Outline.Home),
    SettingsSearchBinding(HomeSettingsIds.HERO_SECTION, Res.string.ss_hero_section_title, Res.string.ss_hero_section_subtitle, homeCategory, Tabler.Outline.LayersLinked),
    SettingsSearchBinding(HomeSettingsIds.HOME_BACKDROP, Res.string.ss_home_backdrop_title, Res.string.ss_home_backdrop_subtitle, homeCategory, Tabler.Outline.Photo),
    SettingsSearchBinding(HomeSettingsIds.CLOCK_HOME, Res.string.ss_clock_home_title, Res.string.ss_clock_home_subtitle, homeCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(HomeSettingsIds.HIDE_TOP_HEADER, Res.string.ss_hide_top_header_title, Res.string.ss_hide_top_header_subtitle, homeCategory, Tabler.Outline.ArrowBarToDown),
    SettingsSearchBinding(HomeSettingsIds.SETTINGS_IN_HOME_SEARCH, Res.string.ss_settings_in_home_search_title, Res.string.ss_settings_in_home_search_subtitle, homeCategory, Tabler.Outline.Adjustments),
    SettingsSearchBinding(HomeSettingsIds.CONTINUE_WATCHING_CLICK, Res.string.ss_continue_watching_click_title, Res.string.ss_continue_watching_click_subtitle, homeCategory, Tabler.Outline.PlayerPlay),
    SettingsSearchBinding(HomeSettingsIds.CLASSIC_ROWS, Res.string.ss_classic_rows_title, Res.string.ss_classic_rows_subtitle, homeCategory, Tabler.Outline.History),
    SettingsSearchBinding(HomeSettingsIds.NEXT_UP_HIDDEN, Res.string.ss_next_up_hidden_title, Res.string.ss_next_up_hidden_subtitle, homeCategory, Tabler.Outline.EyeOff),
    SettingsSearchBinding(HomeSettingsIds.UNHIDE_CW, Res.string.ss_unhide_cw_title, Res.string.ss_unhide_cw_subtitle, homeCategory, Tabler.Outline.Eye),
)

/**
 * Settings-search items for the "Home Display" group of HomeSettingsScreen —
 * the rows moved off AppearanceSettingsScreen's advanced-gated theme list.
 * Aggregated in [SettingsSearchCatalog]; the ids are the pre-move deep-link
 * contract, so existing recents keep resolving.
 *
 * Spec-derived: the record list below is the ordered spine — the catalog
 * order and the screen faces — and every row's search faces derive from its
 * [HomeDiscoveryPreferenceSpecs] spec entry + binding.
 */
internal val HomeDisplayRowRecords = listOf(
    SettingsRowRecord(id = HomeSettingsIds.HOME_MODE, titleRes = Res.string.settings_home_mode, icon = Tabler.Outline.Home),
    SettingsRowRecord(id = HomeSettingsIds.HERO_SECTION, titleRes = Res.string.settings_show_hero_section, icon = Tabler.Outline.LayersLinked),
    SettingsRowRecord(id = HomeSettingsIds.HOME_BACKDROP, titleRes = Res.string.settings_home_backdrop, icon = Tabler.Outline.Photo),
    SettingsRowRecord(id = HomeSettingsIds.CLOCK_HOME, titleRes = Res.string.settings_show_clock_home, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = HomeSettingsIds.HIDE_TOP_HEADER, titleRes = Res.string.settings_hide_top_header_on_scroll, icon = Tabler.Outline.ArrowBarToDown),
    SettingsRowRecord(id = HomeSettingsIds.SETTINGS_IN_HOME_SEARCH, titleRes = Res.string.settings_show_settings_in_home_search, icon = Tabler.Outline.Adjustments),
    SettingsRowRecord(id = HomeSettingsIds.CONTINUE_WATCHING_CLICK, titleRes = Res.string.settings_continue_watching_tap, icon = Tabler.Outline.PlayerPlay),
    SettingsRowRecord(id = HomeSettingsIds.CLASSIC_ROWS, titleRes = Res.string.settings_classic_rows, icon = Tabler.Outline.History),
    SettingsRowRecord(id = HomeSettingsIds.NEXT_UP_HIDDEN, titleRes = Res.string.settings_next_up_hidden, icon = Tabler.Outline.EyeOff),
    SettingsRowRecord(id = HomeSettingsIds.UNHIDE_CW, titleRes = Res.string.settings_unhide_continue_watching, icon = Tabler.Outline.Eye),
)

/** The catalog projection of the spec-derived display rows. */
internal val HomeDisplaySearchItems: List<SettingsSearchItem> =
    HomeDisplayRowRecords.toSearchItems(
        specEntries = specsFor(homeDisplayBindings),
        bindings = homeDisplayBindings,
        routes = searchRoutes,
        categoryRes = homeCategory,
    )


/**
 * The display group's per-id declared row admissions — the single gate both
 * `homeDisplayScreenRowTotal` and HomeSettingsScreen's emission list read.
 * The nine config rows always render ([RowAdmission.Always]); `unhide_cw`
 * deliberately declares NO gate: it renders only while hidden continue-
 * watching items exist — a content-state condition with no admission
 * vocabulary — so the strict derivation excludes it and the screen adds the
 * +1 explicitly (the storage cache-used info row shape, preserved count).
 */
internal val HomeDisplayRowAdmissions: Map<String, RowAdmission> =
    HomeDisplaySearchItems.filter { it.id != HomeSettingsIds.UNHIDE_CW }.admissionsByAdvancedFlag()


/** The next-up group's binding table — the search faces of its three rows. */
private val homeNextUpBindings = listOf(
    SettingsSearchBinding(HomeSettingsIds.MERGE_CONTINUE_NEXT_UP, Res.string.ss_merge_continue_next_up_title, Res.string.ss_merge_continue_next_up_subtitle, homeCategory, Tabler.Outline.LayersLinked),
    SettingsSearchBinding(HomeSettingsIds.NEXT_UP_MAX_DAYS, Res.string.ss_next_up_max_days_title, Res.string.ss_next_up_max_days_subtitle, homeCategory, Tabler.Outline.CalendarTime),
    SettingsSearchBinding(HomeSettingsIds.NEXT_UP_REWATCHING, Res.string.ss_next_up_rewatching_title, Res.string.ss_next_up_rewatching_subtitle, homeCategory, Tabler.Outline.History),
)

/**
 * Settings-search items for the "Continue Watching & Next Up" group of
 * HomeSettingsScreen — Next Up behavior rows moved off Appearance.
 *
 * Spec-derived: see [HomeDisplaySearchItems].
 */
internal val HomeNextUpRowRecords = listOf(
    SettingsRowRecord(id = HomeSettingsIds.MERGE_CONTINUE_NEXT_UP, titleRes = Res.string.settings_merge_continue_next_up, icon = Tabler.Outline.LayersLinked),
    SettingsRowRecord(id = HomeSettingsIds.NEXT_UP_MAX_DAYS, titleRes = Res.string.settings_next_up_time_window, icon = Tabler.Outline.CalendarTime),
    SettingsRowRecord(id = HomeSettingsIds.NEXT_UP_REWATCHING, titleRes = Res.string.settings_rewatching_next_up, icon = Tabler.Outline.History),
)

/** The catalog projection of the spec-derived next-up rows. */
internal val HomeNextUpSearchItems: List<SettingsSearchItem> =
    HomeNextUpRowRecords.toSearchItems(
        specEntries = specsFor(homeNextUpBindings),
        bindings = homeNextUpBindings,
        routes = searchRoutes,
        categoryRes = homeCategory,
    )


/** The layout group's binding table — the search faces of its four drill-in rows. */
private val homeLayoutBindings = listOf(
    SettingsSearchBinding(HomeSettingsIds.PINNED_HOME_SECTIONS, Res.string.ss_pinned_home_sections_title, Res.string.ss_pinned_home_sections_subtitle, homeCategory, Tabler.Outline.Pinned),
    SettingsSearchBinding(HomeSettingsIds.HOME_LAYOUT_PRESETS, Res.string.ss_home_layout_presets_title, Res.string.ss_home_layout_presets_subtitle, homeCategory, Tabler.Outline.Bookmarks),
    SettingsSearchBinding(HomeSettingsIds.CONFIGURE_LIBRARIES, Res.string.ss_configure_libraries_title, Res.string.ss_configure_libraries_subtitle, homeCategory, Tabler.Outline.Folders),
    SettingsSearchBinding(HomeSettingsIds.DISCOVER_ROWS, Res.string.ss_discover_rows_title, Res.string.ss_discover_rows_subtitle, homeCategory, Tabler.Outline.Compass),
)

/**
 * Settings-search items for the "Home Screen Layout" group of HomeSettingsScreen —
 * the drill-in rows moved off Appearance's layout group, plus the previously
 * unindexed Discover Rows row (its screen row existed with no catalog entry,
 * so it was unreachable from search).
 *
 * Spec-derived: see [HomeDisplaySearchItems].
 */
internal val HomeLayoutRowRecords = listOf(
    SettingsRowRecord(id = HomeSettingsIds.PINNED_HOME_SECTIONS, titleRes = Res.string.settings_pinned_home_sections, icon = Tabler.Outline.Pinned),
    SettingsRowRecord(id = HomeSettingsIds.HOME_LAYOUT_PRESETS, titleRes = Res.string.settings_home_layout_presets, icon = Tabler.Outline.Bookmarks),
    SettingsRowRecord(id = HomeSettingsIds.CONFIGURE_LIBRARIES, titleRes = Res.string.settings_configure_libraries, icon = Tabler.Outline.Folders),
    SettingsRowRecord(id = HomeSettingsIds.DISCOVER_ROWS, titleRes = Res.string.settings_discover_rows, icon = Tabler.Outline.Compass),
)

/** The catalog projection of the spec-derived layout rows. */
internal val HomeLayoutSearchItems: List<SettingsSearchItem> =
    HomeLayoutRowRecords.toSearchItems(
        specEntries = specsFor(homeLayoutBindings),
        bindings = homeLayoutBindings,
        routes = searchRoutes,
        categoryRes = homeCategory,
    )


/** The cards group's binding table — the search faces of its four rows. */
private val homeCardsBindings = listOf(
    SettingsSearchBinding(HomeSettingsIds.SHOW_UNWATCHED_BADGE, Res.string.ss_show_unwatched_badge_title, Res.string.ss_show_unwatched_badge_subtitle, homeCategory, Tabler.Outline.Folder),
    SettingsSearchBinding(HomeSettingsIds.SHOW_WATCHED_CHECKMARK, Res.string.ss_show_watched_checkmark_title, Res.string.ss_show_watched_checkmark_subtitle, homeCategory, Tabler.Outline.CircleCheck),
    SettingsSearchBinding(HomeSettingsIds.HIDE_WATCHED_ITEMS, Res.string.ss_hide_watched_items_title, Res.string.ss_hide_watched_items_subtitle, homeCategory, Tabler.Outline.EyeOff),
    SettingsSearchBinding(HomeSettingsIds.SHOW_EXTERNAL_RATINGS, Res.string.ss_show_external_ratings_title, Res.string.ss_show_external_ratings_subtitle, homeCategory, Tabler.Outline.Star),
)

/**
 * Settings-search items for the "Cards" group of HomeSettingsScreen — the
 * card-display toggles moved off AppearanceSettingsScreen's "Library & Cards"
 * group (PS-4, the finished hub split): they are home-discovery knobs and now
 * live beside the rest of the home hub. Aggregated in [SettingsSearchCatalog];
 * the ids are the pre-move deep-link contract, so existing recents keep
 * resolving.
 *
 * Spec-derived: see [HomeDisplaySearchItems]. The four rows render
 * unconditionally on this hub (no advanced gate), matching their shipped
 * always-on behavior.
 */
internal val HomeCardsRowRecords = listOf(
    SettingsRowRecord(id = HomeSettingsIds.SHOW_UNWATCHED_BADGE, titleRes = Res.string.settings_show_unwatched_badge, icon = Tabler.Outline.Folder),
    SettingsRowRecord(id = HomeSettingsIds.SHOW_WATCHED_CHECKMARK, titleRes = Res.string.settings_show_watched_checkmark, icon = Tabler.Outline.CircleCheck),
    SettingsRowRecord(id = HomeSettingsIds.HIDE_WATCHED_ITEMS, titleRes = Res.string.settings_hide_watched_items, icon = Tabler.Outline.EyeOff),
    SettingsRowRecord(id = HomeSettingsIds.SHOW_EXTERNAL_RATINGS, titleRes = Res.string.settings_show_external_ratings, icon = Tabler.Outline.Star),
)

/** The catalog projection of the spec-derived cards rows. */
internal val HomeCardsSearchItems: List<SettingsSearchItem> =
    HomeCardsRowRecords.toSearchItems(
        specEntries = specsFor(homeCardsBindings),
        bindings = homeCardsBindings,
        routes = searchRoutes,
        categoryRes = homeCategory,
    )


/**
 * The cards group's per-id declared row admissions — every row renders
 * unconditionally (the shipped always-on behavior; their former Appearance
 * records carried a legacy advanced tag the screen never honored, now dropped
 * at the spec), so each derives [RowAdmission.Always].
 */
internal val HomeCardsRowAdmissions: Map<String, RowAdmission> =
    HomeCardsSearchItems.admissionsByAdvancedFlag()
