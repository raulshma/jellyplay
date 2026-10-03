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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_classic_rows_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_classic_rows_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_title
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
 * The home domain's fused row declarations — the feature-side single home of
 * every home-hub row's presentation, ordering, and capability (the
 * [AppearanceRows] template). Each [SettingsRow] replaces the trio the domain
 * used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `HomeSettingsIds` holder constant (all
 * retired).
 *
 * The SEMANTICS stay two-homed by design: every row is backed by a
 * [HomeDiscoveryPreferenceSpecs] spec entry — the keywords, advanced flag,
 * platform rule and route kind are declared once next to the store — so the
 * rows carry screen faces only. The one exception is `unhide_cw`
 * ([RowAdmission.ContentGated]): it renders only while hidden
 * continue-watching items exist, a content-state condition the admission
 * flags do not carry, so it counts in no total and the screen adds the +1
 * explicitly (the shipped shape). The projection fails fast at catalog init
 * on any row-spec drift; search results, catalog order, group membership and
 * per-gate visibility are byte-identical to the retired declarations.
 */
internal object HomeRows {

    // -- The "Home Display" group's ten rows, in catalog order (every row spec-backed). --

    val HomeMode = SettingsRow(
        id = "home_mode",
        icon = Tabler.Outline.Home,
        titleRes = Res.string.settings_home_mode,
        searchTitleRes = Res.string.ss_home_mode_title,
        searchSubtitleRes = Res.string.ss_home_mode_subtitle,
    )

    val HeroSection = SettingsRow(
        id = "hero_section",
        icon = Tabler.Outline.LayersLinked,
        titleRes = Res.string.settings_show_hero_section,
        searchTitleRes = Res.string.ss_hero_section_title,
        searchSubtitleRes = Res.string.ss_hero_section_subtitle,
    )

    val HomeBackdrop = SettingsRow(
        id = "home_backdrop",
        icon = Tabler.Outline.Photo,
        titleRes = Res.string.settings_home_backdrop,
        searchTitleRes = Res.string.ss_home_backdrop_title,
        searchSubtitleRes = Res.string.ss_home_backdrop_subtitle,
    )

    val ClockHome = SettingsRow(
        id = "clock_home",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_show_clock_home,
        searchTitleRes = Res.string.ss_clock_home_title,
        searchSubtitleRes = Res.string.ss_clock_home_subtitle,
    )

    val HideTopHeader = SettingsRow(
        id = "hide_top_header",
        icon = Tabler.Outline.ArrowBarToDown,
        titleRes = Res.string.settings_hide_top_header_on_scroll,
        searchTitleRes = Res.string.ss_hide_top_header_title,
        searchSubtitleRes = Res.string.ss_hide_top_header_subtitle,
    )

    val SettingsInHomeSearch = SettingsRow(
        id = "settings_in_home_search",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_show_settings_in_home_search,
        searchTitleRes = Res.string.ss_settings_in_home_search_title,
        searchSubtitleRes = Res.string.ss_settings_in_home_search_subtitle,
    )

    val ContinueWatchingClick = SettingsRow(
        id = "continue_watching_click",
        icon = Tabler.Outline.PlayerPlay,
        titleRes = Res.string.settings_continue_watching_tap,
        searchTitleRes = Res.string.ss_continue_watching_click_title,
        searchSubtitleRes = Res.string.ss_continue_watching_click_subtitle,
    )

    val ClassicRows = SettingsRow(
        id = "classic_rows",
        icon = Tabler.Outline.History,
        titleRes = Res.string.settings_classic_rows,
        searchTitleRes = Res.string.ss_classic_rows_title,
        searchSubtitleRes = Res.string.ss_classic_rows_subtitle,
    )

    val NextUpHidden = SettingsRow(
        id = "next_up_hidden",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_next_up_hidden,
        searchTitleRes = Res.string.ss_next_up_hidden_title,
        searchSubtitleRes = Res.string.ss_next_up_hidden_subtitle,
    )

    val UnhideCw = SettingsRow(
        id = "unhide_cw",
        icon = Tabler.Outline.Eye,
        titleRes = Res.string.settings_unhide_continue_watching,
        searchTitleRes = Res.string.ss_unhide_cw_title,
        searchSubtitleRes = Res.string.ss_unhide_cw_subtitle,
        gate = RowAdmission.ContentGated,
    )

    // -- The "Continue Watching & Next Up" group's three rows, in catalog order. --

    val MergeContinueNextUp = SettingsRow(
        id = "merge_continue_next_up",
        icon = Tabler.Outline.LayersLinked,
        titleRes = Res.string.settings_merge_continue_next_up,
        searchTitleRes = Res.string.ss_merge_continue_next_up_title,
        searchSubtitleRes = Res.string.ss_merge_continue_next_up_subtitle,
    )

    val NextUpMaxDays = SettingsRow(
        id = "next_up_max_days",
        icon = Tabler.Outline.CalendarTime,
        titleRes = Res.string.settings_next_up_time_window,
        searchTitleRes = Res.string.ss_next_up_max_days_title,
        searchSubtitleRes = Res.string.ss_next_up_max_days_subtitle,
    )

    val NextUpRewatching = SettingsRow(
        id = "next_up_rewatching",
        icon = Tabler.Outline.History,
        titleRes = Res.string.settings_rewatching_next_up,
        searchTitleRes = Res.string.ss_next_up_rewatching_title,
        searchSubtitleRes = Res.string.ss_next_up_rewatching_subtitle,
    )

    // -- The "Home Screen Layout" group's four drill-in rows, in catalog order. --

    val PinnedHomeSections = SettingsRow(
        id = "pinned_home_sections",
        icon = Tabler.Outline.Pinned,
        titleRes = Res.string.settings_pinned_home_sections,
        searchTitleRes = Res.string.ss_pinned_home_sections_title,
        searchSubtitleRes = Res.string.ss_pinned_home_sections_subtitle,
    )

    val HomeLayoutPresets = SettingsRow(
        id = "home_layout_presets",
        icon = Tabler.Outline.Bookmarks,
        titleRes = Res.string.settings_home_layout_presets,
        searchTitleRes = Res.string.ss_home_layout_presets_title,
        searchSubtitleRes = Res.string.ss_home_layout_presets_subtitle,
    )

    val ConfigureLibraries = SettingsRow(
        id = "configure_libraries",
        icon = Tabler.Outline.Folders,
        titleRes = Res.string.settings_configure_libraries,
        searchTitleRes = Res.string.ss_configure_libraries_title,
        searchSubtitleRes = Res.string.ss_configure_libraries_subtitle,
    )

    val DiscoverRows = SettingsRow(
        id = "discover_rows",
        icon = Tabler.Outline.Compass,
        titleRes = Res.string.settings_discover_rows,
        searchTitleRes = Res.string.ss_discover_rows_title,
        searchSubtitleRes = Res.string.ss_discover_rows_subtitle,
    )

    // -- The "Cards" group's four rows (the PS-4 moved card-display quartet), in catalog order. --

    val ShowUnwatchedBadge = SettingsRow(
        id = "show_unwatched_badge",
        icon = Tabler.Outline.Folder,
        titleRes = Res.string.settings_show_unwatched_badge,
        searchTitleRes = Res.string.ss_show_unwatched_badge_title,
        searchSubtitleRes = Res.string.ss_show_unwatched_badge_subtitle,
    )

    val ShowWatchedCheckmark = SettingsRow(
        id = "show_watched_checkmark",
        icon = Tabler.Outline.CircleCheck,
        titleRes = Res.string.settings_show_watched_checkmark,
        searchTitleRes = Res.string.ss_show_watched_checkmark_title,
        searchSubtitleRes = Res.string.ss_show_watched_checkmark_subtitle,
    )

    val HideWatchedItems = SettingsRow(
        id = "hide_watched_items",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_hide_watched_items,
        searchTitleRes = Res.string.ss_hide_watched_items_title,
        searchSubtitleRes = Res.string.ss_hide_watched_items_subtitle,
    )

    val ShowExternalRatings = SettingsRow(
        id = "show_external_ratings",
        icon = Tabler.Outline.Star,
        titleRes = Res.string.settings_show_external_ratings,
        searchTitleRes = Res.string.ss_show_external_ratings_title,
        searchSubtitleRes = Res.string.ss_show_external_ratings_subtitle,
    )

    /**
     * Every fused home row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = HomeDisplayRows + HomeNextUpRows + HomeLayoutRows + HomeCardsRows
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = mapOf(
    HomeDiscoveryPreferenceSpecs.ROUTE_HOME_SETTINGS to Route.HomeSettings(),
    HomeDiscoveryPreferenceSpecs.ROUTE_NEXT_UP_EXCLUDED to Route.NextUpExcluded,
    HomeDiscoveryPreferenceSpecs.ROUTE_PINNED_HOME_SECTIONS to Route.PinnedHomeSections(),
    HomeDiscoveryPreferenceSpecs.ROUTE_HOME_LAYOUT_PRESETS to Route.HomeLayoutPresets(),
    HomeDiscoveryPreferenceSpecs.ROUTE_LIBRARY_HOME_SECTIONS to Route.LibraryHomeSections(),
    HomeDiscoveryPreferenceSpecs.ROUTE_DISCOVER_ROWS to Route.DiscoverRows(),
)

private val homeSpecEntries: List<PreferenceSearchSpec> = HomeDiscoveryPreferenceSpecs.searchEntries

private val homeCategory = CoreUiRes.string.ss_cat_home

internal val HomeDisplayRows: List<SettingsRow> = listOf(
    HomeRows.HomeMode,
    HomeRows.HeroSection,
    HomeRows.HomeBackdrop,
    HomeRows.ClockHome,
    HomeRows.HideTopHeader,
    HomeRows.SettingsInHomeSearch,
    HomeRows.ContinueWatchingClick,
    HomeRows.ClassicRows,
    HomeRows.NextUpHidden,
    HomeRows.UnhideCw,
)

internal val HomeNextUpRows: List<SettingsRow> = listOf(
    HomeRows.MergeContinueNextUp,
    HomeRows.NextUpMaxDays,
    HomeRows.NextUpRewatching,
)

internal val HomeLayoutRows: List<SettingsRow> = listOf(
    HomeRows.PinnedHomeSections,
    HomeRows.HomeLayoutPresets,
    HomeRows.ConfigureLibraries,
    HomeRows.DiscoverRows,
)

internal val HomeCardsRows: List<SettingsRow> = listOf(
    HomeRows.ShowUnwatchedBadge,
    HomeRows.ShowWatchedCheckmark,
    HomeRows.HideWatchedItems,
    HomeRows.ShowExternalRatings,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val HomeDisplayGroup =
    HomeDisplayRows.asRowGroup("home.display", specEntriesFor(HomeDisplayRows, homeSpecEntries), searchRoutes, homeCategory)

internal val HomeNextUpGroup =
    HomeNextUpRows.asRowGroup("home.nextUp", specEntriesFor(HomeNextUpRows, homeSpecEntries), searchRoutes, homeCategory)

internal val HomeLayoutGroup =
    HomeLayoutRows.asRowGroup("home.layout", specEntriesFor(HomeLayoutRows, homeSpecEntries), searchRoutes, homeCategory)

internal val HomeCardsGroup =
    HomeCardsRows.asRowGroup("home.cards", specEntriesFor(HomeCardsRows, homeSpecEntries), searchRoutes, homeCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val HomeDisplaySearchItems: List<SettingsSearchItem> = HomeDisplayGroup.items

internal val HomeNextUpSearchItems: List<SettingsSearchItem> = HomeNextUpGroup.items

internal val HomeLayoutSearchItems: List<SettingsSearchItem> = HomeLayoutGroup.items

internal val HomeCardsSearchItems: List<SettingsSearchItem> = HomeCardsGroup.items

// -- The domain's root-screen entrance declaration --

/** The home domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_home` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val HomeEntrance = SettingsEntranceSectionRow(
    key = "item_home",
    rowId = "home",
    icon = Tabler.Outline.Home,
    titleRes = Res.string.settings_home_title,
)
