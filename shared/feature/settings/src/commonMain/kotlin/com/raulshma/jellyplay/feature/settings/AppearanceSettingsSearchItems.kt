package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_date_format
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_theming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_newsletter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size_app
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_handedness
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_haptic_feedback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_episode_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_search_history
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_morning_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_bar_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_hide_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_delivery_day
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_nav_labels
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_share_media
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_special_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_filter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_filter_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_strength_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_color_blind_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_color_blind_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_compact_episode_list_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_compact_episode_list_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_configure_libraries_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_configure_libraries_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hero_section_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hero_section_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_episode_thumbnails_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_episode_thumbnails_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_search_history_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_search_history_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_library_view_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_library_view_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_bar_customization_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_bar_customization_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_hide_on_scroll_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_hide_on_scroll_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_labels_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_labels_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_delivery_day_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_delivery_day_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_sections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_sections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_oled_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_oled_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reduce_motion_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reduce_motion_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_end_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_start_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_external_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_external_ratings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_share_media_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_share_media_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_unwatched_badge_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_unwatched_badge_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_watched_checkmark_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_watched_checkmark_title
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_style_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_unhide_cw_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_unhide_cw_title

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object AppearanceSettingsIds {
    const val SETTINGS_IN_HOME_SEARCH = "settings_in_home_search"
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
    const val HOME_MODE = "home_mode"
    const val HERO_SECTION = "hero_section"
    const val HOME_BACKDROP = "home_backdrop"
    const val CLOCK_HOME = "clock_home"
    const val HIDE_TOP_HEADER = "hide_top_header"
    const val CONTINUE_WATCHING_CLICK = "continue_watching_click"
    const val UNHIDE_CW = "unhide_cw"
    const val MERGE_CONTINUE_NEXT_UP = "merge_continue_next_up"
    const val NEXT_UP_MAX_DAYS = "next_up_max_days"
    const val NEXT_UP_REWATCHING = "next_up_rewatching"
    const val THEME_MUSIC = "theme_music"
    const val NAV_LABELS = "nav_labels"
    const val ACCENT_COLOR = "accent_color"
    const val COLOR_STYLE = "color_style"
    const val SCHEDULED_START = "scheduled_start"
    const val SCHEDULED_END = "scheduled_end"
    const val NAV_BAR_CUSTOMIZATION = "nav_bar_customization"
    const val NAV_HIDE_ON_SCROLL = "nav_hide_on_scroll"
    const val SHOW_UNWATCHED_BADGE = "show_unwatched_badge"
    const val SHOW_WATCHED_CHECKMARK = "show_watched_checkmark"
    const val HIDE_WATCHED_ITEMS = "hide_watched_items"
    const val HIDE_EPISODE_THUMBNAILS = "hide_episode_thumbnails"
    const val COMPACT_EPISODE_LIST = "compact_episode_list"
    const val SKIP_SPECIALS = "skip_specials"
    const val HAPTICS_ENABLED = "haptics_enabled"
    const val SHOW_SHARE_MEDIA = "show_share_media"
    const val SHOW_EXTERNAL_RATINGS = "show_external_ratings"
    const val HIDE_SEARCH_HISTORY = "hide_search_history"
    const val PINNED_HOME_SECTIONS = "pinned_home_sections"
    const val HOME_LAYOUT_PRESETS = "home_layout_presets"
    const val CONFIGURE_LIBRARIES = "configure_libraries"
    const val PERFORMANCE_MODE = "performance_mode"
    const val REDUCE_MOTION = "reduce_motion"
    const val BLUE_LIGHT_FILTER = "blue_light_filter"
    const val BLUE_LIGHT_STRENGTH = "blue_light_strength"
    const val NEWSLETTER_ENABLED = "newsletter_enabled"
    const val NEWSLETTER_DELIVERY_DAY = "newsletter_delivery_day"
    const val NEWSLETTER_SECTIONS = "newsletter_sections"
}

/**
 * Settings-search items for the "Theme" group (theme mode/style/accent, dynamic color, display and player-adjacent appearance rows) of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceThemeRowRecords = listOf(
    SettingsRowRecord(
        id = AppearanceSettingsIds.SETTINGS_IN_HOME_SEARCH,
        titleRes = Res.string.settings_show_settings_in_home_search,
        searchTitleRes = Res.string.ss_settings_in_home_search_title,
        searchSubtitleRes = Res.string.ss_settings_in_home_search_subtitle,
        keywords = listOf("search", "settings", "home", "find", "discover", "quick", "shortcut"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.DATE_FORMAT,
        titleRes = Res.string.settings_date_format,
        searchTitleRes = Res.string.ss_date_format_title,
        searchSubtitleRes = Res.string.ss_date_format_subtitle,
        keywords = listOf("date", "format", "time", "calendar", "day", "month", "year", "display"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Calendar,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.FONT_SCALE,
        titleRes = Res.string.settings_font_size_app,
        searchTitleRes = Res.string.ss_font_scale_title,
        searchSubtitleRes = Res.string.ss_font_scale_subtitle,
        keywords = listOf("font", "size", "text", "scale", "accessibility", "readability", "large", "small"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.TextSize,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.COLOR_BLIND_MODE,
        titleRes = Res.string.settings_color_blind_mode,
        searchTitleRes = Res.string.ss_color_blind_mode_title,
        searchSubtitleRes = Res.string.ss_color_blind_mode_subtitle,
        keywords = listOf("color", "blind", "daltonize", "accessibility", "protanopia", "deuteranopia", "tritanopia", "vision"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HAND_MODE,
        titleRes = Res.string.settings_handedness,
        searchTitleRes = Res.string.ss_hand_mode_title,
        searchSubtitleRes = Res.string.ss_hand_mode_subtitle,
        keywords = listOf("hand", "left", "right", "handed", "accessibility", "mirror", "one-handed"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.HandClick,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.THEME_SCHEDULER,
        titleRes = Res.string.settings_theme_mode,
        searchTitleRes = Res.string.ss_theme_scheduler_title,
        searchSubtitleRes = Res.string.ss_theme_scheduler_subtitle,
        keywords = listOf("theme", "scheduler", "day", "night", "auto", "time", "scheduled", "dark", "light"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.THEME_MODE,
        titleRes = Res.string.settings_theme_mode,
        searchTitleRes = Res.string.ss_theme_mode_title,
        searchSubtitleRes = Res.string.ss_theme_mode_subtitle,
        keywords = listOf("theme", "mode", "light", "dark", "system", "black"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Moon
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.THEME_STYLE,
        titleRes = Res.string.settings_theme_style,
        searchTitleRes = Res.string.ss_theme_style_title,
        searchSubtitleRes = Res.string.ss_theme_style_subtitle,
        keywords = listOf("theme", "style", "variant", "synthwave", "soothing", "monochrome", "vivid", "aurora", "sakura", "vector", "pop", "pastel", "neon", "retro", "look"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.STYLE_ACCENT,
        titleRes = null,
        searchTitleRes = Res.string.ss_style_accent_title,
        searchSubtitleRes = Res.string.ss_style_accent_subtitle,
        keywords = listOf("accent", "color", "swatch", "synthwave", "soothing", "vivid", "aurora", "sakura", "vector", "neon", "theme"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.DYNAMIC_THEMING,
        titleRes = Res.string.settings_dynamic_theming,
        searchTitleRes = Res.string.ss_dynamic_theming_title,
        searchSubtitleRes = Res.string.ss_dynamic_theming_subtitle,
        keywords = listOf("dynamic", "artwork", "colors", "theme", "wallpaper"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Video,
        platforms = platformsForCapability(settingsCapabilities.supportsDynamicColor),
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.OLED_MODE,
        titleRes = Res.string.settings_oled_mode,
        searchTitleRes = Res.string.ss_oled_mode_title,
        searchSubtitleRes = Res.string.ss_oled_mode_subtitle,
        keywords = listOf("oled", "black", "amoled", "pure black", "battery"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.BrightnessHalf
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.CONTRAST,
        titleRes = Res.string.settings_contrast,
        searchTitleRes = Res.string.ss_contrast_title,
        searchSubtitleRes = Res.string.ss_contrast_subtitle,
        keywords = listOf("contrast", "accessibility", "legibility", "readability"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.LIBRARY_VIEW_MODE,
        titleRes = Res.string.settings_library_view_mode,
        searchTitleRes = Res.string.ss_library_view_mode_title,
        searchSubtitleRes = Res.string.ss_library_view_mode_subtitle,
        keywords = listOf("library", "view", "grid", "list", "layout"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayoutGrid,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HOME_MODE,
        titleRes = Res.string.settings_home_mode,
        searchTitleRes = Res.string.ss_home_mode_title,
        searchSubtitleRes = Res.string.ss_home_mode_subtitle,
        keywords = listOf("home", "layout", "mode", "video", "music"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Home,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HERO_SECTION,
        titleRes = Res.string.settings_show_hero_section,
        searchTitleRes = Res.string.ss_hero_section_title,
        searchSubtitleRes = Res.string.ss_hero_section_subtitle,
        keywords = listOf("hero", "banner", "featured", "home", "carousel"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayersLinked,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HOME_BACKDROP,
        titleRes = Res.string.settings_home_backdrop,
        searchTitleRes = Res.string.ss_home_backdrop_title,
        searchSubtitleRes = Res.string.ss_home_backdrop_subtitle,
        keywords = listOf("home", "backdrop", "artwork", "background", "blur", "wallpaper"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.CLOCK_HOME,
        titleRes = Res.string.settings_show_clock_home,
        searchTitleRes = Res.string.ss_clock_home_title,
        searchSubtitleRes = Res.string.ss_clock_home_subtitle,
        keywords = listOf("clock", "time", "home", "wall", "current"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HIDE_TOP_HEADER,
        titleRes = Res.string.settings_hide_top_header_on_scroll,
        searchTitleRes = Res.string.ss_hide_top_header_title,
        searchSubtitleRes = Res.string.ss_hide_top_header_subtitle,
        keywords = listOf("top header", "app bar", "home bar", "hide", "scroll", "auto hide", "collapse", "dock"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.ArrowBarToDown,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.CONTINUE_WATCHING_CLICK,
        titleRes = Res.string.settings_continue_watching_tap,
        searchTitleRes = Res.string.ss_continue_watching_click_title,
        searchSubtitleRes = Res.string.ss_continue_watching_click_subtitle,
        keywords = listOf("continue watching", "tap", "click", "resume", "play", "details"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PlayerPlay,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.UNHIDE_CW,
        titleRes = Res.string.settings_unhide_continue_watching,
        searchTitleRes = Res.string.ss_unhide_cw_title,
        searchSubtitleRes = Res.string.ss_unhide_cw_subtitle,
        keywords = listOf("unhide", "continue watching", "hidden", "reset", "show"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.MERGE_CONTINUE_NEXT_UP,
        titleRes = Res.string.settings_merge_continue_next_up,
        searchTitleRes = Res.string.ss_merge_continue_next_up_title,
        searchSubtitleRes = Res.string.ss_merge_continue_next_up_subtitle,
        keywords = listOf("merge", "combine", "continue watching", "next up", "single row"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayersLinked,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.NEXT_UP_MAX_DAYS,
        titleRes = Res.string.settings_next_up_time_window,
        searchTitleRes = Res.string.ss_next_up_max_days_title,
        searchSubtitleRes = Res.string.ss_next_up_max_days_subtitle,
        keywords = listOf("next up", "days", "time window", "recent", "max days", "filter"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.CalendarTime,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.NEXT_UP_REWATCHING,
        titleRes = Res.string.settings_rewatching_next_up,
        searchTitleRes = Res.string.ss_next_up_rewatching_title,
        searchSubtitleRes = Res.string.ss_next_up_rewatching_subtitle,
        keywords = listOf("next up", "rewatching", "rewatch", "rewatch", "repeat"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.History,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.THEME_MUSIC,
        titleRes = Res.string.settings_backdrop_theme_music,
        searchTitleRes = Res.string.ss_theme_music_title,
        searchSubtitleRes = Res.string.ss_theme_music_subtitle,
        keywords = listOf("theme", "music", "backdrop", "ambience", "song", "score"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.NAV_LABELS,
        titleRes = Res.string.settings_show_nav_labels,
        searchTitleRes = Res.string.ss_nav_labels_title,
        searchSubtitleRes = Res.string.ss_nav_labels_subtitle,
        keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.TextSize,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.ACCENT_COLOR,
        titleRes = CoreUiRes.string.core_ui_accent_color_title,
        searchTitleRes = CoreUiRes.string.core_ui_accent_color_title,
        searchSubtitleRes = CoreUiRes.string.core_ui_accent_color_subtitle,
        keywords = listOf("accent", "color", "theme", "swatch", "palette", "customize"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.COLOR_STYLE,
        titleRes = CoreUiRes.string.core_ui_color_style_title,
        searchTitleRes = CoreUiRes.string.core_ui_color_style_title,
        searchSubtitleRes = CoreUiRes.string.core_ui_color_style_subtitle,
        keywords = listOf("color style", "palette", "vibe", "generated", "mood", "theme"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.SCHEDULED_START,
        titleRes = Res.string.settings_night_starts_at,
        searchTitleRes = Res.string.ss_scheduled_start_title,
        searchSubtitleRes = Res.string.ss_scheduled_start_subtitle,
        keywords = listOf("theme", "schedule", "start", "hour", "day", "auto"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Sunrise,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.SCHEDULED_END,
        titleRes = Res.string.settings_morning_starts_at,
        searchTitleRes = Res.string.ss_scheduled_end_title,
        searchSubtitleRes = Res.string.ss_scheduled_end_subtitle,
        keywords = listOf("theme", "schedule", "end", "hour", "night", "auto"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Sunset,
        isAdvanced = true
    ))

/** The catalog projection of `AppearanceThemeRowRecords`: the search faces + the shared category. */
internal val AppearanceThemeSearchItems: List<SettingsSearchItem> = AppearanceThemeRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)


/**
 * Settings-search items for the navigation-customization group rendered by NavigationCustomizationGroup of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
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
        searchTitleRes = Res.string.ss_nav_hide_on_scroll_title,
        searchSubtitleRes = Res.string.ss_nav_hide_on_scroll_subtitle,
        keywords = listOf("navigation", "hide", "scroll", "auto hide", "bottom bar", "collapsible"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff
    ))

/** The catalog projection of `AppearanceNavigationRowRecords`: the search faces + the shared category. */
internal val AppearanceNavigationSearchItems: List<SettingsSearchItem> = AppearanceNavigationRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)


/**
 * Settings-search items for the "Library & Cards" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceLibraryRowRecords = listOf(
    SettingsRowRecord(
        id = AppearanceSettingsIds.SHOW_UNWATCHED_BADGE,
        titleRes = Res.string.settings_show_unwatched_badge,
        searchTitleRes = Res.string.ss_show_unwatched_badge_title,
        searchSubtitleRes = Res.string.ss_show_unwatched_badge_subtitle,
        keywords = listOf("unwatched", "badge", "indicator", "new", "marker"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Folder,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.SHOW_WATCHED_CHECKMARK,
        titleRes = Res.string.settings_show_watched_checkmark,
        searchTitleRes = Res.string.ss_show_watched_checkmark_title,
        searchSubtitleRes = Res.string.ss_show_watched_checkmark_subtitle,
        keywords = listOf("watched", "checkmark", "badge", "indicator", "finished"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.CircleCheck,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HIDE_WATCHED_ITEMS,
        titleRes = Res.string.settings_hide_watched_items,
        searchTitleRes = Res.string.ss_hide_watched_items_title,
        searchSubtitleRes = Res.string.ss_hide_watched_items_subtitle,
        keywords = listOf("hide", "watched", "filter", "library", "clean"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HIDE_EPISODE_THUMBNAILS,
        titleRes = Res.string.settings_hide_episode_thumbnails,
        searchTitleRes = Res.string.ss_hide_episode_thumbnails_title,
        searchSubtitleRes = Res.string.ss_hide_episode_thumbnails_subtitle,
        keywords = listOf("hide", "episode", "thumbnail", "spoiler", "preview"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PhotoOff,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.COMPACT_EPISODE_LIST,
        titleRes = Res.string.settings_compact_episode_list,
        searchTitleRes = Res.string.ss_compact_episode_list_title,
        searchSubtitleRes = Res.string.ss_compact_episode_list_subtitle,
        keywords = listOf("episode", "list", "compact", "vertical", "layout", "rows", "dense"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.List
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.SKIP_SPECIALS,
        titleRes = Res.string.settings_skip_special_episodes,
        searchTitleRes = Res.string.ss_skip_specials_title,
        searchSubtitleRes = Res.string.ss_skip_specials_subtitle,
        keywords = listOf("skip", "special", "episode", "bonus", "exclude"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HAPTICS_ENABLED,
        titleRes = Res.string.settings_haptic_feedback,
        searchTitleRes = Res.string.ss_haptics_enabled_title,
        searchSubtitleRes = Res.string.ss_haptics_enabled_subtitle,
        keywords = listOf("haptic", "vibration", "feedback", "vibrate", "touch"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.DeviceMobileVibration,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.SHOW_SHARE_MEDIA,
        titleRes = Res.string.settings_show_share_media,
        searchTitleRes = Res.string.ss_show_share_media_title,
        searchSubtitleRes = Res.string.ss_show_share_media_subtitle,
        keywords = listOf("share", "media", "send", "details"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Share,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.SHOW_EXTERNAL_RATINGS,
        titleRes = Res.string.settings_show_external_ratings,
        searchTitleRes = Res.string.ss_show_external_ratings_title,
        searchSubtitleRes = Res.string.ss_show_external_ratings_subtitle,
        keywords = listOf("ratings", "imdb", "tmdb", "critic", "score", "star"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Star,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HIDE_SEARCH_HISTORY,
        titleRes = Res.string.settings_hide_search_history,
        searchTitleRes = Res.string.ss_hide_search_history_title,
        searchSubtitleRes = Res.string.ss_hide_search_history_subtitle,
        keywords = listOf("search", "history", "hide", "privacy", "recent"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff,
        isAdvanced = true
    ))

/** The catalog projection of `AppearanceLibraryRowRecords`: the search faces + the shared category. */
internal val AppearanceLibrarySearchItems: List<SettingsSearchItem> = AppearanceLibraryRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)


/**
 * Settings-search items for the "Home Screen Layout" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceHomeLayoutRowRecords = listOf(
    SettingsRowRecord(
        id = AppearanceSettingsIds.PINNED_HOME_SECTIONS,
        titleRes = Res.string.settings_pinned_home_sections,
        searchTitleRes = Res.string.ss_pinned_home_sections_title,
        searchSubtitleRes = Res.string.ss_pinned_home_sections_subtitle,
        keywords = listOf("pinned", "home", "collection", "playlist", "favorites", "genre", "studio", "shelf", "row"),
        route = Route.PinnedHomeSections(),
        icon = Tabler.Outline.Pinned
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.HOME_LAYOUT_PRESETS,
        titleRes = Res.string.settings_home_layout_presets,
        searchTitleRes = Res.string.ss_home_layout_presets_title,
        searchSubtitleRes = Res.string.ss_home_layout_presets_subtitle,
        keywords = listOf("preset", "layout", "home", "save", "load", "import", "export", "share", "reset", "backup", "configuration"),
        route = Route.HomeLayoutPresets(),
        icon = Tabler.Outline.Bookmarks
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.CONFIGURE_LIBRARIES,
        titleRes = Res.string.settings_configure_libraries,
        searchTitleRes = Res.string.ss_configure_libraries_title,
        searchSubtitleRes = Res.string.ss_configure_libraries_subtitle,
        keywords = listOf("library", "libraries", "latest", "recently", "added", "home", "row", "shelf", "hide", "show"),
        route = Route.LibraryHomeSections(),
        icon = Tabler.Outline.Folders
    ))

/** The catalog projection of `AppearanceHomeLayoutRowRecords`: the search faces + the shared category. */
internal val AppearanceHomeLayoutSearchItems: List<SettingsSearchItem> = AppearanceHomeLayoutRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)


/**
 * Settings-search items for the advanced-gated "Performance" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearancePerformanceRowRecords = listOf(
    SettingsRowRecord(
        id = AppearanceSettingsIds.PERFORMANCE_MODE,
        titleRes = Res.string.settings_performance_mode,
        searchTitleRes = Res.string.ss_performance_mode_title,
        searchSubtitleRes = Res.string.ss_performance_mode_subtitle,
        keywords = listOf("performance", "speed", "lag", "battery", "animations"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.REDUCE_MOTION,
        titleRes = Res.string.settings_reduce_motion,
        searchTitleRes = Res.string.ss_reduce_motion_title,
        searchSubtitleRes = Res.string.ss_reduce_motion_subtitle,
        keywords = listOf("motion", "reduce", "animations", "parallax", "effects"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Activity,
        isAdvanced = true
    ))

/** The catalog projection of `AppearancePerformanceRowRecords`: the search faces + the shared category. */
internal val AppearancePerformanceSearchItems: List<SettingsSearchItem> = AppearancePerformanceRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)


/**
 * Settings-search items for the advanced-gated "Eye Care" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceEyeCareRowRecords = listOf(
    SettingsRowRecord(
        id = AppearanceSettingsIds.BLUE_LIGHT_FILTER,
        titleRes = Res.string.settings_blue_light_filter,
        searchTitleRes = Res.string.ss_blue_light_filter_title,
        searchSubtitleRes = Res.string.ss_blue_light_filter_subtitle,
        keywords = listOf("blue light", "amber", "eye care", "night", "filter", "tint"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = AppearanceSettingsIds.BLUE_LIGHT_STRENGTH,
        titleRes = Res.string.settings_blue_light_filter_strength,
        searchTitleRes = Res.string.ss_blue_light_strength_title,
        searchSubtitleRes = Res.string.ss_blue_light_strength_subtitle,
        keywords = listOf("blue light", "strength", "amber", "intensity", "overlay"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ))

/** The catalog projection of `AppearanceEyeCareRowRecords`: the search faces + the shared category. */
internal val AppearanceEyeCareSearchItems: List<SettingsSearchItem> = AppearanceEyeCareRowRecords.toSearchItems(CoreUiRes.string.ss_cat_appearance)


/**
 * Settings-search items for the advanced-gated "Newsletter" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
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

