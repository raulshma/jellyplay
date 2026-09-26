package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_home
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_backdrop_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_backdrop_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_layout_presets_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_layout_presets_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_title
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
    const val UNHIDE_CW = "unhide_cw"
    const val MERGE_CONTINUE_NEXT_UP = "merge_continue_next_up"
    const val NEXT_UP_MAX_DAYS = "next_up_max_days"
    const val NEXT_UP_REWATCHING = "next_up_rewatching"
    const val PINNED_HOME_SECTIONS = "pinned_home_sections"
    const val HOME_LAYOUT_PRESETS = "home_layout_presets"
    const val CONFIGURE_LIBRARIES = "configure_libraries"
    const val DISCOVER_ROWS = "discover_rows"
}

/**
 * Settings-search items for the "Home Display" group of HomeSettingsScreen —
 * the rows moved off AppearanceSettingsScreen's advanced-gated theme list.
 * Aggregated in [SettingsSearchCatalog]; the ids are the pre-move deep-link
 * contract, so existing recents keep resolving.
 */
internal val HomeDisplayRowRecords = listOf(
    SettingsRowRecord(
        id = HomeSettingsIds.HOME_MODE,
        titleRes = Res.string.settings_home_mode,
        searchTitleRes = Res.string.ss_home_mode_title,
        searchSubtitleRes = Res.string.ss_home_mode_subtitle,
        keywords = listOf("home", "layout", "mode", "video", "music"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.Home,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.HERO_SECTION,
        titleRes = Res.string.settings_show_hero_section,
        searchTitleRes = Res.string.ss_hero_section_title,
        searchSubtitleRes = Res.string.ss_hero_section_subtitle,
        keywords = listOf("hero", "banner", "featured", "home", "carousel"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.LayersLinked,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.HOME_BACKDROP,
        titleRes = Res.string.settings_home_backdrop,
        searchTitleRes = Res.string.ss_home_backdrop_title,
        searchSubtitleRes = Res.string.ss_home_backdrop_subtitle,
        keywords = listOf("home", "backdrop", "artwork", "background", "blur", "wallpaper"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.Photo,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.CLOCK_HOME,
        titleRes = Res.string.settings_show_clock_home,
        searchTitleRes = Res.string.ss_clock_home_title,
        searchSubtitleRes = Res.string.ss_clock_home_subtitle,
        keywords = listOf("clock", "time", "home", "wall", "current"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.Clock,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.HIDE_TOP_HEADER,
        titleRes = Res.string.settings_hide_top_header_on_scroll,
        searchTitleRes = Res.string.ss_hide_top_header_title,
        searchSubtitleRes = Res.string.ss_hide_top_header_subtitle,
        keywords = listOf("top header", "app bar", "home bar", "hide", "scroll", "auto hide", "collapse", "dock"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.ArrowBarToDown,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.SETTINGS_IN_HOME_SEARCH,
        titleRes = Res.string.settings_show_settings_in_home_search,
        searchTitleRes = Res.string.ss_settings_in_home_search_title,
        searchSubtitleRes = Res.string.ss_settings_in_home_search_subtitle,
        keywords = listOf("search", "settings", "home", "find", "discover", "quick", "shortcut"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.Adjustments,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.CONTINUE_WATCHING_CLICK,
        titleRes = Res.string.settings_continue_watching_tap,
        searchTitleRes = Res.string.ss_continue_watching_click_title,
        searchSubtitleRes = Res.string.ss_continue_watching_click_subtitle,
        keywords = listOf("continue watching", "tap", "click", "resume", "play", "details"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.PlayerPlay,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.UNHIDE_CW,
        titleRes = Res.string.settings_unhide_continue_watching,
        searchTitleRes = Res.string.ss_unhide_cw_title,
        searchSubtitleRes = Res.string.ss_unhide_cw_subtitle,
        keywords = listOf("unhide", "continue watching", "hidden", "reset", "show"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.Eye,
    ))

/** The catalog projection of [HomeDisplayRowRecords]: the search faces + the shared category. */
internal val HomeDisplaySearchItems: List<SettingsSearchItem> = HomeDisplayRowRecords.toSearchItems(CoreUiRes.string.ss_cat_home)


/**
 * The display group's per-id declared row admissions — the single gate both
 * `homeDisplayScreenRowTotal` and HomeSettingsScreen's emission list read.
 * The seven config rows always render ([RowAdmission.Always]); `unhide_cw`
 * deliberately declares NO gate: it renders only while hidden continue-
 * watching items exist — a content-state condition with no admission
 * vocabulary — so the strict derivation excludes it and the screen adds the
 * +1 explicitly (the storage cache-used info row shape, preserved count).
 */
internal val HomeDisplayRowAdmissions: Map<String, RowAdmission> =
    HomeDisplayRowRecords.filter { it.id != HomeSettingsIds.UNHIDE_CW }.admissionsByAdvancedFlag()


/**
 * Settings-search items for the "Continue Watching & Next Up" group of
 * HomeSettingsScreen — Next Up behavior rows moved off Appearance.
 */
internal val HomeNextUpRowRecords = listOf(
    SettingsRowRecord(
        id = HomeSettingsIds.MERGE_CONTINUE_NEXT_UP,
        titleRes = Res.string.settings_merge_continue_next_up,
        searchTitleRes = Res.string.ss_merge_continue_next_up_title,
        searchSubtitleRes = Res.string.ss_merge_continue_next_up_subtitle,
        keywords = listOf("merge", "combine", "continue watching", "next up", "single row"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.LayersLinked,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.NEXT_UP_MAX_DAYS,
        titleRes = Res.string.settings_next_up_time_window,
        searchTitleRes = Res.string.ss_next_up_max_days_title,
        searchSubtitleRes = Res.string.ss_next_up_max_days_subtitle,
        keywords = listOf("next up", "days", "time window", "recent", "max days", "filter"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.CalendarTime,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.NEXT_UP_REWATCHING,
        titleRes = Res.string.settings_rewatching_next_up,
        searchTitleRes = Res.string.ss_next_up_rewatching_title,
        searchSubtitleRes = Res.string.ss_next_up_rewatching_subtitle,
        keywords = listOf("next up", "rewatching", "rewatch", "repeat"),
        route = Route.HomeSettings(),
        icon = Tabler.Outline.History,
    ))

/** The catalog projection of [HomeNextUpRowRecords]: the search faces + the shared category. */
internal val HomeNextUpSearchItems: List<SettingsSearchItem> = HomeNextUpRowRecords.toSearchItems(CoreUiRes.string.ss_cat_home)


/**
 * Settings-search items for the "Home Screen Layout" group of HomeSettingsScreen —
 * the drill-in rows moved off Appearance's layout group, plus the previously
 * unindexed Discover Rows row (its screen row existed with no catalog entry,
 * so it was unreachable from search).
 */
internal val HomeLayoutRowRecords = listOf(
    SettingsRowRecord(
        id = HomeSettingsIds.PINNED_HOME_SECTIONS,
        titleRes = Res.string.settings_pinned_home_sections,
        searchTitleRes = Res.string.ss_pinned_home_sections_title,
        searchSubtitleRes = Res.string.ss_pinned_home_sections_subtitle,
        keywords = listOf("pinned", "home", "collection", "playlist", "favorites", "genre", "studio", "shelf", "row"),
        route = Route.PinnedHomeSections(),
        icon = Tabler.Outline.Pinned,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.HOME_LAYOUT_PRESETS,
        titleRes = Res.string.settings_home_layout_presets,
        searchTitleRes = Res.string.ss_home_layout_presets_title,
        searchSubtitleRes = Res.string.ss_home_layout_presets_subtitle,
        keywords = listOf("preset", "layout", "home", "save", "load", "import", "export", "share", "reset", "backup", "configuration"),
        route = Route.HomeLayoutPresets(),
        icon = Tabler.Outline.Bookmarks,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.CONFIGURE_LIBRARIES,
        titleRes = Res.string.settings_configure_libraries,
        searchTitleRes = Res.string.ss_configure_libraries_title,
        searchSubtitleRes = Res.string.ss_configure_libraries_subtitle,
        keywords = listOf("library", "libraries", "latest", "recently", "added", "home", "row", "shelf", "hide", "show"),
        route = Route.LibraryHomeSections(),
        icon = Tabler.Outline.Folders,
    )
,
    SettingsRowRecord(
        id = HomeSettingsIds.DISCOVER_ROWS,
        titleRes = Res.string.settings_discover_rows,
        searchTitleRes = Res.string.ss_discover_rows_title,
        searchSubtitleRes = Res.string.ss_discover_rows_subtitle,
        keywords = listOf("discover", "rows", "custom", "filters", "seerr", "jellyfin", "shelves", "random", "dice", "build", "recommendations"),
        route = Route.DiscoverRows(),
        icon = Tabler.Outline.Compass,
    ))

/** The catalog projection of [HomeLayoutRowRecords]: the search faces + the shared category. */
internal val HomeLayoutSearchItems: List<SettingsSearchItem> = HomeLayoutRowRecords.toSearchItems(CoreUiRes.string.ss_cat_home)
