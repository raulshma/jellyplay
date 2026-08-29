package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Appearance Settings" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to AppearanceSettingsScreen (incl. pinned sections, layout presets, configure libraries). Aggregated in [SettingsSearchCatalog].
 */
internal val AppearanceSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "pinned_home_sections",
        titleRes = R.string.ss_pinned_home_sections_title,
        subtitleRes = R.string.ss_pinned_home_sections_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("pinned", "home", "collection", "playlist", "favorites", "genre", "studio", "shelf", "row"),
        route = Route.PinnedHomeSections(),
        icon = Tabler.Outline.Pinned
    ),
    SettingsSearchItem(
        id = "home_layout_presets",
        titleRes = R.string.ss_home_layout_presets_title,
        subtitleRes = R.string.ss_home_layout_presets_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("preset", "layout", "home", "save", "load", "import", "export", "share", "reset", "backup", "configuration"),
        route = Route.HomeLayoutPresets(),
        icon = Tabler.Outline.Bookmarks
    ),
    SettingsSearchItem(
        id = "configure_libraries",
        titleRes = R.string.ss_configure_libraries_title,
        subtitleRes = R.string.ss_configure_libraries_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("library", "libraries", "latest", "recently", "added", "home", "row", "shelf", "hide", "show"),
        route = Route.LibraryHomeSections(),
        icon = Tabler.Outline.Folders
    ),
    SettingsSearchItem(
        id = "settings_in_home_search",
        titleRes = R.string.ss_settings_in_home_search_title,
        subtitleRes = R.string.ss_settings_in_home_search_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("search", "settings", "home", "find", "discover", "quick", "shortcut"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
    ),
    SettingsSearchItem(
        id = "date_format",
        titleRes = R.string.ss_date_format_title,
        subtitleRes = R.string.ss_date_format_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("date", "format", "time", "calendar", "day", "month", "year", "display"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Calendar,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "font_scale",
        titleRes = R.string.ss_font_scale_title,
        subtitleRes = R.string.ss_font_scale_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("font", "size", "text", "scale", "accessibility", "readability", "large", "small"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.TextSize,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "color_blind_mode",
        titleRes = R.string.ss_color_blind_mode_title,
        subtitleRes = R.string.ss_color_blind_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("color", "blind", "daltonize", "accessibility", "protanopia", "deuteranopia", "tritanopia", "vision"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hand_mode",
        titleRes = R.string.ss_hand_mode_title,
        subtitleRes = R.string.ss_hand_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("hand", "left", "right", "handed", "accessibility", "mirror", "one-handed"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.HandClick,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "theme_scheduler",
        titleRes = R.string.ss_theme_scheduler_title,
        subtitleRes = R.string.ss_theme_scheduler_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("theme", "scheduler", "day", "night", "auto", "time", "scheduled", "dark", "light"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "theme_mode",
        titleRes = R.string.ss_theme_mode_title,
        subtitleRes = R.string.ss_theme_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("theme", "mode", "light", "dark", "system", "black"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Moon
    ),
    SettingsSearchItem(
        id = "theme_style",
        titleRes = R.string.ss_theme_style_title,
        subtitleRes = R.string.ss_theme_style_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("theme", "style", "variant", "synthwave", "soothing", "monochrome", "vivid", "aurora", "sakura", "vector", "pop", "pastel", "neon", "retro", "look"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    ),
    SettingsSearchItem(
        id = "style_accent",
        titleRes = R.string.ss_style_accent_title,
        subtitleRes = R.string.ss_style_accent_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("accent", "color", "swatch", "synthwave", "soothing", "vivid", "aurora", "sakura", "vector", "neon", "theme"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "dynamic_theming",
        titleRes = R.string.ss_dynamic_theming_title,
        subtitleRes = R.string.ss_dynamic_theming_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("dynamic", "artwork", "colors", "theme", "wallpaper"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Video
    ),
    SettingsSearchItem(
        id = "oled_mode",
        titleRes = R.string.ss_oled_mode_title,
        subtitleRes = R.string.ss_oled_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("oled", "black", "amoled", "pure black", "battery"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.BrightnessHalf
    ),
    SettingsSearchItem(
        id = "contrast",
        titleRes = R.string.ss_contrast_title,
        subtitleRes = R.string.ss_contrast_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("contrast", "accessibility", "legibility", "readability"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "library_view_mode",
        titleRes = R.string.ss_library_view_mode_title,
        subtitleRes = R.string.ss_library_view_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("library", "view", "grid", "list", "layout"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayoutGrid,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "home_mode",
        titleRes = R.string.ss_home_mode_title,
        subtitleRes = R.string.ss_home_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("home", "layout", "mode", "video", "music"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Home,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hero_section",
        titleRes = R.string.ss_hero_section_title,
        subtitleRes = R.string.ss_hero_section_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("hero", "banner", "featured", "home", "carousel"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayersLinked,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "home_backdrop",
        titleRes = R.string.ss_home_backdrop_title,
        subtitleRes = R.string.ss_home_backdrop_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("home", "backdrop", "artwork", "background", "blur", "wallpaper"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "clock_home",
        titleRes = R.string.ss_clock_home_title,
        subtitleRes = R.string.ss_clock_home_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("clock", "time", "home", "wall", "current"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hide_top_header",
        titleRes = R.string.ss_hide_top_header_title,
        subtitleRes = R.string.ss_hide_top_header_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("top header", "app bar", "home bar", "hide", "scroll", "auto hide", "collapse", "dock"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.ArrowBarToDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "continue_watching_click",
        titleRes = R.string.ss_continue_watching_click_title,
        subtitleRes = R.string.ss_continue_watching_click_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("continue watching", "tap", "click", "resume", "play", "details"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PlayerPlay,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "unhide_cw",
        titleRes = R.string.ss_unhide_cw_title,
        subtitleRes = R.string.ss_unhide_cw_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("unhide", "continue watching", "hidden", "reset", "show"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Eye,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "merge_continue_next_up",
        titleRes = R.string.ss_merge_continue_next_up_title,
        subtitleRes = R.string.ss_merge_continue_next_up_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("merge", "combine", "continue watching", "next up", "single row"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayersLinked,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "next_up_max_days",
        titleRes = R.string.ss_next_up_max_days_title,
        subtitleRes = R.string.ss_next_up_max_days_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("next up", "days", "time window", "recent", "max days", "filter"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.CalendarTime,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "next_up_rewatching",
        titleRes = R.string.ss_next_up_rewatching_title,
        subtitleRes = R.string.ss_next_up_rewatching_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("next up", "rewatching", "rewatch", "rewatch", "repeat"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.History,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "theme_music",
        titleRes = R.string.ss_theme_music_title,
        subtitleRes = R.string.ss_theme_music_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("theme", "music", "backdrop", "ambience", "song", "score"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Music,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "nav_labels",
        titleRes = R.string.ss_nav_labels_title,
        subtitleRes = R.string.ss_nav_labels_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.TextSize,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "nav_bar_customization",
        titleRes = R.string.ss_nav_bar_customization_title,
        subtitleRes = R.string.ss_nav_bar_customization_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("navigation", "bar", "items", "bottom", "reorder", "hide", "show", "tabs", "home", "library", "search", "live tv", "browse", "shortcuts", "customize"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.LayoutGrid
    ),
    SettingsSearchItem(
        id = "nav_hide_on_scroll",
        titleRes = R.string.ss_nav_hide_on_scroll_title,
        subtitleRes = R.string.ss_nav_hide_on_scroll_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("navigation", "hide", "scroll", "auto hide", "bottom bar", "collapsible"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff
    ),
    SettingsSearchItem(
        id = "show_unwatched_badge",
        titleRes = R.string.ss_show_unwatched_badge_title,
        subtitleRes = R.string.ss_show_unwatched_badge_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("unwatched", "badge", "indicator", "new", "marker"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Folder,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "show_watched_checkmark",
        titleRes = R.string.ss_show_watched_checkmark_title,
        subtitleRes = R.string.ss_show_watched_checkmark_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("watched", "checkmark", "badge", "indicator", "finished"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.CircleCheck,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hide_watched_items",
        titleRes = R.string.ss_hide_watched_items_title,
        subtitleRes = R.string.ss_hide_watched_items_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("hide", "watched", "filter", "library", "clean"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hide_episode_thumbnails",
        titleRes = R.string.ss_hide_episode_thumbnails_title,
        subtitleRes = R.string.ss_hide_episode_thumbnails_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("hide", "episode", "thumbnail", "spoiler", "preview"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PhotoOff,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "compact_episode_list",
        titleRes = R.string.ss_compact_episode_list_title,
        subtitleRes = R.string.ss_compact_episode_list_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("episode", "list", "compact", "vertical", "layout", "rows", "dense"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.List
    ),
    SettingsSearchItem(
        id = "skip_specials",
        titleRes = R.string.ss_skip_specials_title,
        subtitleRes = R.string.ss_skip_specials_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("skip", "special", "episode", "bonus", "exclude"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.PlayerSkipForward,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "haptics_enabled",
        titleRes = R.string.ss_haptics_enabled_title,
        subtitleRes = R.string.ss_haptics_enabled_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("haptic", "vibration", "feedback", "vibrate", "touch"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.DeviceMobileVibration,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "show_share_media",
        titleRes = R.string.ss_show_share_media_title,
        subtitleRes = R.string.ss_show_share_media_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("share", "media", "send", "details"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Share,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "show_external_ratings",
        titleRes = R.string.ss_show_external_ratings_title,
        subtitleRes = R.string.ss_show_external_ratings_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("ratings", "imdb", "tmdb", "critic", "score", "star"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Star,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "performance_mode",
        titleRes = R.string.ss_performance_mode_title,
        subtitleRes = R.string.ss_performance_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("performance", "speed", "lag", "battery", "animations"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Gauge,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "reduce_motion",
        titleRes = R.string.ss_reduce_motion_title,
        subtitleRes = R.string.ss_reduce_motion_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("motion", "reduce", "animations", "parallax", "effects"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Activity,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "blue_light_filter",
        titleRes = R.string.ss_blue_light_filter_title,
        subtitleRes = R.string.ss_blue_light_filter_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("blue light", "amber", "eye care", "night", "filter", "tint"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "blue_light_strength",
        titleRes = R.string.ss_blue_light_strength_title,
        subtitleRes = R.string.ss_blue_light_strength_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("blue light", "strength", "amber", "intensity", "overlay"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Adjustments,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "accent_color",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_ui_accent_color_title,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_ui_accent_color_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("accent", "color", "theme", "swatch", "palette", "customize"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    ),
    SettingsSearchItem(
        id = "color_style",
        titleRes = com.raulshma.jellyplay.core.ui.R.string.core_ui_color_style_title,
        subtitleRes = com.raulshma.jellyplay.core.ui.R.string.core_ui_color_style_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("color style", "palette", "vibe", "generated", "mood", "theme"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Palette
    ),
    SettingsSearchItem(
        id = "hide_search_history",
        titleRes = R.string.ss_hide_search_history_title,
        subtitleRes = R.string.ss_hide_search_history_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("search", "history", "hide", "privacy", "recent"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.EyeOff,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "scheduled_start",
        titleRes = R.string.ss_scheduled_start_title,
        subtitleRes = R.string.ss_scheduled_start_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("theme", "schedule", "start", "hour", "day", "auto"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Sunrise,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "scheduled_end",
        titleRes = R.string.ss_scheduled_end_title,
        subtitleRes = R.string.ss_scheduled_end_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("theme", "schedule", "end", "hour", "night", "auto"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Sunset,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "newsletter_enabled",
        titleRes = R.string.ss_newsletter_enabled_title,
        subtitleRes = R.string.ss_newsletter_enabled_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("newsletter", "digest", "email", "periodic", "report"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Mail,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "newsletter_delivery_day",
        titleRes = R.string.ss_newsletter_delivery_day_title,
        subtitleRes = R.string.ss_newsletter_delivery_day_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("newsletter", "delivery", "day", "schedule", "weekday", "send"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Calendar,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "newsletter_sections",
        titleRes = R.string.ss_newsletter_sections_title,
        subtitleRes = R.string.ss_newsletter_sections_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_appearance,
        keywords = listOf("newsletter", "sections", "recently added", "activity log", "library stats", "continue watching", "next up", "curated picks", "content", "digest"),
        route = Route.AppearanceSettings(),
        icon = Tabler.Outline.Mail,
        isAdvanced = true
    ),
)
