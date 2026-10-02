package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_accent_color_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_accent_color_title
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_style_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_style_title
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_appearance
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_color_blind_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_compact_episode_list
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_date_format
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_theming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_newsletter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size_app
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_handedness
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_haptic_feedback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_episode_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_search_history
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_layout_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_morning_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_bar_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_hide_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_delivery_day
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_prefer_logos
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_nav_labels
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_missing_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_share_media
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_special_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_style
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_filter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_filter_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_strength_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_color_blind_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_color_blind_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_compact_episode_list_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_contrast_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_contrast_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_date_format_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_date_format_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dynamic_theming_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dynamic_theming_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_font_scale_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_font_scale_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hand_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hand_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_haptics_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_haptics_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_episode_thumbnails_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_episode_thumbnails_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_search_history_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_search_history_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_library_view_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_library_view_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_layout_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_layout_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_bar_customization_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_bar_customization_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_hide_on_scroll_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_labels_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_labels_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_delivery_day_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_delivery_day_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_sections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_sections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_oled_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_oled_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_prefer_logos_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_prefer_logos_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reduce_motion_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reduce_motion_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_end_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_start_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_missing_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_missing_episodes_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_share_media_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_share_media_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_specials_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_specials_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_style_accent_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_style_accent_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_music_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_music_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_scheduler_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_scheduler_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_style_subtitle

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object AppearanceSettingsIds {
    const val DATE_FORMAT = "date_format"
    const val FONT_SCALE = "font_scale"
    const val COLOR_BLIND_MODE = "color_blind_mode"
    const val HAND_MODE = "hand_mode"
    const val THEME_SCHEDULER = "theme_scheduler"
    const val THEME_MODE = "theme_mode"
    const val THEME_STYLE = "theme_style"
    const val STYLE_ACCENT = "style_accent"
    const val DYNAMIC_THEMING = "dynamic_theming"
    const val OLED_MODE = "oled_mode"
    const val CONTRAST = "contrast"
    const val LIBRARY_VIEW_MODE = "library_view_mode"
    const val LAYOUT_MODE = "layout_mode"
    const val THEME_MUSIC = "theme_music"
    const val NAV_LABELS = "nav_labels"
    const val ACCENT_COLOR = "accent_color"
    const val COLOR_STYLE = "color_style"
    const val SCHEDULED_START = "scheduled_start"
    const val SCHEDULED_END = "scheduled_end"
    const val NAV_BAR_CUSTOMIZATION = "nav_bar_customization"
    const val NAV_HIDE_ON_SCROLL = "nav_hide_on_scroll"
    const val HIDE_EPISODE_THUMBNAILS = "hide_episode_thumbnails"
    const val COMPACT_EPISODE_LIST = "compact_episode_list"
    const val SKIP_SPECIALS = "skip_specials"
    const val SHOW_MISSING_EPISODES = "show_missing_episodes"
    const val PREFER_LOGOS = "prefer_logos"
    const val HAPTICS_ENABLED = "haptics_enabled"
    const val SHOW_SHARE_MEDIA = "show_share_media"
    const val HIDE_SEARCH_HISTORY = "hide_search_history"
    const val PERFORMANCE_MODE = "performance_mode"
    const val REDUCE_MOTION = "reduce_motion"
    const val BLUE_LIGHT_FILTER = "blue_light_filter"
    const val BLUE_LIGHT_STRENGTH = "blue_light_strength"
    const val NEWSLETTER_ENABLED = "newsletter_enabled"
    const val NEWSLETTER_DELIVERY_DAY = "newsletter_delivery_day"
    const val NEWSLETTER_SECTIONS = "newsletter_sections"
}

// ═══════════════════════════════════════════════════════════════════════
// The spec-derived derivation inputs: the
// appearance screen's searchable semantics live on the datastore-side spec
// declarations — this domain's own ([AppearancePreferenceSpecs]) plus the
// cross-domain knobs the library group renders (the experimental
// share-media / hide-search-history toggles). The record lists below stay
// the ordered spine — screen faces + the residual rows' hand search faces
// (the library/rows whose knobs live in the spec-less Library/Navigation/
// UserPreferences stores). (The home-discovery watch-state quartet moved to
// HomeSettingsSearchItems.kt's cards group — PS-4.)
// ═══════════════════════════════════════════════════════════════════════

private val searchRoutes: Map<String, Route> = mapOf(
    AppearancePreferenceSpecs.ROUTE_APPEARANCE_SETTINGS to Route.AppearanceSettings(),
)

private val appearanceSpecEntries: List<PreferenceSearchSpec> =
    AppearancePreferenceSpecs.searchEntries +
        ExperimentalPreferenceSpecs.appearanceSearchEntries

private fun specsFor(bindings: List<SettingsSearchBinding>): List<PreferenceSearchSpec> {
    val ids = bindings.map { it.id }.toSet()
    val matched = appearanceSpecEntries.filter { it.id in ids }
    val missing = ids - matched.map { it.id }.toSet()
    require(missing.isEmpty()) { "settings-search binding ids without a spec entry: $missing" }
    return matched
}

private val appearanceCategory = CoreUiRes.string.ss_cat_appearance

/**
 * The theme group's binding table — the search faces of its 17 spec-backed
 * rows (library_view_mode and nav_labels are the group's feature-side
 * residuals).
 */
private val appearanceThemeBindings = listOf(
    SettingsSearchBinding(AppearanceSettingsIds.DATE_FORMAT, Res.string.ss_date_format_title, Res.string.ss_date_format_subtitle, appearanceCategory, Tabler.Outline.Calendar),
    SettingsSearchBinding(AppearanceSettingsIds.FONT_SCALE, Res.string.ss_font_scale_title, Res.string.ss_font_scale_subtitle, appearanceCategory, Tabler.Outline.TextSize),
    SettingsSearchBinding(AppearanceSettingsIds.COLOR_BLIND_MODE, Res.string.ss_color_blind_mode_title, Res.string.ss_color_blind_mode_subtitle, appearanceCategory, Tabler.Outline.Eye),
    SettingsSearchBinding(AppearanceSettingsIds.HAND_MODE, Res.string.ss_hand_mode_title, Res.string.ss_hand_mode_subtitle, appearanceCategory, Tabler.Outline.HandClick),
    SettingsSearchBinding(AppearanceSettingsIds.THEME_SCHEDULER, Res.string.ss_theme_scheduler_title, Res.string.ss_theme_scheduler_subtitle, appearanceCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(AppearanceSettingsIds.THEME_MODE, Res.string.ss_theme_mode_title, Res.string.ss_theme_mode_subtitle, appearanceCategory, Tabler.Outline.Moon),
    // The search hit restates the row's screen title (the fold).
    SettingsSearchBinding(AppearanceSettingsIds.THEME_STYLE, Res.string.settings_theme_style, Res.string.ss_theme_style_subtitle, appearanceCategory, Tabler.Outline.Palette),
    SettingsSearchBinding(AppearanceSettingsIds.STYLE_ACCENT, Res.string.ss_style_accent_title, Res.string.ss_style_accent_subtitle, appearanceCategory, Tabler.Outline.Palette),
    SettingsSearchBinding(
        AppearanceSettingsIds.DYNAMIC_THEMING,
        Res.string.ss_dynamic_theming_title,
        Res.string.ss_dynamic_theming_subtitle,
        appearanceCategory,
        Tabler.Outline.Video,
        platforms = platformsForCapability(settingsCapabilities.supportsDynamicColor),
    ),
    SettingsSearchBinding(AppearanceSettingsIds.OLED_MODE, Res.string.ss_oled_mode_title, Res.string.ss_oled_mode_subtitle, appearanceCategory, Tabler.Outline.BrightnessHalf),
    SettingsSearchBinding(AppearanceSettingsIds.CONTRAST, Res.string.ss_contrast_title, Res.string.ss_contrast_subtitle, appearanceCategory, Tabler.Outline.Adjustments),
    SettingsSearchBinding(AppearanceSettingsIds.LAYOUT_MODE, Res.string.ss_layout_mode_title, Res.string.ss_layout_mode_subtitle, appearanceCategory, Tabler.Outline.Devices),
    SettingsSearchBinding(AppearanceSettingsIds.THEME_MUSIC, Res.string.ss_theme_music_title, Res.string.ss_theme_music_subtitle, appearanceCategory, Tabler.Outline.Music),
    SettingsSearchBinding(AppearanceSettingsIds.ACCENT_COLOR, CoreUiRes.string.core_ui_accent_color_title, CoreUiRes.string.core_ui_accent_color_subtitle, appearanceCategory, Tabler.Outline.Palette),
    SettingsSearchBinding(AppearanceSettingsIds.COLOR_STYLE, CoreUiRes.string.core_ui_color_style_title, CoreUiRes.string.core_ui_color_style_subtitle, appearanceCategory, Tabler.Outline.Palette),
    SettingsSearchBinding(AppearanceSettingsIds.SCHEDULED_START, Res.string.ss_scheduled_start_title, Res.string.ss_scheduled_start_subtitle, appearanceCategory, Tabler.Outline.Sunrise),
    SettingsSearchBinding(AppearanceSettingsIds.SCHEDULED_END, Res.string.ss_scheduled_end_title, Res.string.ss_scheduled_end_subtitle, appearanceCategory, Tabler.Outline.Sunset),
)

/**
 * Settings-search items for the "Theme" group (theme mode/style/accent, dynamic color, display and player-adjacent appearance rows) of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 *
 * Spec-derived for the 17 rows whose knobs live in the appearance store (or
 * are its standalone catalog facts); library_view_mode (LibraryStore) and
 * nav_labels (NavigationStore) stay feature-side residuals.
 */
internal val AppearanceThemeRowRecords = listOf(
    SettingsRowRecord(id = AppearanceSettingsIds.DATE_FORMAT, titleRes = Res.string.settings_date_format, icon = Tabler.Outline.Calendar),
    SettingsRowRecord(id = AppearanceSettingsIds.FONT_SCALE, titleRes = Res.string.settings_font_size_app, icon = Tabler.Outline.TextSize),
    SettingsRowRecord(id = AppearanceSettingsIds.COLOR_BLIND_MODE, titleRes = Res.string.settings_color_blind_mode, icon = Tabler.Outline.Eye),
    SettingsRowRecord(id = AppearanceSettingsIds.HAND_MODE, titleRes = Res.string.settings_handedness, icon = Tabler.Outline.HandClick),
    // The theme-scheduler highlight-alias row: its screen face IS the
    // theme_mode row (a search deep-link highlights through THEME_HIGHLIGHT_IDS).
    SettingsRowRecord(id = AppearanceSettingsIds.THEME_SCHEDULER, titleRes = Res.string.settings_theme_mode, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = AppearanceSettingsIds.THEME_MODE, titleRes = Res.string.settings_theme_mode, icon = Tabler.Outline.Moon),
    SettingsRowRecord(id = AppearanceSettingsIds.THEME_STYLE, titleRes = Res.string.settings_theme_style, icon = Tabler.Outline.Palette),
    // style_accent: the hand-built VariantAccentPicker (titled per-variant by
    // core_ui_variant_accent_title) — no single screen title resource.
    SettingsRowRecord(id = AppearanceSettingsIds.STYLE_ACCENT, titleRes = null, icon = Tabler.Outline.Palette),
    SettingsRowRecord(id = AppearanceSettingsIds.DYNAMIC_THEMING, titleRes = Res.string.settings_dynamic_theming, icon = Tabler.Outline.Video),
    SettingsRowRecord(id = AppearanceSettingsIds.OLED_MODE, titleRes = Res.string.settings_oled_mode, icon = Tabler.Outline.BrightnessHalf),
    SettingsRowRecord(id = AppearanceSettingsIds.CONTRAST, titleRes = Res.string.settings_contrast, icon = Tabler.Outline.Adjustments),
    // ── RESIDUAL row (LibraryStore — no spec machinery).
    SettingsRowRecord(
        id = AppearanceSettingsIds.LIBRARY_VIEW_MODE,
        titleRes = Res.string.settings_library_view_mode,
        searchTitleRes = Res.string.ss_library_view_mode_title,
        searchSubtitleRes = Res.string.ss_library_view_mode_subtitle,
        keywords = listOf("library", "view", "grid", "list", "layout"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayoutGrid,
        isAdvanced = true,
    ),
    SettingsRowRecord(id = AppearanceSettingsIds.LAYOUT_MODE, titleRes = Res.string.settings_layout_mode, icon = Tabler.Outline.Devices),
    SettingsRowRecord(id = AppearanceSettingsIds.THEME_MUSIC, titleRes = Res.string.settings_backdrop_theme_music, icon = Tabler.Outline.Music),
    // ── RESIDUAL row (NavigationStore — no spec machinery).
    SettingsRowRecord(
        id = AppearanceSettingsIds.NAV_LABELS,
        titleRes = Res.string.settings_show_nav_labels,
        searchTitleRes = Res.string.ss_nav_labels_title,
        searchSubtitleRes = Res.string.ss_nav_labels_subtitle,
        keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.TextSize,
        isAdvanced = true,
    ),
    SettingsRowRecord(id = AppearanceSettingsIds.ACCENT_COLOR, titleRes = CoreUiRes.string.core_ui_accent_color_title, icon = Tabler.Outline.Palette),
    SettingsRowRecord(id = AppearanceSettingsIds.COLOR_STYLE, titleRes = CoreUiRes.string.core_ui_color_style_title, icon = Tabler.Outline.Palette),
    SettingsRowRecord(id = AppearanceSettingsIds.SCHEDULED_START, titleRes = Res.string.settings_night_starts_at, icon = Tabler.Outline.Sunrise),
    SettingsRowRecord(id = AppearanceSettingsIds.SCHEDULED_END, titleRes = Res.string.settings_morning_starts_at, icon = Tabler.Outline.Sunset),
)

/** The catalog projection of the spec-backed theme rows + the residuals. */
internal val AppearanceThemeSearchItems: List<SettingsSearchItem> =
    AppearanceThemeRowRecords.toSearchItems(
        specEntries = specsFor(appearanceThemeBindings),
        bindings = appearanceThemeBindings,
        routes = searchRoutes,
        categoryRes = appearanceCategory,
    )


/**
 * Settings-search items for the navigation-customization group rendered by NavigationCustomizationGroup of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the navigation knobs live in the spec-less NavigationStore —
 * these rows stay feature-side records until that store migrates.
 */
internal val AppearanceNavigationRowRecords = listOf(
    SettingsRowRecord(
        id = AppearanceSettingsIds.NAV_BAR_CUSTOMIZATION,
        titleRes = Res.string.settings_nav_bar_title,
        searchTitleRes = Res.string.ss_nav_bar_customization_title,
        searchSubtitleRes = Res.string.ss_nav_bar_customization_subtitle,
        keywords = listOf("navigation", "bar", "items", "bottom", "reorder", "hide", "show", "tabs", "home", "library", "search", "live tv", "browse", "shortcuts", "customize"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayoutGrid
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.NAV_HIDE_ON_SCROLL,
        titleRes = Res.string.settings_nav_hide_on_scroll,
        searchSubtitleRes = Res.string.ss_nav_hide_on_scroll_subtitle,
        keywords = listOf("navigation", "hide", "scroll", "auto hide", "bottom bar", "collapsible"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff
    ))

/** The catalog projection of `AppearanceNavigationRowRecords`: the search faces + the shared category. */
internal val AppearanceNavigationSearchItems: List<SettingsSearchItem> = AppearanceNavigationRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)


/**
 * The "Library & Cards" group's binding table — the search faces of its 3
 * spec-backed rows (the appearance haptics toggle and the experimental
 * share-media / hide-search-history toggles). The five library-content rows
 * are the group's feature-side residuals. (The home-discovery watch-state
 * quartet moved to HomeSettingsSearchItems.kt's cards group — PS-4.)
 */
private val appearanceLibraryBindings = listOf(
    SettingsSearchBinding(AppearanceSettingsIds.HAPTICS_ENABLED, Res.string.ss_haptics_enabled_title, Res.string.ss_haptics_enabled_subtitle, appearanceCategory, Tabler.Outline.DeviceMobileVibration),
    SettingsSearchBinding(AppearanceSettingsIds.SHOW_SHARE_MEDIA, Res.string.ss_show_share_media_title, Res.string.ss_show_share_media_subtitle, appearanceCategory, Tabler.Outline.Share),
    SettingsSearchBinding(AppearanceSettingsIds.HIDE_SEARCH_HISTORY, Res.string.ss_hide_search_history_title, Res.string.ss_hide_search_history_subtitle, appearanceCategory, Tabler.Outline.EyeOff),
)

/**
 * Settings-search items for the "Library & Cards" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 *
 * Spec-derived for the haptics toggle (appearance store) and the
 * share-media / hide-search-history toggles (experimental store); the five
 * library-content rows stay feature-side residuals (LibraryStore — no spec
 * machinery). (The home-discovery watch-state quartet moved to
 * HomeSettingsSearchItems.kt's cards group — PS-4.)
 */
internal val AppearanceLibraryRowRecords = listOf(
    // ── RESIDUAL rows (LibraryStore — no spec machinery).
    SettingsRowRecord(
        id = AppearanceSettingsIds.HIDE_EPISODE_THUMBNAILS,
        titleRes = Res.string.settings_hide_episode_thumbnails,
        searchTitleRes = Res.string.ss_hide_episode_thumbnails_title,
        searchSubtitleRes = Res.string.ss_hide_episode_thumbnails_subtitle,
        keywords = listOf("hide", "episode", "thumbnail", "spoiler", "preview"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PhotoOff,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = AppearanceSettingsIds.COMPACT_EPISODE_LIST,
        titleRes = Res.string.settings_compact_episode_list,
        searchSubtitleRes = Res.string.ss_compact_episode_list_subtitle,
        keywords = listOf("episode", "list", "compact", "vertical", "layout", "rows", "dense"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.List
    ),
    SettingsRowRecord(
        id = AppearanceSettingsIds.SKIP_SPECIALS,
        titleRes = Res.string.settings_skip_special_episodes,
        searchTitleRes = Res.string.ss_skip_specials_title,
        searchSubtitleRes = Res.string.ss_skip_specials_subtitle,
        keywords = listOf("skip", "special", "episode", "bonus", "exclude"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = AppearanceSettingsIds.SHOW_MISSING_EPISODES,
        titleRes = Res.string.settings_show_missing_episodes,
        searchTitleRes = Res.string.ss_show_missing_episodes_title,
        searchSubtitleRes = Res.string.ss_show_missing_episodes_subtitle,
        keywords = listOf("missing", "episode", "unaired", "virtual", "placeholder", "season", "show"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = AppearanceSettingsIds.PREFER_LOGOS,
        titleRes = Res.string.settings_prefer_logos,
        searchTitleRes = Res.string.ss_prefer_logos_title,
        searchSubtitleRes = Res.string.ss_prefer_logos_subtitle,
        keywords = listOf("logo", "clear logo", "title", "artwork", "image", "banner", "details", "prefer"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Photo
    ),
    SettingsRowRecord(id = AppearanceSettingsIds.HAPTICS_ENABLED, titleRes = Res.string.settings_haptic_feedback, icon = Tabler.Outline.DeviceMobileVibration),
    SettingsRowRecord(id = AppearanceSettingsIds.SHOW_SHARE_MEDIA, titleRes = Res.string.settings_show_share_media, icon = Tabler.Outline.Share),
    SettingsRowRecord(id = AppearanceSettingsIds.HIDE_SEARCH_HISTORY, titleRes = Res.string.settings_hide_search_history, icon = Tabler.Outline.EyeOff),
)

/** The catalog projection of the spec-backed library rows + the residuals. */
internal val AppearanceLibrarySearchItems: List<SettingsSearchItem> =
    AppearanceLibraryRowRecords.toSearchItems(
        specEntries = specsFor(appearanceLibraryBindings),
        bindings = appearanceLibraryBindings,
        routes = searchRoutes,
        categoryRes = appearanceCategory,
    )


/**
 * The advanced-gated "Performance" group's binding table (both rows
 * spec-backed by the appearance store).
 */
private val appearancePerformanceBindings = listOf(
    SettingsSearchBinding(AppearanceSettingsIds.PERFORMANCE_MODE, Res.string.ss_performance_mode_title, Res.string.ss_performance_mode_subtitle, appearanceCategory, Tabler.Outline.Gauge),
    SettingsSearchBinding(AppearanceSettingsIds.REDUCE_MOTION, Res.string.ss_reduce_motion_title, Res.string.ss_reduce_motion_subtitle, appearanceCategory, Tabler.Outline.Activity),
)

/**
 * Settings-search items for the advanced-gated "Performance" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearancePerformanceRowRecords = listOf(
    SettingsRowRecord(id = AppearanceSettingsIds.PERFORMANCE_MODE, titleRes = Res.string.settings_performance_mode, icon = Tabler.Outline.Gauge),
    SettingsRowRecord(id = AppearanceSettingsIds.REDUCE_MOTION, titleRes = Res.string.settings_reduce_motion, icon = Tabler.Outline.Activity),
)

/** The catalog projection of the spec-derived performance rows. */
internal val AppearancePerformanceSearchItems: List<SettingsSearchItem> =
    AppearancePerformanceRowRecords.toSearchItems(
        specEntries = specsFor(appearancePerformanceBindings),
        bindings = appearancePerformanceBindings,
        routes = searchRoutes,
        categoryRes = appearanceCategory,
    )


/** The advanced-gated "Eye Care" group's binding table (both rows spec-backed). */
private val appearanceEyeCareBindings = listOf(
    SettingsSearchBinding(AppearanceSettingsIds.BLUE_LIGHT_FILTER, Res.string.ss_blue_light_filter_title, Res.string.ss_blue_light_filter_subtitle, appearanceCategory, Tabler.Outline.Moon),
    SettingsSearchBinding(AppearanceSettingsIds.BLUE_LIGHT_STRENGTH, Res.string.ss_blue_light_strength_title, Res.string.ss_blue_light_strength_subtitle, appearanceCategory, Tabler.Outline.Adjustments),
)

/**
 * Settings-search items for the advanced-gated "Eye Care" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceEyeCareRowRecords = listOf(
    SettingsRowRecord(id = AppearanceSettingsIds.BLUE_LIGHT_FILTER, titleRes = Res.string.settings_blue_light_filter, icon = Tabler.Outline.Moon),
    SettingsRowRecord(id = AppearanceSettingsIds.BLUE_LIGHT_STRENGTH, titleRes = Res.string.settings_blue_light_filter_strength, icon = Tabler.Outline.Adjustments),
)

/** The catalog projection of the spec-derived eye-care rows. */
internal val AppearanceEyeCareSearchItems: List<SettingsSearchItem> =
    AppearanceEyeCareRowRecords.toSearchItems(
        specEntries = specsFor(appearanceEyeCareBindings),
        bindings = appearanceEyeCareBindings,
        routes = searchRoutes,
        categoryRes = appearanceCategory,
    )


/**
 * Settings-search items for the advanced-gated "Newsletter" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the newsletter knobs live in spec-less stores — these rows
 * stay feature-side records until their store migrates.
 */
internal val AppearanceNewsletterRowRecords = listOf(
    SettingsRowRecord(
        id = AppearanceSettingsIds.NEWSLETTER_ENABLED,
        titleRes = Res.string.settings_enable_newsletter,
        searchTitleRes = Res.string.ss_newsletter_enabled_title,
        searchSubtitleRes = Res.string.ss_newsletter_enabled_subtitle,
        keywords = listOf("newsletter", "digest", "email", "periodic", "report"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Mail,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.NEWSLETTER_DELIVERY_DAY,
        titleRes = Res.string.settings_newsletter_delivery_day,
        searchTitleRes = Res.string.ss_newsletter_delivery_day_title,
        searchSubtitleRes = Res.string.ss_newsletter_delivery_day_subtitle,
        keywords = listOf("newsletter", "delivery", "day", "schedule", "weekday", "send"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Calendar,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.NEWSLETTER_SECTIONS,
        titleRes = null,
        searchTitleRes = Res.string.ss_newsletter_sections_title,
        searchSubtitleRes = Res.string.ss_newsletter_sections_subtitle,
        keywords = listOf("newsletter", "sections", "recently added", "activity log", "library stats", "continue watching", "next up", "curated picks", "content", "digest"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Mail,
        isAdvanced = true
    ))

/** The catalog projection of `AppearanceNewsletterRowRecords`: the search faces + the shared category. */
internal val AppearanceNewsletterSearchItems: List<SettingsSearchItem> = AppearanceNewsletterRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)

// ── Declared row admissions (the gates the screen totals AND emissions read) ──

/**
 * The theme group's declared admissions: every row rides its declared
 * advanced flag ([SettingsSearchItem.isAdvanced] — from the spec
 * declarations for the spec-derived rows, from the residual records
 * otherwise) EXCEPT the ids in [AppearanceThemeContentGatedIds] (content-
 * gated screen rows) and [AppearanceSettingsIds.THEME_SCHEDULER] (the
 * highlight-alias row — see below). Both stay undeclared, so the strict
 * [rowTotalFor] counts neither — the shipped count semantics (the hand-built
 * `appearanceItems` list never contained either). The contract test's strict
 * per-id ratchet pins this exception set; the screen's
 * [com.raulshma.jellyplay.feature.settings.appearanceThemeScreenRowTotal]
 * adds the content-gated rows back as explicit content terms (the
 * home.display unhide-row precedent).
 */
internal val AppearanceThemeContentGatedIds: Set<String> = setOf(
    AppearanceSettingsIds.STYLE_ACCENT,
    AppearanceSettingsIds.ACCENT_COLOR,
    AppearanceSettingsIds.COLOR_STYLE,
    AppearanceSettingsIds.DYNAMIC_THEMING,
    AppearanceSettingsIds.OLED_MODE,
    AppearanceSettingsIds.LAYOUT_MODE,
    AppearanceSettingsIds.SCHEDULED_START,
    AppearanceSettingsIds.SCHEDULED_END,
)

/**
 * `theme_scheduler`'s screen face is the theme_mode row itself — a search
 * deep-link to it highlights through [THEME_HIGHLIGHT_IDS] — so it declares
 * no admission and is counted by no total (the shipped count never contained
 * it; the security group's `pin_for_player_lock` quirk shape).
 */
internal val AppearanceThemeAliasRowId: String = AppearanceSettingsIds.THEME_SCHEDULER

/** The theme group's declared admissions — see [AppearanceThemeContentGatedIds]. */
internal val AppearanceThemeRowAdmissions: Map<String, RowAdmission> =
    AppearanceThemeSearchItems
        .filter { it.id !in AppearanceThemeContentGatedIds && it.id != AppearanceThemeAliasRowId }
        .admissionsByAdvancedFlag()

/**
 * The "Library & Cards" group's admissions: every declared row renders
 * unconditionally (the shipped always-on behavior — the rows declare most
 * of them advanced, yet the group predates and outlives the advanced gate,
 * the language high-contrast shipped quirk), so each id states
 * [RowAdmission.Always] explicitly instead of riding its advanced base.
 */
internal val AppearanceLibraryRowAdmissions: Map<String, RowAdmission> =
    AppearanceLibraryRowRecords.associate { it.id to RowAdmission.Always }

/** The advanced-gated performance group: both rows ride the advanced toggle (the group only composes behind it). */
internal val AppearancePerformanceRowAdmissions: Map<String, RowAdmission> =
    AppearancePerformanceSearchItems.admissionsByAdvancedFlag()

/** The advanced-gated eye-care group: both rows ride the advanced toggle (the group only composes behind it). */
internal val AppearanceEyeCareRowAdmissions: Map<String, RowAdmission> =
    AppearanceEyeCareSearchItems.admissionsByAdvancedFlag()

/**
 * The advanced-gated newsletter group: the three declared rows ride the
 * advanced toggle. `newsletter_sections`'s declared row renders as the
 * runtime-reorderable per-section rows (the media-segment enum-driven shape),
 * which the screen's total accounts for explicitly.
 */
internal val AppearanceNewsletterRowAdmissions: Map<String, RowAdmission> =
    AppearanceNewsletterRowRecords.admissionsByAdvancedFlag()
