package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_accent_color_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_accent_color_title
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_style_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_style_title
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_appearance
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_contrast_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_contrast_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_title
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
internal val AppearanceThemeSearchItems = listOf(
    SettingsSearchItem(
        id = AppearanceSettingsIds.SETTINGS_IN_HOME_SEARCH,
        titleRes = Res.string.ss_settings_in_home_search_title,
        subtitleRes = Res.string.ss_settings_in_home_search_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("search", "settings", "home", "find", "discover", "quick", "shortcut"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.DATE_FORMAT,
        titleRes = Res.string.ss_date_format_title,
        subtitleRes = Res.string.ss_date_format_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("date", "format", "time", "calendar", "day", "month", "year", "display"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Calendar,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.FONT_SCALE,
        titleRes = Res.string.ss_font_scale_title,
        subtitleRes = Res.string.ss_font_scale_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("font", "size", "text", "scale", "accessibility", "readability", "large", "small"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.TextSize,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.COLOR_BLIND_MODE,
        titleRes = Res.string.ss_color_blind_mode_title,
        subtitleRes = Res.string.ss_color_blind_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("color", "blind", "daltonize", "accessibility", "protanopia", "deuteranopia", "tritanopia", "vision"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HAND_MODE,
        titleRes = Res.string.ss_hand_mode_title,
        subtitleRes = Res.string.ss_hand_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("hand", "left", "right", "handed", "accessibility", "mirror", "one-handed"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.HandClick,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.THEME_SCHEDULER,
        titleRes = Res.string.ss_theme_scheduler_title,
        subtitleRes = Res.string.ss_theme_scheduler_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("theme", "scheduler", "day", "night", "auto", "time", "scheduled", "dark", "light"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.THEME_MODE,
        titleRes = Res.string.ss_theme_mode_title,
        subtitleRes = Res.string.ss_theme_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("theme", "mode", "light", "dark", "system", "black"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Moon
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.THEME_STYLE,
        titleRes = Res.string.ss_theme_style_title,
        subtitleRes = Res.string.ss_theme_style_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("theme", "style", "variant", "synthwave", "soothing", "monochrome", "vivid", "aurora", "sakura", "vector", "pop", "pastel", "neon", "retro", "look"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.STYLE_ACCENT,
        titleRes = Res.string.ss_style_accent_title,
        subtitleRes = Res.string.ss_style_accent_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("accent", "color", "swatch", "synthwave", "soothing", "vivid", "aurora", "sakura", "vector", "neon", "theme"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.DYNAMIC_THEMING,
        titleRes = Res.string.ss_dynamic_theming_title,
        subtitleRes = Res.string.ss_dynamic_theming_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("dynamic", "artwork", "colors", "theme", "wallpaper"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Video,
        platforms = platformsForCapability(settingsCapabilities.supportsDynamicColor),
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.OLED_MODE,
        titleRes = Res.string.ss_oled_mode_title,
        subtitleRes = Res.string.ss_oled_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("oled", "black", "amoled", "pure black", "battery"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.BrightnessHalf
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.CONTRAST,
        titleRes = Res.string.ss_contrast_title,
        subtitleRes = Res.string.ss_contrast_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("contrast", "accessibility", "legibility", "readability"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.LIBRARY_VIEW_MODE,
        titleRes = Res.string.ss_library_view_mode_title,
        subtitleRes = Res.string.ss_library_view_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("library", "view", "grid", "list", "layout"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayoutGrid,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HOME_MODE,
        titleRes = Res.string.ss_home_mode_title,
        subtitleRes = Res.string.ss_home_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("home", "layout", "mode", "video", "music"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Home,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HERO_SECTION,
        titleRes = Res.string.ss_hero_section_title,
        subtitleRes = Res.string.ss_hero_section_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("hero", "banner", "featured", "home", "carousel"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayersLinked,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HOME_BACKDROP,
        titleRes = Res.string.ss_home_backdrop_title,
        subtitleRes = Res.string.ss_home_backdrop_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("home", "backdrop", "artwork", "background", "blur", "wallpaper"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.CLOCK_HOME,
        titleRes = Res.string.ss_clock_home_title,
        subtitleRes = Res.string.ss_clock_home_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("clock", "time", "home", "wall", "current"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HIDE_TOP_HEADER,
        titleRes = Res.string.ss_hide_top_header_title,
        subtitleRes = Res.string.ss_hide_top_header_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("top header", "app bar", "home bar", "hide", "scroll", "auto hide", "collapse", "dock"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.ArrowBarToDown,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.CONTINUE_WATCHING_CLICK,
        titleRes = Res.string.ss_continue_watching_click_title,
        subtitleRes = Res.string.ss_continue_watching_click_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("continue watching", "tap", "click", "resume", "play", "details"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PlayerPlay,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.UNHIDE_CW,
        titleRes = Res.string.ss_unhide_cw_title,
        subtitleRes = Res.string.ss_unhide_cw_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("unhide", "continue watching", "hidden", "reset", "show"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.MERGE_CONTINUE_NEXT_UP,
        titleRes = Res.string.ss_merge_continue_next_up_title,
        subtitleRes = Res.string.ss_merge_continue_next_up_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("merge", "combine", "continue watching", "next up", "single row"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayersLinked,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.NEXT_UP_MAX_DAYS,
        titleRes = Res.string.ss_next_up_max_days_title,
        subtitleRes = Res.string.ss_next_up_max_days_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("next up", "days", "time window", "recent", "max days", "filter"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.CalendarTime,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.NEXT_UP_REWATCHING,
        titleRes = Res.string.ss_next_up_rewatching_title,
        subtitleRes = Res.string.ss_next_up_rewatching_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("next up", "rewatching", "rewatch", "rewatch", "repeat"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.History,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.THEME_MUSIC,
        titleRes = Res.string.ss_theme_music_title,
        subtitleRes = Res.string.ss_theme_music_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("theme", "music", "backdrop", "ambience", "song", "score"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.NAV_LABELS,
        titleRes = Res.string.ss_nav_labels_title,
        subtitleRes = Res.string.ss_nav_labels_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.TextSize,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.ACCENT_COLOR,
        titleRes = CoreUiRes.string.core_ui_accent_color_title,
        subtitleRes = CoreUiRes.string.core_ui_accent_color_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("accent", "color", "theme", "swatch", "palette", "customize"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.COLOR_STYLE,
        titleRes = CoreUiRes.string.core_ui_color_style_title,
        subtitleRes = CoreUiRes.string.core_ui_color_style_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("color style", "palette", "vibe", "generated", "mood", "theme"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.SCHEDULED_START,
        titleRes = Res.string.ss_scheduled_start_title,
        subtitleRes = Res.string.ss_scheduled_start_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("theme", "schedule", "start", "hour", "day", "auto"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Sunrise,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.SCHEDULED_END,
        titleRes = Res.string.ss_scheduled_end_title,
        subtitleRes = Res.string.ss_scheduled_end_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("theme", "schedule", "end", "hour", "night", "auto"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Sunset,
        isAdvanced = true
    )
)

/**
 * Settings-search items for the navigation-customization group rendered by NavigationCustomizationGroup of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceNavigationSearchItems = listOf(
    SettingsSearchItem(
        id = AppearanceSettingsIds.NAV_BAR_CUSTOMIZATION,
        titleRes = Res.string.ss_nav_bar_customization_title,
        subtitleRes = Res.string.ss_nav_bar_customization_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("navigation", "bar", "items", "bottom", "reorder", "hide", "show", "tabs", "home", "library", "search", "live tv", "browse", "shortcuts", "customize"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayoutGrid
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.NAV_HIDE_ON_SCROLL,
        titleRes = Res.string.ss_nav_hide_on_scroll_title,
        subtitleRes = Res.string.ss_nav_hide_on_scroll_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("navigation", "hide", "scroll", "auto hide", "bottom bar", "collapsible"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff
    )
)

/**
 * Settings-search items for the "Library & Cards" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceLibrarySearchItems = listOf(
    SettingsSearchItem(
        id = AppearanceSettingsIds.SHOW_UNWATCHED_BADGE,
        titleRes = Res.string.ss_show_unwatched_badge_title,
        subtitleRes = Res.string.ss_show_unwatched_badge_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("unwatched", "badge", "indicator", "new", "marker"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Folder,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.SHOW_WATCHED_CHECKMARK,
        titleRes = Res.string.ss_show_watched_checkmark_title,
        subtitleRes = Res.string.ss_show_watched_checkmark_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("watched", "checkmark", "badge", "indicator", "finished"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.CircleCheck,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HIDE_WATCHED_ITEMS,
        titleRes = Res.string.ss_hide_watched_items_title,
        subtitleRes = Res.string.ss_hide_watched_items_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("hide", "watched", "filter", "library", "clean"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HIDE_EPISODE_THUMBNAILS,
        titleRes = Res.string.ss_hide_episode_thumbnails_title,
        subtitleRes = Res.string.ss_hide_episode_thumbnails_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("hide", "episode", "thumbnail", "spoiler", "preview"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PhotoOff,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.COMPACT_EPISODE_LIST,
        titleRes = Res.string.ss_compact_episode_list_title,
        subtitleRes = Res.string.ss_compact_episode_list_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("episode", "list", "compact", "vertical", "layout", "rows", "dense"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.List
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.SKIP_SPECIALS,
        titleRes = Res.string.ss_skip_specials_title,
        subtitleRes = Res.string.ss_skip_specials_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("skip", "special", "episode", "bonus", "exclude"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HAPTICS_ENABLED,
        titleRes = Res.string.ss_haptics_enabled_title,
        subtitleRes = Res.string.ss_haptics_enabled_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("haptic", "vibration", "feedback", "vibrate", "touch"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.DeviceMobileVibration,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.SHOW_SHARE_MEDIA,
        titleRes = Res.string.ss_show_share_media_title,
        subtitleRes = Res.string.ss_show_share_media_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("share", "media", "send", "details"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Share,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.SHOW_EXTERNAL_RATINGS,
        titleRes = Res.string.ss_show_external_ratings_title,
        subtitleRes = Res.string.ss_show_external_ratings_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("ratings", "imdb", "tmdb", "critic", "score", "star"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Star,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HIDE_SEARCH_HISTORY,
        titleRes = Res.string.ss_hide_search_history_title,
        subtitleRes = Res.string.ss_hide_search_history_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("search", "history", "hide", "privacy", "recent"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff,
        isAdvanced = true
    )
)

/**
 * Settings-search items for the "Home Screen Layout" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceHomeLayoutSearchItems = listOf(
    SettingsSearchItem(
        id = AppearanceSettingsIds.PINNED_HOME_SECTIONS,
        titleRes = Res.string.ss_pinned_home_sections_title,
        subtitleRes = Res.string.ss_pinned_home_sections_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("pinned", "home", "collection", "playlist", "favorites", "genre", "studio", "shelf", "row"),
        route = Route.PinnedHomeSections(),
        icon = Tabler.Outline.Pinned
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.HOME_LAYOUT_PRESETS,
        titleRes = Res.string.ss_home_layout_presets_title,
        subtitleRes = Res.string.ss_home_layout_presets_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("preset", "layout", "home", "save", "load", "import", "export", "share", "reset", "backup", "configuration"),
        route = Route.HomeLayoutPresets(),
        icon = Tabler.Outline.Bookmarks
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.CONFIGURE_LIBRARIES,
        titleRes = Res.string.ss_configure_libraries_title,
        subtitleRes = Res.string.ss_configure_libraries_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("library", "libraries", "latest", "recently", "added", "home", "row", "shelf", "hide", "show"),
        route = Route.LibraryHomeSections(),
        icon = Tabler.Outline.Folders
    )
)

/**
 * Settings-search items for the advanced-gated "Performance" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearancePerformanceSearchItems = listOf(
    SettingsSearchItem(
        id = AppearanceSettingsIds.PERFORMANCE_MODE,
        titleRes = Res.string.ss_performance_mode_title,
        subtitleRes = Res.string.ss_performance_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("performance", "speed", "lag", "battery", "animations"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.REDUCE_MOTION,
        titleRes = Res.string.ss_reduce_motion_title,
        subtitleRes = Res.string.ss_reduce_motion_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("motion", "reduce", "animations", "parallax", "effects"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Activity,
        isAdvanced = true
    )
)

/**
 * Settings-search items for the advanced-gated "Eye Care" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceEyeCareSearchItems = listOf(
    SettingsSearchItem(
        id = AppearanceSettingsIds.BLUE_LIGHT_FILTER,
        titleRes = Res.string.ss_blue_light_filter_title,
        subtitleRes = Res.string.ss_blue_light_filter_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("blue light", "amber", "eye care", "night", "filter", "tint"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.BLUE_LIGHT_STRENGTH,
        titleRes = Res.string.ss_blue_light_strength_title,
        subtitleRes = Res.string.ss_blue_light_strength_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("blue light", "strength", "amber", "intensity", "overlay"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    )
)

/**
 * Settings-search items for the advanced-gated "Newsletter" group of AppearanceSettingsScreen.
 * Split from the single flat appearance list along the screen-group line so
 * each screen group derives its facts from its own declaration list
 * (decision Q11a). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceNewsletterSearchItems = listOf(
    SettingsSearchItem(
        id = AppearanceSettingsIds.NEWSLETTER_ENABLED,
        titleRes = Res.string.ss_newsletter_enabled_title,
        subtitleRes = Res.string.ss_newsletter_enabled_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("newsletter", "digest", "email", "periodic", "report"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Mail,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.NEWSLETTER_DELIVERY_DAY,
        titleRes = Res.string.ss_newsletter_delivery_day_title,
        subtitleRes = Res.string.ss_newsletter_delivery_day_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("newsletter", "delivery", "day", "schedule", "weekday", "send"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Calendar,
        isAdvanced = true
    )
,
    SettingsSearchItem(
        id = AppearanceSettingsIds.NEWSLETTER_SECTIONS,
        titleRes = Res.string.ss_newsletter_sections_title,
        subtitleRes = Res.string.ss_newsletter_sections_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_appearance,
        keywords = listOf("newsletter", "sections", "recently added", "activity log", "library stats", "continue watching", "next up", "curated picks", "content", "digest"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Mail,
        isAdvanced = true
    ),

)
