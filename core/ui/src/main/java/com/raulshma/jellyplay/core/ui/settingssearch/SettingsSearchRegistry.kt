package com.raulshma.jellyplay.core.ui.settingssearch

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.ui.R
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

data class SettingsSearchItem(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val categoryRes: Int,
    val keywords: List<String>,
    val route: Route,
    val icon: ImageVector,
    val isAdvanced: Boolean = false
)

/**
 * Plain-[String] projection of a [SettingsSearchItem] used for fuzzy matching and rendering.
 *
 * The registry stores `@StringRes` ids so the UI resolves them lazily through the current locale;
 * matching and display both operate on these already-resolved [title]/[subtitle]/[category]
 * strings. The original [item] is carried along so callers can navigate by `route`, read
 * `isAdvanced`, etc.
 */
data class ResolvedSettingsItem(
    val item: SettingsSearchItem,
    val title: String,
    val subtitle: String,
    val category: String,
) {
    val id: String get() = item.id
    val route: Route get() = item.route
    val icon: ImageVector get() = item.icon
    val isAdvanced: Boolean get() = item.isAdvanced
}

/**
 * Resolve [SettingsSearchRegistry.items] into [ResolvedSettingsItem]s using [resolve]
 * (typically `context::getString` or a Compose-backed resolver). Resolution is the single place
 * that turns resource ids into locale-aware text, so both search matching and rendering share one
 * localized snapshot.
 */
fun List<SettingsSearchItem>.resolve(@StringRes resolve: (Int) -> String): List<ResolvedSettingsItem> =
    map { item ->
        ResolvedSettingsItem(
            item = item,
            title = resolve(item.titleRes),
            subtitle = resolve(item.subtitleRes),
            category = resolve(item.categoryRes),
        )
    }

object SettingsSearchRegistry {
    val items = listOf(
        SettingsSearchItem(
            id = "logout",
            titleRes = R.string.ss_logout_title,
            subtitleRes = R.string.ss_logout_subtitle,
            categoryRes = R.string.ss_cat_account,
            keywords = listOf("sign out", "logout", "exit", "disconnect"),
            route = Route.Settings,
            icon = Tabler.Outline.Logout
        ),
        SettingsSearchItem(
            id = "sign_out_from_server",
            titleRes = R.string.ss_sign_out_from_server_title,
            subtitleRes = R.string.ss_sign_out_from_server_subtitle,
            categoryRes = R.string.ss_cat_account,
            keywords = listOf("sign out", "server", "remove device", "revoke", "session", "remote", "disconnect"),
            route = Route.Settings,
            icon = Tabler.Outline.Logout
        ),
        // Account / Users / Servers
        SettingsSearchItem(
            id = "server_management",
            titleRes = R.string.ss_server_management_title,
            subtitleRes = R.string.ss_server_management_subtitle,
            categoryRes = R.string.ss_cat_account,
            keywords = listOf("server", "connection", "jellyfin", "address", "switch"),
            route = Route.ServerManagement(),
            icon = Tabler.Outline.Server
        ),
        SettingsSearchItem(
            id = "user_management",
            titleRes = R.string.ss_user_management_title,
            subtitleRes = R.string.ss_user_management_subtitle,
            categoryRes = R.string.ss_cat_account,
            keywords = listOf("user", "accounts", "profile", "switch user", "admin"),
            route = Route.UserManagement(),
            icon = Tabler.Outline.Users
        ),
        SettingsSearchItem(
            id = "seerr_settings",
            titleRes = R.string.ss_seerr_settings_title,
            subtitleRes = R.string.ss_seerr_settings_subtitle,
            categoryRes = R.string.ss_cat_integrations,
            keywords = listOf("seerr", "jellyseerr", "request", "movies", "shows", "approve", "integration"),
            route = Route.SeerrSettings(),
            icon = Tabler.Outline.Puzzle
        ),
        SettingsSearchItem(
            id = "integrations",
            titleRes = R.string.ss_integrations_title,
            subtitleRes = R.string.ss_integrations_subtitle,
            categoryRes = R.string.ss_cat_integrations,
            keywords = listOf("integrations", "jellyseerr", "overseerr", "arr", "sonarr", "radarr", "request"),
            route = Route.Integrations(highlightSettingId = "integrations"),
            icon = Tabler.Outline.Plug
        ),
        SettingsSearchItem(
            id = "subtitle_provider_settings",
            titleRes = R.string.ss_subtitle_provider_settings_title,
            subtitleRes = R.string.ss_subtitle_provider_settings_subtitle,
            categoryRes = R.string.ss_cat_integrations,
            keywords = listOf("subtitle", "provider", "opensubtitles", "opensubtitles.com", "tvsubs", "manager", "extensions"),
            route = Route.Integrations(highlightSettingId = "subtitle_provider_settings"),
            icon = Tabler.Outline.Language,
            isAdvanced = true
        ),

        // Activity & Insights (migrated from the Home drawer)
        SettingsSearchItem(
            id = "favorites",
            titleRes = R.string.ss_favorites_title,
            subtitleRes = R.string.ss_favorites_subtitle,
            categoryRes = R.string.ss_cat_activity_insights,
            keywords = listOf("favorites", "favourite", "liked", "collection", "heart"),
            route = Route.Favorites,
            icon = Tabler.Outline.Heart
        ),
        SettingsSearchItem(
            id = "watch_progress_heatmap",
            titleRes = R.string.ss_watch_progress_heatmap_title,
            subtitleRes = R.string.ss_watch_progress_heatmap_subtitle,
            categoryRes = R.string.ss_cat_activity_insights,
            keywords = listOf("watch", "history", "heatmap", "progress", "activity", "stats"),
            route = Route.WatchProgressHeatmap,
            icon = Tabler.Outline.ChartBar
        ),
        SettingsSearchItem(
            id = "activity_queue",
            titleRes = R.string.ss_activity_queue_title,
            subtitleRes = R.string.ss_activity_queue_subtitle,
            categoryRes = R.string.ss_cat_activity_insights,
            keywords = listOf("activity", "queue", "download", "radarr", "sonarr", "arr", "import"),
            route = Route.ArrQueue,
            icon = Tabler.Outline.Database
        ),
        SettingsSearchItem(
            id = "upcoming",
            titleRes = R.string.ss_upcoming_title,
            subtitleRes = R.string.ss_upcoming_subtitle,
            categoryRes = R.string.ss_cat_activity_insights,
            keywords = listOf("upcoming", "calendar", "schedule", "new", "episodes", "soon"),
            route = Route.UpcomingCalendar,
            icon = Tabler.Outline.CalendarEvent
        ),
        SettingsSearchItem(
            id = "requests",
            titleRes = R.string.ss_requests_title,
            subtitleRes = R.string.ss_requests_subtitle,
            categoryRes = R.string.ss_cat_activity_insights,
            keywords = listOf("requests", "seerr", "jellyseerr", "pending", "approve"),
            route = Route.Requests,
            icon = Tabler.Outline.Inbox
        ),

        // System (migrated from the Home drawer)
        SettingsSearchItem(
            id = "admin_dashboard",
            titleRes = R.string.ss_admin_dashboard_title,
            subtitleRes = R.string.ss_admin_dashboard_subtitle,
            categoryRes = R.string.ss_cat_system,
            keywords = listOf("admin", "dashboard", "sessions", "server", "management"),
            route = Route.AdminDashboard,
            icon = Tabler.Outline.Shield
        ),
        SettingsSearchItem(
            id = "setup_wizard",
            titleRes = R.string.ss_setup_wizard_title,
            subtitleRes = R.string.ss_setup_wizard_subtitle,
            categoryRes = R.string.ss_cat_system,
            keywords = listOf("setup", "wizard", "onboarding", "configure", "initial"),
            route = Route.Onboarding,
            icon = Tabler.Outline.Wand
        ),
        SettingsSearchItem(
            id = "screensaver_show_title",
            titleRes = R.string.ss_screensaver_show_title_title,
            subtitleRes = R.string.ss_screensaver_show_title_subtitle,
            categoryRes = R.string.ss_cat_system,
            keywords = listOf("screensaver", "dream", "title", "tv", "show", "media title", "display"),
            route = Route.Settings,
            icon = Tabler.Outline.Typography
        ),
        SettingsSearchItem(
            id = "screensaver_categories",
            titleRes = R.string.ss_screensaver_categories_title,
            subtitleRes = R.string.ss_screensaver_categories_subtitle,
            categoryRes = R.string.ss_cat_system,
            keywords = listOf("screensaver", "dream", "categories", "tv", "movies", "music", "content"),
            route = Route.Settings,
            icon = Tabler.Outline.Folders
        ),
        SettingsSearchItem(
            id = "screensaver_slideshow_interval",
            titleRes = R.string.ss_screensaver_slideshow_interval_title,
            subtitleRes = R.string.ss_screensaver_slideshow_interval_subtitle,
            categoryRes = R.string.ss_cat_system,
            keywords = listOf("screensaver", "dream", "slideshow", "interval", "tv", "duration", "seconds"),
            route = Route.Settings,
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "screensaver_ken_burns",
            titleRes = R.string.ss_screensaver_ken_burns_title,
            subtitleRes = R.string.ss_screensaver_ken_burns_subtitle,
            categoryRes = R.string.ss_cat_system,
            keywords = listOf("screensaver", "dream", "ken burns", "pan", "zoom", "animation", "tv"),
            route = Route.Settings,
            icon = Tabler.Outline.Movie
        ),
        SettingsSearchItem(
            id = "screensaver_transition_style",
            titleRes = R.string.ss_screensaver_transition_style_title,
            subtitleRes = R.string.ss_screensaver_transition_style_subtitle,
            categoryRes = R.string.ss_cat_system,
            keywords = listOf("screensaver", "dream", "transition", "style", "crossfade", "slide", "tv"),
            route = Route.Settings,
            icon = Tabler.Outline.ArrowsHorizontal
        ),

        // Appearance Settings
        SettingsSearchItem(
            id = "pinned_home_sections",
            titleRes = R.string.ss_pinned_home_sections_title,
            subtitleRes = R.string.ss_pinned_home_sections_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("pinned", "home", "collection", "playlist", "favorites", "genre", "studio", "shelf", "row"),
            route = Route.PinnedHomeSections(),
            icon = Tabler.Outline.Pinned
        ),
        SettingsSearchItem(
            id = "home_layout_presets",
            titleRes = R.string.ss_home_layout_presets_title,
            subtitleRes = R.string.ss_home_layout_presets_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("preset", "layout", "home", "save", "load", "import", "export", "share", "reset", "backup", "configuration"),
            route = Route.HomeLayoutPresets(),
            icon = Tabler.Outline.Bookmarks
        ),
        SettingsSearchItem(
            id = "configure_libraries",
            titleRes = R.string.ss_configure_libraries_title,
            subtitleRes = R.string.ss_configure_libraries_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("library", "libraries", "latest", "recently", "added", "home", "row", "shelf", "hide", "show"),
            route = Route.LibraryHomeSections(),
            icon = Tabler.Outline.Folders
        ),
        SettingsSearchItem(
            id = "settings_in_home_search",
            titleRes = R.string.ss_settings_in_home_search_title,
            subtitleRes = R.string.ss_settings_in_home_search_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("search", "settings", "home", "find", "discover", "quick", "shortcut"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Adjustments,
        ),
        SettingsSearchItem(
            id = "date_format",
            titleRes = R.string.ss_date_format_title,
            subtitleRes = R.string.ss_date_format_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("date", "format", "time", "calendar", "day", "month", "year", "display"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Calendar,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "font_scale",
            titleRes = R.string.ss_font_scale_title,
            subtitleRes = R.string.ss_font_scale_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("font", "size", "text", "scale", "accessibility", "readability", "large", "small"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.TextSize,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "color_blind_mode",
            titleRes = R.string.ss_color_blind_mode_title,
            subtitleRes = R.string.ss_color_blind_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("color", "blind", "daltonize", "accessibility", "protanopia", "deuteranopia", "tritanopia", "vision"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Eye,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hand_mode",
            titleRes = R.string.ss_hand_mode_title,
            subtitleRes = R.string.ss_hand_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("hand", "left", "right", "handed", "accessibility", "mirror", "one-handed"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.HandClick,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "theme_scheduler",
            titleRes = R.string.ss_theme_scheduler_title,
            subtitleRes = R.string.ss_theme_scheduler_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("theme", "scheduler", "day", "night", "auto", "time", "scheduled", "dark", "light"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "theme_mode",
            titleRes = R.string.ss_theme_mode_title,
            subtitleRes = R.string.ss_theme_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("theme", "mode", "light", "dark", "system", "black"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Moon
        ),
        SettingsSearchItem(
            id = "synthwave_mode",
            titleRes = R.string.ss_synthwave_mode_title,
            subtitleRes = R.string.ss_synthwave_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("synthwave", "retro", "neon", "theme", "colors"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette
        ),
        SettingsSearchItem(
            id = "synthwave_accent",
            titleRes = R.string.core_ui_synthwave_accent_title,
            subtitleRes = R.string.core_ui_synthwave_accent_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("synthwave", "accent", "neon", "color", "swatch"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "soothing_mode",
            titleRes = R.string.ss_soothing_mode_title,
            subtitleRes = R.string.ss_soothing_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("soothing", "soft", "rounded", "calm", "theme"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette
        ),
        SettingsSearchItem(
            id = "soothing_accent",
            titleRes = R.string.core_ui_soothing_accent_title,
            subtitleRes = R.string.core_ui_soothing_accent_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("soothing", "accent", "color", "calm", "swatch"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "dynamic_theming",
            titleRes = R.string.ss_dynamic_theming_title,
            subtitleRes = R.string.ss_dynamic_theming_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("dynamic", "artwork", "colors", "theme", "wallpaper"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Video
        ),
        SettingsSearchItem(
            id = "oled_mode",
            titleRes = R.string.ss_oled_mode_title,
            subtitleRes = R.string.ss_oled_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("oled", "black", "amoled", "pure black", "battery"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.BrightnessHalf
        ),
        SettingsSearchItem(
            id = "contrast",
            titleRes = R.string.ss_contrast_title,
            subtitleRes = R.string.ss_contrast_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("contrast", "accessibility", "legibility", "readability"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "library_view_mode",
            titleRes = R.string.ss_library_view_mode_title,
            subtitleRes = R.string.ss_library_view_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("library", "view", "grid", "list", "layout"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.LayoutGrid,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "home_mode",
            titleRes = R.string.ss_home_mode_title,
            subtitleRes = R.string.ss_home_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("home", "layout", "mode", "video", "music"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Home,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hero_section",
            titleRes = R.string.ss_hero_section_title,
            subtitleRes = R.string.ss_hero_section_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("hero", "banner", "featured", "home", "carousel"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.LayersLinked,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "home_backdrop",
            titleRes = R.string.ss_home_backdrop_title,
            subtitleRes = R.string.ss_home_backdrop_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("home", "backdrop", "artwork", "background", "blur", "wallpaper"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Photo,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "clock_home",
            titleRes = R.string.ss_clock_home_title,
            subtitleRes = R.string.ss_clock_home_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("clock", "time", "home", "wall", "current"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hide_top_header",
            titleRes = R.string.ss_hide_top_header_title,
            subtitleRes = R.string.ss_hide_top_header_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("top header", "app bar", "home bar", "hide", "scroll", "auto hide", "collapse", "dock"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.ArrowBarToDown,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "continue_watching_click",
            titleRes = R.string.ss_continue_watching_click_title,
            subtitleRes = R.string.ss_continue_watching_click_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("continue watching", "tap", "click", "resume", "play", "details"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.PlayerPlay,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "unhide_cw",
            titleRes = R.string.ss_unhide_cw_title,
            subtitleRes = R.string.ss_unhide_cw_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("unhide", "continue watching", "hidden", "reset", "show"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Eye,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "merge_continue_next_up",
            titleRes = R.string.ss_merge_continue_next_up_title,
            subtitleRes = R.string.ss_merge_continue_next_up_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("merge", "combine", "continue watching", "next up", "single row"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.LayersLinked,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "next_up_max_days",
            titleRes = R.string.ss_next_up_max_days_title,
            subtitleRes = R.string.ss_next_up_max_days_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("next up", "days", "time window", "recent", "max days", "filter"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.CalendarTime,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "next_up_rewatching",
            titleRes = R.string.ss_next_up_rewatching_title,
            subtitleRes = R.string.ss_next_up_rewatching_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("next up", "rewatching", "rewatch", "rewatch", "repeat"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.History,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "theme_music",
            titleRes = R.string.ss_theme_music_title,
            subtitleRes = R.string.ss_theme_music_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("theme", "music", "backdrop", "ambience", "song", "score"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "nav_labels",
            titleRes = R.string.ss_nav_labels_title,
            subtitleRes = R.string.ss_nav_labels_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.TextSize,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "nav_bar_customization",
            titleRes = R.string.ss_nav_bar_customization_title,
            subtitleRes = R.string.ss_nav_bar_customization_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("navigation", "bar", "items", "bottom", "reorder", "hide", "show", "tabs", "home", "library", "search", "live tv", "browse", "shortcuts", "customize"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.LayoutGrid
        ),
        SettingsSearchItem(
            id = "nav_hide_on_scroll",
            titleRes = R.string.ss_nav_hide_on_scroll_title,
            subtitleRes = R.string.ss_nav_hide_on_scroll_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("navigation", "hide", "scroll", "auto hide", "bottom bar", "collapsible"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.EyeOff
        ),
        SettingsSearchItem(
            id = "show_unwatched_badge",
            titleRes = R.string.ss_show_unwatched_badge_title,
            subtitleRes = R.string.ss_show_unwatched_badge_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("unwatched", "badge", "indicator", "new", "marker"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Folder,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_watched_checkmark",
            titleRes = R.string.ss_show_watched_checkmark_title,
            subtitleRes = R.string.ss_show_watched_checkmark_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("watched", "checkmark", "badge", "indicator", "finished"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.CircleCheck,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hide_watched_items",
            titleRes = R.string.ss_hide_watched_items_title,
            subtitleRes = R.string.ss_hide_watched_items_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("hide", "watched", "filter", "library", "clean"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.EyeOff,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hide_episode_thumbnails",
            titleRes = R.string.ss_hide_episode_thumbnails_title,
            subtitleRes = R.string.ss_hide_episode_thumbnails_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("hide", "episode", "thumbnail", "spoiler", "preview"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.PhotoOff,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "compact_episode_list",
            titleRes = R.string.ss_compact_episode_list_title,
            subtitleRes = R.string.ss_compact_episode_list_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("episode", "list", "compact", "vertical", "layout", "rows", "dense"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.List
        ),
        SettingsSearchItem(
            id = "skip_specials",
            titleRes = R.string.ss_skip_specials_title,
            subtitleRes = R.string.ss_skip_specials_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("skip", "special", "episode", "bonus", "exclude"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.PlayerSkipForward,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "haptics_enabled",
            titleRes = R.string.ss_haptics_enabled_title,
            subtitleRes = R.string.ss_haptics_enabled_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("haptic", "vibration", "feedback", "vibrate", "touch"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.DeviceMobileVibration,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_share_media",
            titleRes = R.string.ss_show_share_media_title,
            subtitleRes = R.string.ss_show_share_media_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("share", "media", "send", "details"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Share,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_external_ratings",
            titleRes = R.string.ss_show_external_ratings_title,
            subtitleRes = R.string.ss_show_external_ratings_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("ratings", "imdb", "tmdb", "critic", "score", "star"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Star,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "performance_mode",
            titleRes = R.string.ss_performance_mode_title,
            subtitleRes = R.string.ss_performance_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("performance", "speed", "lag", "battery", "animations"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Gauge,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "reduce_motion",
            titleRes = R.string.ss_reduce_motion_title,
            subtitleRes = R.string.ss_reduce_motion_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("motion", "reduce", "animations", "parallax", "effects"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Activity,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "blue_light_filter",
            titleRes = R.string.ss_blue_light_filter_title,
            subtitleRes = R.string.ss_blue_light_filter_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("blue light", "amber", "eye care", "night", "filter", "tint"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Moon,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "blue_light_strength",
            titleRes = R.string.ss_blue_light_strength_title,
            subtitleRes = R.string.ss_blue_light_strength_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("blue light", "strength", "amber", "intensity", "overlay"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "monochrome_mode",
            titleRes = R.string.ss_monochrome_mode_title,
            subtitleRes = R.string.ss_monochrome_mode_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("monochrome", "black", "white", "nothing", "grayscale", "minimal"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "accent_color",
            titleRes = R.string.core_ui_accent_color_title,
            subtitleRes = R.string.core_ui_accent_color_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("accent", "color", "theme", "swatch", "palette", "customize"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette
        ),
        SettingsSearchItem(
            id = "color_style",
            titleRes = R.string.core_ui_color_style_title,
            subtitleRes = R.string.core_ui_color_style_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("color style", "palette", "vibe", "generated", "mood", "theme"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette
        ),
        SettingsSearchItem(
            id = "hide_search_history",
            titleRes = R.string.ss_hide_search_history_title,
            subtitleRes = R.string.ss_hide_search_history_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("search", "history", "hide", "privacy", "recent"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.EyeOff,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "scheduled_start",
            titleRes = R.string.ss_scheduled_start_title,
            subtitleRes = R.string.ss_scheduled_start_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("theme", "schedule", "start", "hour", "day", "auto"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Sunrise,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "scheduled_end",
            titleRes = R.string.ss_scheduled_end_title,
            subtitleRes = R.string.ss_scheduled_end_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("theme", "schedule", "end", "hour", "night", "auto"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Sunset,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "newsletter_enabled",
            titleRes = R.string.ss_newsletter_enabled_title,
            subtitleRes = R.string.ss_newsletter_enabled_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("newsletter", "digest", "email", "periodic", "report"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Mail,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "newsletter_delivery_day",
            titleRes = R.string.ss_newsletter_delivery_day_title,
            subtitleRes = R.string.ss_newsletter_delivery_day_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("newsletter", "delivery", "day", "schedule", "weekday", "send"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Calendar,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "newsletter_sections",
            titleRes = R.string.ss_newsletter_sections_title,
            subtitleRes = R.string.ss_newsletter_sections_subtitle,
            categoryRes = R.string.ss_cat_appearance,
            keywords = listOf("newsletter", "sections", "recently added", "activity log", "library stats", "continue watching", "next up", "curated picks", "content", "digest"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Mail,
            isAdvanced = true
        ),

        // Playback Settings
        SettingsSearchItem(
            id = "player_engine",
            titleRes = R.string.ss_player_engine_title,
            subtitleRes = R.string.ss_player_engine_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerPlay
        ),
        SettingsSearchItem(
            id = "seek_duration",
            titleRes = R.string.ss_seek_duration_title,
            subtitleRes = R.string.ss_seek_duration_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("seek", "duration", "skip", "double tap", "seconds"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerTrackNext
        ),
        SettingsSearchItem(
            id = "orientation",
            titleRes = R.string.ss_orientation_title,
            subtitleRes = R.string.ss_orientation_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("orientation", "rotation", "landscape", "portrait", "sensor"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.DeviceMobileRotated
        ),
        SettingsSearchItem(
            id = "gestures",
            titleRes = R.string.ss_gestures_title,
            subtitleRes = R.string.ss_gestures_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("gestures", "swipe", "brightness", "volume", "seeking"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.HandMove
        ),
        SettingsSearchItem(
            id = "gesture_indicator_side",
            titleRes = R.string.ss_gesture_indicator_side_title,
            subtitleRes = R.string.ss_gesture_indicator_side_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("indicator", "brightness", "volume", "bar", "side", "gesture", "opposite"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowsHorizontal
        ),
        SettingsSearchItem(
            id = "default_speed",
            titleRes = R.string.ss_default_speed_title,
            subtitleRes = R.string.ss_default_speed_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("speed", "rate", "fast", "slow", "playback speed"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "default_aspect",
            titleRes = R.string.ss_default_aspect_title,
            subtitleRes = R.string.ss_default_aspect_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("aspect", "ratio", "stretch", "zoom", "fit", "fill"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowAutofitHeight
        ),
        SettingsSearchItem(
            id = "video_autoplay_next",
            titleRes = R.string.ss_video_autoplay_next_title,
            subtitleRes = R.string.ss_video_autoplay_next_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("autoplay", "next", "continuous", "episode", "sequence"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerSkipForward
        ),
        SettingsSearchItem(
            id = "autoplay_countdown",
            titleRes = R.string.ss_autoplay_countdown_title,
            subtitleRes = R.string.ss_autoplay_countdown_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("countdown", "timer", "autoplay", "next"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "controls_timeout",
            titleRes = R.string.ss_controls_timeout_title,
            subtitleRes = R.string.ss_controls_timeout_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("controls", "timeout", "hide", "overlay"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "skip_back_on_resume",
            titleRes = R.string.ss_skip_back_on_resume_title,
            subtitleRes = R.string.ss_skip_back_on_resume_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("skip", "back", "resume", "rewind", "unpause", "seek"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.History,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_clock_player",
            titleRes = R.string.ss_show_clock_player_title,
            subtitleRes = R.string.ss_show_clock_player_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("clock", "time", "player", "wall", "current"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "pass_out_protection",
            titleRes = R.string.ss_pass_out_protection_title,
            subtitleRes = R.string.ss_pass_out_protection_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("pass out", "fall asleep", "auto pause", "sleep", "hours"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Moon,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "duck_on_transient_focus_loss",
            titleRes = R.string.ss_duck_on_transient_focus_loss_title,
            subtitleRes = R.string.ss_duck_on_transient_focus_loss_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("duck", "phone", "call", "focus", "transient", "volume", "rewind"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Phone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "autoplay_trailers",
            titleRes = R.string.ss_autoplay_trailers_title,
            subtitleRes = R.string.ss_autoplay_trailers_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("trailer", "autoplay", "preview", "details"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clipboard,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "cinema_mode",
            titleRes = R.string.ss_cinema_mode_title,
            subtitleRes = R.string.ss_cinema_mode_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("cinema", "intro", "preroll", "pre-roll", "trailer"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Video,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "episode_browser",
            titleRes = R.string.ss_episode_browser_title,
            subtitleRes = R.string.ss_episode_browser_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("episodes", "browser", "list", "in-player"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.List,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "playback_metadata",
            titleRes = R.string.ss_playback_metadata_title,
            subtitleRes = R.string.ss_playback_metadata_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("metadata", "codec", "bitrate", "stream stats", "debug"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.InfoCircle,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "swipe_seek_range",
            titleRes = R.string.ss_swipe_seek_range_title,
            subtitleRes = R.string.ss_swipe_seek_range_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("seek range", "swipe limit", "skip max"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowBarRight,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "remember_brightness",
            titleRes = R.string.ss_remember_brightness_title,
            subtitleRes = R.string.ss_remember_brightness_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("brightness", "remember", "save", "light"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.BrightnessHalf,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "trickplay_preview",
            titleRes = R.string.ss_trickplay_preview_title,
            subtitleRes = R.string.ss_trickplay_preview_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("trickplay", "thumbnails", "scrubbing", "preview", "seek preview"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Photo,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "preload_buffer",
            titleRes = R.string.ss_preload_buffer_title,
            subtitleRes = R.string.ss_preload_buffer_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("buffer", "preload", "cache", "size", "network cache"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "background_audio",
            titleRes = R.string.ss_background_audio_title,
            subtitleRes = R.string.ss_background_audio_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("background", "audio", "video background", "pip"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "keep_screen_on",
            titleRes = R.string.ss_keep_screen_on_title,
            subtitleRes = R.string.ss_keep_screen_on_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("screen", "awake", "lock", "stay on", "timeout"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Eye,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "incognito_mode",
            titleRes = R.string.ss_incognito_mode_title,
            subtitleRes = R.string.ss_incognito_mode_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("incognito", "private", "history", "stealth"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Ghost,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "dialogue_boost",
            titleRes = R.string.ss_dialogue_boost_title,
            subtitleRes = R.string.ss_dialogue_boost_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Microphone2,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "dialogue_boost_strength",
            titleRes = R.string.ss_dialogue_boost_strength_title,
            subtitleRes = R.string.ss_dialogue_boost_strength_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("dialogue", "boost", "strength", "level", "speech", "amplify"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Microphone2,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "decoder",
            titleRes = R.string.ss_decoder_title,
            subtitleRes = R.string.ss_decoder_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("decoder", "hardware", "software", "decoding", "codec"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.BadgeHd,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_passthrough",
            titleRes = R.string.ss_audio_passthrough_title,
            subtitleRes = R.string.ss_audio_passthrough_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("passthrough", "surround", "hdmi", "receiver", "raw"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Movie,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "frame_rate_matching",
            titleRes = R.string.ss_frame_rate_matching_title,
            subtitleRes = R.string.ss_frame_rate_matching_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("refresh rate", "frame rate", "hz", "judder", "tv"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Maximize,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "streaming_quality",
            titleRes = R.string.ss_streaming_quality_title,
            subtitleRes = R.string.ss_streaming_quality_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("quality", "streaming", "resolution", "4k", "1080p", "sd"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.BadgeHd,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_delay",
            titleRes = R.string.ss_audio_delay_title,
            subtitleRes = R.string.ss_audio_delay_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("delay", "latency", "sync", "lip sync", "bluetooth"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "live_stream_option",
            titleRes = R.string.ss_live_stream_option_title,
            subtitleRes = R.string.ss_live_stream_option_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("live tv", "direct stream", "transcode", "tuner", "htsp", "tvheadend", "channel", "mpeg-ts", "mpeg ts", "broadcast"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.DeviceTv,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hold_speed_multiplier",
            titleRes = R.string.ss_hold_speed_multiplier_title,
            subtitleRes = R.string.ss_hold_speed_multiplier_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("hold", "seek", "speed", "multiplier", "fast", "fast forward", "rewind", "long press", "off", "disable"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Rocket
        ),
        SettingsSearchItem(
            id = "android_tv_watch_next",
            titleRes = R.string.ss_android_tv_watch_next_title,
            subtitleRes = R.string.ss_android_tv_watch_next_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("android tv", "watch next", "home", "tv", "continue"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.DeviceTv,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "tv_zoom_mode",
            titleRes = R.string.ss_tv_zoom_mode_title,
            subtitleRes = R.string.ss_tv_zoom_mode_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("tv", "zoom", "crop", "fill", "screen"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Crop,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "default_brightness_level",
            titleRes = R.string.ss_default_brightness_level_title,
            subtitleRes = R.string.ss_default_brightness_level_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("brightness", "default", "screen", "light", "level"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Sun,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "trickplay_on_gestures",
            titleRes = R.string.ss_trickplay_on_gestures_title,
            subtitleRes = R.string.ss_trickplay_on_gestures_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("trickplay", "thumbnails", "gesture", "swipe", "seek"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.HandMove,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_time_remaining",
            titleRes = R.string.ss_show_time_remaining_title,
            subtitleRes = R.string.ss_show_time_remaining_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("time", "remaining", "elapsed", "duration", "countdown"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "pause_on_focus_loss",
            titleRes = R.string.ss_pause_on_focus_loss_title,
            subtitleRes = R.string.ss_pause_on_focus_loss_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("pause", "focus", "loss", "audio focus", "interruption"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerPause,
            isAdvanced = true
        ),
        // MPV Engine Config
        SettingsSearchItem(
            id = "mpv_video_output",
            titleRes = R.string.ss_mpv_video_output_title,
            subtitleRes = R.string.ss_mpv_video_output_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "video output", "vo", "gpu", "render"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Video,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_scaler",
            titleRes = R.string.ss_mpv_scaler_title,
            subtitleRes = R.string.ss_mpv_scaler_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "scaler", "scaling", "interpolation", "quality"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowAutofitHeight,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_debanding",
            titleRes = R.string.ss_mpv_debanding_title,
            subtitleRes = R.string.ss_mpv_debanding_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "deband", "debanding", "banding", "gradient"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ColorFilter,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_interpolation",
            titleRes = R.string.ss_mpv_interpolation_title,
            subtitleRes = R.string.ss_mpv_interpolation_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "interpolation", "smooth", "motion", "judder"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowsHorizontal,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_audio_output",
            titleRes = R.string.ss_mpv_audio_output_title,
            subtitleRes = R.string.ss_mpv_audio_output_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "audio output", "ao", "sound"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Volume,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_audio_fallback",
            titleRes = R.string.ss_mpv_audio_fallback_title,
            subtitleRes = R.string.ss_mpv_audio_fallback_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "audio", "fallback", "secondary", "output"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowBack,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_buffer_size",
            titleRes = R.string.ss_mpv_buffer_size_title,
            subtitleRes = R.string.ss_mpv_buffer_size_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "buffer", "demuxer", "size", "bytes", "cache"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Database,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_hwdec_override",
            titleRes = R.string.ss_mpv_hwdec_override_title,
            subtitleRes = R.string.ss_mpv_hwdec_override_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "hardware", "hwdec", "decoder", "override", "gpu"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Cpu,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_skip_loop_filter",
            titleRes = R.string.ss_mpv_skip_loop_filter_title,
            subtitleRes = R.string.ss_mpv_skip_loop_filter_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "skip", "loop filter", "h264", "performance"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Filter,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_frame_drop",
            titleRes = R.string.ss_mpv_frame_drop_title,
            subtitleRes = R.string.ss_mpv_frame_drop_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "frame", "drop", "vdrop", "performance"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PhotoDown,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "mpv_extra_config",
            titleRes = R.string.ss_mpv_extra_config_title,
            subtitleRes = R.string.ss_mpv_extra_config_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("mpv", "advanced", "config", "raw", "options", "editor", "custom"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Code,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "reset_engine_defaults",
            titleRes = R.string.ss_reset_engine_defaults_title,
            subtitleRes = R.string.ss_reset_engine_defaults_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("reset", "defaults", "restore", "engine", "mpv", "vlc", "exoplayer", "configuration"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        // VLC Engine Config
        SettingsSearchItem(
            id = "vlc_audio_output",
            titleRes = R.string.ss_vlc_audio_output_title,
            subtitleRes = R.string.ss_vlc_audio_output_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "libvlc", "audio output", "sound"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Volume,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "vlc_audio_time_stretch",
            titleRes = R.string.ss_vlc_audio_time_stretch_title,
            subtitleRes = R.string.ss_vlc_audio_time_stretch_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "time stretch", "pitch", "speed"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "vlc_video_output",
            titleRes = R.string.ss_vlc_video_output_title,
            subtitleRes = R.string.ss_vlc_video_output_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "libvlc", "video output", "display"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Video,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "vlc_network_caching",
            titleRes = R.string.ss_vlc_network_caching_title,
            subtitleRes = R.string.ss_vlc_network_caching_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "network", "caching", "buffer", "streaming"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Wifi,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "vlc_skip_loop_filter",
            titleRes = R.string.ss_vlc_skip_loop_filter_title,
            subtitleRes = R.string.ss_vlc_skip_loop_filter_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "skip", "loop filter", "h264", "quality"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Filter,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "vlc_skip_frames",
            titleRes = R.string.ss_vlc_skip_frames_title,
            subtitleRes = R.string.ss_vlc_skip_frames_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "skip frames", "performance", "frame"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerSkipForward,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "vlc_decoder_threads",
            titleRes = R.string.ss_vlc_decoder_threads_title,
            subtitleRes = R.string.ss_vlc_decoder_threads_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "decoder", "threads", "cpu", "multithreading"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Cpu,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "vlc_drop_late_frames",
            titleRes = R.string.ss_vlc_drop_late_frames_title,
            subtitleRes = R.string.ss_vlc_drop_late_frames_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("vlc", "drop", "late", "frames", "delayed"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Trash,
            isAdvanced = true
        ),
        // ExoPlayer Engine Config
        SettingsSearchItem(
            id = "exo_video_scaling",
            titleRes = R.string.ss_exo_video_scaling_title,
            subtitleRes = R.string.ss_exo_video_scaling_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("exoplayer", "exo", "scaling", "video", "resize"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowAutofitHeight,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "exo_frame_rate_strategy",
            titleRes = R.string.ss_exo_frame_rate_strategy_title,
            subtitleRes = R.string.ss_exo_frame_rate_strategy_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("exoplayer", "exo", "frame rate", "refresh", "strategy"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "exo_skip_silence",
            titleRes = R.string.ss_exo_skip_silence_title,
            subtitleRes = R.string.ss_exo_skip_silence_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("exoplayer", "exo", "skip", "silence", "audio", "gap"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Volume,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "exo_audio_offload",
            titleRes = R.string.ss_exo_audio_offload_title,
            subtitleRes = R.string.ss_exo_audio_offload_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("exoplayer", "exo", "audio", "offload", "battery"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Headphones,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "exo_decoder_fallback",
            titleRes = R.string.ss_exo_decoder_fallback_title,
            subtitleRes = R.string.ss_exo_decoder_fallback_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("exoplayer", "exo", "decoder", "fallback", "secondary"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ToggleLeft,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "exo_back_buffer",
            titleRes = R.string.ss_exo_back_buffer_title,
            subtitleRes = R.string.ss_exo_back_buffer_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("exoplayer", "exo", "back buffer", "rewind", "buffer"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Database,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "exo_preferred_codecs",
            titleRes = R.string.ss_exo_preferred_codecs_title,
            subtitleRes = R.string.ss_exo_preferred_codecs_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("exoplayer", "exo", "codec", "mime", "preferred", "video"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Code,
            isAdvanced = true
        ),
        // SyncPlay
        SettingsSearchItem(
            id = "syncplay_join_behavior",
            titleRes = R.string.ss_syncplay_join_behavior_title,
            subtitleRes = R.string.ss_syncplay_join_behavior_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("syncplay", "join", "behavior", "group", "watch party"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.MessageQuestion
        ),
        SettingsSearchItem(
            id = "syncplay_tolerance",
            titleRes = R.string.ss_syncplay_tolerance_title,
            subtitleRes = R.string.ss_syncplay_tolerance_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("syncplay", "tolerance", "drift", "sync", "correction"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.WaveSine
        ),
        SettingsSearchItem(
            id = "syncplay_auto_accept_invites",
            titleRes = R.string.ss_syncplay_auto_accept_invites_title,
            subtitleRes = R.string.ss_syncplay_auto_accept_invites_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("syncplay", "auto", "accept", "invites", "friends"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.CircleCheck
        ),
        // Casting & DLNA
        SettingsSearchItem(
            id = "casting_strategy",
            titleRes = R.string.ss_casting_strategy_title,
            subtitleRes = R.string.ss_casting_strategy_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("casting", "strategy", "dlna", "cast", "chromecast", "tv"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Cast
        ),
        SettingsSearchItem(
            id = "background_casting",
            titleRes = R.string.ss_background_casting_title,
            subtitleRes = R.string.ss_background_casting_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("casting", "background", "keep alive", "dlna", "cast"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Settings
        ),
        SettingsSearchItem(
            id = "preferred_renderer",
            titleRes = R.string.ss_preferred_renderer_title,
            subtitleRes = R.string.ss_preferred_renderer_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("renderer", "preferred", "cast", "device", "target", "tv"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Devices
        ),
        // Live TV & DVR
        SettingsSearchItem(
            id = "dvr_pre_padding",
            titleRes = R.string.ss_dvr_pre_padding_title,
            subtitleRes = R.string.ss_dvr_pre_padding_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("dvr", "pre padding", "recording", "live tv", "start", "early"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "dvr_post_padding",
            titleRes = R.string.ss_dvr_post_padding_title,
            subtitleRes = R.string.ss_dvr_post_padding_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("dvr", "post padding", "recording", "live tv", "end", "extend"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "dvr_recording_quality",
            titleRes = R.string.ss_dvr_recording_quality_title,
            subtitleRes = R.string.ss_dvr_recording_quality_subtitle,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("dvr", "recording", "quality", "live tv", "resolution"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.BadgeHd,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "media_segment_intro",
            titleRes = R.string.core_segment_intro,
            subtitleRes = R.string.core_segment_intro_desc,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("segment", "intro", "skip", "opening", "credits", "marker"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.SquareRounded,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "media_segment_outro",
            titleRes = R.string.core_segment_outro,
            subtitleRes = R.string.core_segment_outro_desc,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("segment", "outro", "ending", "skip", "credits", "marker"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.SquareRounded,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "media_segment_preview",
            titleRes = R.string.core_segment_preview,
            subtitleRes = R.string.core_segment_preview_desc,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("segment", "preview", "next episode", "recap", "skip", "marker"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.SquareRounded,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "media_segment_recap",
            titleRes = R.string.core_segment_recap,
            subtitleRes = R.string.core_segment_recap_desc,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("segment", "recap", "previously on", "skip", "marker"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.SquareRounded,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "media_segment_commercial",
            titleRes = R.string.core_segment_commercial,
            subtitleRes = R.string.core_segment_commercial_desc,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("segment", "commercial", "ad", "advertisement", "skip", "marker"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.SquareRounded,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "media_segment_unknown",
            titleRes = R.string.core_segment_unknown,
            subtitleRes = R.string.core_segment_unknown_desc,
            categoryRes = R.string.ss_cat_playback,
            keywords = listOf("segment", "unknown", "skip", "marker", "unidentified"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.SquareRounded,
            isAdvanced = true
        ),

        // Audio Player Settings
        SettingsSearchItem(
            id = "audio_default_speed",
            titleRes = R.string.ss_audio_default_speed_title,
            subtitleRes = R.string.ss_audio_default_speed_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio speed", "pitch", "podcast speed", "music rate"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "audio_visualizer",
            titleRes = R.string.ss_audio_visualizer_title,
            subtitleRes = R.string.ss_audio_visualizer_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("visualizer", "fft", "spectrum", "music wave", "effects"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Eye
        ),
        SettingsSearchItem(
            id = "sleep_timer",
            titleRes = R.string.ss_sleep_timer_title,
            subtitleRes = R.string.ss_sleep_timer_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("sleep", "timer", "pause", "bedtime"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "audio_description",
            titleRes = R.string.ss_audio_description_title,
            subtitleRes = R.string.ss_audio_description_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio description", "narrated", "accessibility", "visually impaired"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone
        ),
        SettingsSearchItem(
            id = "gapless_playback",
            titleRes = R.string.ss_gapless_playback_title,
            subtitleRes = R.string.ss_gapless_playback_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("gapless", "seamless", "transition", "silence"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.PlaylistAdd,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "crossfade",
            titleRes = R.string.ss_crossfade_title,
            subtitleRes = R.string.ss_crossfade_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("crossfade", "fade", "transition", "overlap"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "volume_normalization",
            titleRes = R.string.ss_volume_normalization_title,
            subtitleRes = R.string.ss_volume_normalization_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("normalization", "volume", "replaygain", "compression", "gain"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "equalizer",
            titleRes = R.string.ss_equalizer_title,
            subtitleRes = R.string.ss_equalizer_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("equalizer", "eq", "bands", "bass", "treble", "audio profile"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "bass_boost",
            titleRes = R.string.ss_bass_boost_title,
            subtitleRes = R.string.ss_bass_boost_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("bass", "boost", "low end", "subwoofer", "amplify"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.WaveSine,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "virtualizer",
            titleRes = R.string.ss_virtualizer_title,
            subtitleRes = R.string.ss_virtualizer_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("virtualizer", "spatial", "3d", "surround", "headphones"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "volume_boost",
            titleRes = R.string.ss_volume_boost_title,
            subtitleRes = R.string.ss_volume_boost_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("volume boost", "boost", "loudness", "gain", "preamp"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "reverb",
            titleRes = R.string.ss_reverb_title,
            subtitleRes = R.string.ss_reverb_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("reverb", "acoustic", "environment", "room", "hall"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.WaveSine,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "channel_mixing",
            titleRes = R.string.ss_channel_mixing_title,
            subtitleRes = R.string.ss_channel_mixing_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("mixing", "channel", "mono", "stereo", "surround"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "lr_balance",
            titleRes = R.string.ss_lr_balance_title,
            subtitleRes = R.string.ss_lr_balance_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("balance", "left", "right", "stereo balance"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_autoplay_next",
            titleRes = R.string.ss_audio_autoplay_next_title,
            subtitleRes = R.string.ss_audio_autoplay_next_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "autoplay", "next", "track", "music", "continuous"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.PlaylistAdd
        ),
        SettingsSearchItem(
            id = "night_mode_volume",
            titleRes = R.string.ss_night_mode_volume_title,
            subtitleRes = R.string.ss_night_mode_volume_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("night mode", "volume", "max", "limit", "quiet"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "night_mode_gain",
            titleRes = R.string.ss_night_mode_gain_title,
            subtitleRes = R.string.ss_night_mode_gain_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("night mode", "gain", "loudness", "compensation", "boost"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_skip_prev_threshold",
            titleRes = R.string.ss_audio_skip_prev_threshold_title,
            subtitleRes = R.string.ss_audio_skip_prev_threshold_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("skip", "previous", "threshold", "restart", "song", "rewind"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.PlayerSkipForward,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_preload_buffer",
            titleRes = R.string.ss_audio_preload_buffer_title,
            subtitleRes = R.string.ss_audio_preload_buffer_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "preload", "buffer", "cache", "ahead"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_caching_enabled",
            titleRes = R.string.ss_audio_caching_enabled_title,
            subtitleRes = R.string.ss_audio_caching_enabled_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "cache", "caching", "prefetch", "buffer", "plexamp", "music"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Database
        ),
        SettingsSearchItem(
            id = "audio_cache_size",
            titleRes = R.string.ss_audio_cache_size_title,
            subtitleRes = R.string.ss_audio_cache_size_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "cache", "size", "disk", "storage"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.DeviceFloppy
        ),
        SettingsSearchItem(
            id = "audio_prefetch_lookahead",
            titleRes = R.string.ss_audio_prefetch_lookahead_title,
            subtitleRes = R.string.ss_audio_prefetch_lookahead_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "prefetch", "lookahead", "buffering", "music", "queue"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_prefetch_backfill",
            titleRes = R.string.ss_audio_prefetch_backfill_title,
            subtitleRes = R.string.ss_audio_prefetch_backfill_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "prefetch", "backfill", "buffering", "music", "previous"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_cache_clear",
            titleRes = R.string.ss_audio_cache_clear_title,
            subtitleRes = R.string.ss_audio_cache_clear_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "cache", "clear", "music", "storage", "wipe"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Trash,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_cache_network_policy",
            titleRes = R.string.ss_audio_cache_network_policy_title,
            subtitleRes = R.string.ss_audio_cache_network_policy_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("audio", "cache", "network", "wifi", "cellular", "metered"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Wifi
        ),
        SettingsSearchItem(
            id = "replaygain_preamp",
            titleRes = R.string.ss_replaygain_preamp_title,
            subtitleRes = R.string.ss_replaygain_preamp_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("replaygain", "preamp", "pre-amp", "loudness", "gain", "target"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "equalizer_preset",
            titleRes = R.string.ss_equalizer_preset_title,
            subtitleRes = R.string.ss_equalizer_preset_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("equalizer", "preset", "eq", "profile", "bass", "treble"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "night_mode",
            titleRes = R.string.ss_night_mode_title,
            subtitleRes = R.string.ss_night_mode_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("night mode", "audio", "evening", "quiet", "soft"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Gauge,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "night_mode_strength",
            titleRes = R.string.ss_night_mode_strength_title,
            subtitleRes = R.string.ss_night_mode_strength_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("night mode", "strength", "intensity", "audio", "level"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Moon,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "bass_boost_strength",
            titleRes = R.string.ss_bass_boost_strength_title,
            subtitleRes = R.string.ss_bass_boost_strength_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("bass", "boost", "strength", "intensity", "low end", "subwoofer"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.WaveSine,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "virtualizer_strength",
            titleRes = R.string.ss_virtualizer_strength_title,
            subtitleRes = R.string.ss_virtualizer_strength_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("virtualizer", "strength", "spatial", "3d", "surround"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "volume_boost_gain",
            titleRes = R.string.ss_volume_boost_gain_title,
            subtitleRes = R.string.ss_volume_boost_gain_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("volume boost", "gain", "loudness", "preamp", "level"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "auto_eq_by_genre",
            titleRes = R.string.ss_auto_eq_by_genre_title,
            subtitleRes = R.string.ss_auto_eq_by_genre_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("auto eq", "genre", "automatic", "equalizer", "preset", "music"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Wand,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "channel_mix_mode",
            titleRes = R.string.ss_channel_mix_mode_title,
            subtitleRes = R.string.ss_channel_mix_mode_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("channel", "mix", "mode", "surround", "stereo", "downmix"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "pitch_shift",
            titleRes = R.string.ss_pitch_shift_title,
            subtitleRes = R.string.ss_pitch_shift_subtitle,
            categoryRes = R.string.ss_cat_audio_player,
            keywords = listOf("pitch", "shift", "semitone", "tone", "key", "audio"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.WaveSine,
            isAdvanced = true
        ),

        // Language & Subtitles
        SettingsSearchItem(
            id = "app_language",
            titleRes = R.string.ss_app_language_title,
            subtitleRes = R.string.ss_app_language_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("language", "display", "interface", "locale", "ui language", "app language"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Language
        ),
        SettingsSearchItem(
            id = "audio_language",
            titleRes = R.string.ss_audio_language_title,
            subtitleRes = R.string.ss_audio_language_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("language", "audio track", "speech", "default language"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Language
        ),
        SettingsSearchItem(
            id = "subtitle_language",
            titleRes = R.string.ss_subtitle_language_title,
            subtitleRes = R.string.ss_subtitle_language_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("subtitles", "language", "cc", "captions"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Subtitles
        ),
        SettingsSearchItem(
            id = "subtitle_font_size",
            titleRes = R.string.ss_subtitle_font_size_title,
            subtitleRes = R.string.ss_subtitle_font_size_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("subtitle size", "font size", "text size", "bigger"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Typography
        ),
        SettingsSearchItem(
            id = "subtitle_forced_only",
            titleRes = R.string.ss_subtitle_forced_only_title,
            subtitleRes = R.string.ss_subtitle_forced_only_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("forced", "subtitles", "foreign", "parts", "native"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.TextSize
        ),
        SettingsSearchItem(
            id = "pgs_direct_play",
            titleRes = R.string.ss_pgs_direct_play_title,
            subtitleRes = R.string.ss_pgs_direct_play_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("pgs", "subtitle", "direct play", "picture", "image subtitle", "bluray"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Photo,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hdr_subtitle_style",
            titleRes = R.string.ss_hdr_subtitle_style_title,
            subtitleRes = R.string.ss_hdr_subtitle_style_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("hdr", "subtitle", "style", "dolby vision", "hdr10", "brightness"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Sun,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_color",
            titleRes = R.string.ss_subtitle_color_title,
            subtitleRes = R.string.ss_subtitle_color_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("subtitle color", "text color", "yellow subtitles", "white"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Palette,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_background",
            titleRes = R.string.ss_subtitle_background_title,
            subtitleRes = R.string.ss_subtitle_background_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("subtitle background", "opacity", "transparency", "box"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Background,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_edge_style",
            titleRes = R.string.ss_subtitle_edge_style_title,
            subtitleRes = R.string.ss_subtitle_edge_style_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("edge style", "shadow", "outline", "border"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.BorderAll,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_sync_offset",
            titleRes = R.string.ss_subtitle_sync_offset_title,
            subtitleRes = R.string.ss_subtitle_sync_offset_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("sync", "offset", "delay", "lagging subtitles"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_vertical_position",
            titleRes = R.string.ss_subtitle_vertical_position_title,
            subtitleRes = R.string.ss_subtitle_vertical_position_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("position", "height", "vertical", "bottom", "margin"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.ArrowBarDown,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "high_contrast_subtitles",
            titleRes = R.string.ss_high_contrast_subtitles_title,
            subtitleRes = R.string.ss_high_contrast_subtitles_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("subtitle", "high", "contrast", "accessibility", "visibility"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Contrast2,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_tester",
            titleRes = R.string.ss_subtitle_tester_title,
            subtitleRes = R.string.ss_subtitle_tester_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("subtitle", "tester", "preview", "sample", "test", "style"),
            route = Route.SubtitleTester,
            icon = Tabler.Outline.EyeCheck
        ),
        SettingsSearchItem(
            id = "hdr_subtitle_font_size",
            titleRes = R.string.ss_hdr_subtitle_font_size_title,
            subtitleRes = R.string.ss_hdr_subtitle_font_size_subtitle,
            categoryRes = R.string.ss_cat_language_subtitles,
            keywords = listOf("hdr", "subtitle", "font size", "text", "dolby vision"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Typography,
            isAdvanced = true
        ),

        // Notifications
        SettingsSearchItem(
            id = "notifications_enable",
            titleRes = R.string.ss_notifications_enable_title,
            subtitleRes = R.string.ss_notifications_enable_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("notifications", "frequency", "bell", "check frequency", "alerts"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Bell
        ),
        SettingsSearchItem(
            id = "respect_system_dnd",
            titleRes = R.string.ss_respect_system_dnd_title,
            subtitleRes = R.string.ss_respect_system_dnd_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("dnd", "do not disturb", "quiet", "silent", "notification policy"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.BellOff,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "system_notification_settings",
            titleRes = R.string.ss_system_notification_settings_title,
            subtitleRes = R.string.ss_system_notification_settings_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("system", "notification", "channel", "settings", "customize"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Settings,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "notification_check_frequency",
            titleRes = R.string.ss_notification_check_frequency_title,
            subtitleRes = R.string.ss_notification_check_frequency_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("notification", "check", "frequency", "interval", "polling", "new media"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "quiet_hours",
            titleRes = R.string.ss_quiet_hours_title,
            subtitleRes = R.string.ss_quiet_hours_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("quiet hours", "suppress", "silent", "night", "do not disturb"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Moon,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "quiet_start",
            titleRes = R.string.ss_quiet_start_title,
            subtitleRes = R.string.ss_quiet_start_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("quiet hours", "start", "begin", "night", "silent"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Sunset,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "quiet_end",
            titleRes = R.string.ss_quiet_end_title,
            subtitleRes = R.string.ss_quiet_end_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("quiet hours", "end", "morning", "silent"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Sunrise,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "notification_sound",
            titleRes = R.string.ss_notification_sound_title,
            subtitleRes = R.string.ss_notification_sound_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("notification", "sound", "audio", "alert", "tone"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Volume
        ),
        SettingsSearchItem(
            id = "notification_vibrate",
            titleRes = R.string.ss_notification_vibrate_title,
            subtitleRes = R.string.ss_notification_vibrate_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("notification", "vibrate", "vibration", "haptic", "buzz"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.PhoneCall
        ),
        SettingsSearchItem(
            id = "notification_lights",
            titleRes = R.string.ss_notification_lights_title,
            subtitleRes = R.string.ss_notification_lights_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("notification", "lights", "led", "pulse", "blink"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Bulb
        ),
        SettingsSearchItem(
            id = "max_per_check",
            titleRes = R.string.ss_max_per_check_title,
            subtitleRes = R.string.ss_max_per_check_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("max", "per check", "batch", "items", "limit", "notification"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.LetterCase,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "notification_libraries",
            titleRes = R.string.ss_notification_libraries_title,
            subtitleRes = R.string.ss_notification_libraries_subtitle,
            categoryRes = R.string.ss_cat_notifications,
            keywords = listOf("notification", "libraries", "folders", "monitor", "per library"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Folders,
            isAdvanced = true
        ),

        // Storage, Network & Offline
        SettingsSearchItem(
            id = "clear_cache",
            titleRes = R.string.ss_clear_cache_title,
            subtitleRes = R.string.ss_clear_cache_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("clear cache", "trash", "free space", "clean", "reset"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Trash
        ),
        SettingsSearchItem(
            id = "clear_image_cache",
            titleRes = R.string.ss_clear_image_cache_title,
            subtitleRes = R.string.ss_clear_image_cache_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("image cache", "clear images", "posters", "thumbnails", "coil"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Photo
        ),
        SettingsSearchItem(
            id = "wifi_only_downloads",
            titleRes = R.string.ss_wifi_only_downloads_title,
            subtitleRes = R.string.ss_wifi_only_downloads_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("wifi only", "downloads", "cellular downloads", "data saving"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Wifi,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "auto_delete_cache",
            titleRes = R.string.ss_auto_delete_cache_title,
            subtitleRes = R.string.ss_auto_delete_cache_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("auto delete", "cache limit", "disk full"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "max_cache_size",
            titleRes = R.string.ss_max_cache_size_title,
            subtitleRes = R.string.ss_max_cache_size_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("max cache", "size limit", "cache limit"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Database,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "offline_mode",
            titleRes = R.string.ss_offline_mode_title,
            subtitleRes = R.string.ss_offline_mode_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("offline", "airplane mode", "no network", "local only"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.CloudOff
        ),
        SettingsSearchItem(
            id = "adaptive_bitrate",
            titleRes = R.string.ss_adaptive_bitrate_title,
            subtitleRes = R.string.ss_adaptive_bitrate_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("adaptive bitrate", "network", "bandwidth", "cellular", "buffer"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "bandwidth_cap",
            titleRes = R.string.ss_bandwidth_cap_title,
            subtitleRes = R.string.ss_bandwidth_cap_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("bandwidth cap", "limit", "throttle", "data cap"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Lock
        ),
        SettingsSearchItem(
            id = "data_saver",
            titleRes = R.string.ss_data_saver_title,
            subtitleRes = R.string.ss_data_saver_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("data saver", "saving", "cellular usage", "bandwidth"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "cellular_download_warning",
            titleRes = R.string.ss_cellular_download_warning_title,
            subtitleRes = R.string.ss_cellular_download_warning_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("cellular", "download", "warning", "size", "data", "mobile"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.AlertTriangle,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "network_timeout",
            titleRes = R.string.ss_network_timeout_title,
            subtitleRes = R.string.ss_network_timeout_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("timeout", "network", "connect", "read", "write", "slow"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "verbose_logging",
            titleRes = R.string.ss_verbose_logging_title,
            subtitleRes = R.string.ss_verbose_logging_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("verbose", "debug", "logging", "network", "http", "developer"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Code,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "user_data_sync",
            titleRes = R.string.ss_user_data_sync_title,
            subtitleRes = R.string.ss_user_data_sync_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("sync", "background", "user-data", "favorites", "played", "progress", "worker"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "download_quality",
            titleRes = R.string.ss_download_quality_title,
            subtitleRes = R.string.ss_download_quality_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("download quality", "offline quality", "1080p downloads"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Video
        ),
        SettingsSearchItem(
            id = "smart_downloads",
            titleRes = R.string.ss_smart_downloads_title,
            subtitleRes = R.string.ss_smart_downloads_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("smart downloads", "auto delete", "episodes", "clean space"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Trash
        ),
        SettingsSearchItem(
            id = "download_connections",
            titleRes = R.string.ss_download_connections_title,
            subtitleRes = R.string.ss_download_connections_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("connections", "parallel", "streams", "download", "segments"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Download,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "max_concurrent_downloads",
            titleRes = R.string.ss_max_concurrent_downloads_title,
            subtitleRes = R.string.ss_max_concurrent_downloads_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("concurrent", "simultaneous", "parallel", "downloads", "max", "queue"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.ArrowBarToDown,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "auto_offline",
            titleRes = R.string.ss_auto_offline_title,
            subtitleRes = R.string.ss_auto_offline_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("auto offline", "network lost", "offline", "automatic", "disconnect"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.WifiOff
        ),
        SettingsSearchItem(
            id = "metered_network_behavior",
            titleRes = R.string.ss_metered_network_behavior_title,
            subtitleRes = R.string.ss_metered_network_behavior_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("metered", "cellular", "behavior", "data", "mobile", "network"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Compass
        ),
        SettingsSearchItem(
            id = "cellular_streaming_quality",
            titleRes = R.string.ss_cellular_streaming_quality_title,
            subtitleRes = R.string.ss_cellular_streaming_quality_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("cellular", "streaming", "quality", "mobile", "data", "resolution"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.DeviceMobile
        ),
        SettingsSearchItem(
            id = "auto_download_new_episodes",
            titleRes = R.string.ss_auto_download_new_episodes_title,
            subtitleRes = R.string.ss_auto_download_new_episodes_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("auto download", "new episodes", "automatic", "next", "series"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Download
        ),
        SettingsSearchItem(
            id = "download_schedule",
            titleRes = R.string.ss_download_schedule_title,
            subtitleRes = R.string.ss_download_schedule_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("download", "schedule", "hours", "overnight", "window", "time"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "download_schedule_start",
            titleRes = R.string.ss_download_schedule_start_title,
            subtitleRes = R.string.ss_download_schedule_start_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("download", "schedule", "start", "hour", "window", "begin"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Sun
        ),
        SettingsSearchItem(
            id = "download_schedule_end",
            titleRes = R.string.ss_download_schedule_end_title,
            subtitleRes = R.string.ss_download_schedule_end_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("download", "schedule", "end", "hour", "window", "stop"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Moon
        ),
        SettingsSearchItem(
            id = "download_schedule_wifi_only",
            titleRes = R.string.ss_download_schedule_wifi_only_title,
            subtitleRes = R.string.ss_download_schedule_wifi_only_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("download", "schedule", "wifi only", "unmetered", "require"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Wifi
        ),
        SettingsSearchItem(
            id = "max_download_storage_limit",
            titleRes = R.string.ss_max_download_storage_limit_title,
            subtitleRes = R.string.ss_max_download_storage_limit_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("download", "storage", "limit", "max", "size", "cap", "gb"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Database
        ),
        SettingsSearchItem(
            id = "download_storage_location",
            titleRes = R.string.ss_download_storage_location_title,
            subtitleRes = R.string.ss_download_storage_location_subtitle,
            categoryRes = R.string.ss_cat_storage,
            keywords = listOf("download", "storage", "location", "folder", "sd card", "internal"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Folder
        ),

        // Security
        SettingsSearchItem(
            id = "pin_lock",
            titleRes = R.string.ss_pin_lock_title,
            subtitleRes = R.string.ss_pin_lock_subtitle,
            categoryRes = R.string.ss_cat_security,
            keywords = listOf("pin", "lock", "code", "password", "security"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Lock
        ),
        SettingsSearchItem(
            id = "biometric_lock",
            titleRes = R.string.ss_biometric_lock_title,
            subtitleRes = R.string.ss_biometric_lock_subtitle,
            categoryRes = R.string.ss_cat_security,
            keywords = listOf("biometric", "fingerprint", "face lock", "iris", "sensors"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Fingerprint
        ),
        SettingsSearchItem(
            id = "pin_for_player_lock",
            titleRes = R.string.ss_pin_for_player_lock_title,
            subtitleRes = R.string.ss_pin_for_player_lock_subtitle,
            categoryRes = R.string.ss_cat_security,
            keywords = listOf("pin", "player", "lock", "unlock", "screen lock"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Key
        ),
        SettingsSearchItem(
            id = "quick_connect_authorize",
            titleRes = R.string.ss_quick_connect_authorize_title,
            subtitleRes = R.string.ss_quick_connect_authorize_subtitle,
            categoryRes = R.string.ss_cat_security,
            keywords = listOf("quick connect", "authorize", "approve", "code", "device", "pair"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Bolt
        ),
        SettingsSearchItem(
            id = "remote_control_enabled",
            titleRes = R.string.ss_remote_control_enabled_title,
            subtitleRes = R.string.ss_remote_control_enabled_subtitle,
            categoryRes = R.string.ss_cat_security,
            keywords = listOf("remote", "control", "cast", "play to", "external control", "receive commands"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Cast
        ),
        SettingsSearchItem(
            id = "auto_lock_timer",
            titleRes = R.string.ss_auto_lock_timer_title,
            subtitleRes = R.string.ss_auto_lock_timer_subtitle,
            categoryRes = R.string.ss_cat_security,
            keywords = listOf("auto lock", "timer", "lock", "timeout", "delay", "security"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),

        // Backup
        SettingsSearchItem(
            id = "backup_export",
            titleRes = R.string.ss_backup_export_title,
            subtitleRes = R.string.ss_backup_export_subtitle,
            categoryRes = R.string.ss_cat_backup_restore,
            keywords = listOf("backup", "export", "save config", "migration"),
            route = Route.BackupSettings(),
            icon = Tabler.Outline.DatabaseExport
        ),
        SettingsSearchItem(
            id = "backup_import",
            titleRes = R.string.ss_backup_import_title,
            subtitleRes = R.string.ss_backup_import_subtitle,
            categoryRes = R.string.ss_cat_backup_restore,
            keywords = listOf("import", "restore", "load config", "backup restore"),
            route = Route.BackupSettings(),
            icon = Tabler.Outline.DatabaseImport
        ),
        SettingsSearchItem(
            id = "factory_reset",
            titleRes = R.string.ss_factory_reset_title,
            subtitleRes = R.string.ss_factory_reset_subtitle,
            categoryRes = R.string.ss_cat_backup_restore,
            keywords = listOf("factory", "reset", "defaults", "clear", "wipe"),
            route = Route.BackupSettings(),
            icon = Tabler.Outline.AlertTriangle,
            isAdvanced = true
        ),

        // About
        SettingsSearchItem(
            id = "about_version",
            titleRes = R.string.ss_about_version_title,
            subtitleRes = R.string.ss_about_version_subtitle,
            categoryRes = R.string.ss_cat_about,
            keywords = listOf("about", "version", "licenses", "open source", "developer"),
            route = Route.About,
            icon = Tabler.Outline.InfoCircle
        ),

        // Experimental
        SettingsSearchItem(
            id = "experimental",
            titleRes = R.string.ss_experimental_title,
            subtitleRes = R.string.ss_experimental_subtitle,
            categoryRes = R.string.ss_cat_experimental,
            keywords = listOf("experimental", "beta", "labs", "preview", "early access", "developer"),
            route = Route.ExperimentalSettings(),
            icon = Tabler.Outline.Flask
        ),
        SettingsSearchItem(
            id = "HOME_CARD_CLIPPING",
            titleRes = R.string.ss_HOME_CARD_CLIPPING_title,
            subtitleRes = R.string.ss_HOME_CARD_CLIPPING_subtitle,
            categoryRes = R.string.ss_cat_experimental,
            keywords = listOf("home", "card", "clipping", "render", "experimental"),
            route = Route.ExperimentalSettings(),
            icon = Tabler.Outline.Photo,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "MEDIA_CARD_PEEK",
            titleRes = R.string.ss_MEDIA_CARD_PEEK_title,
            subtitleRes = R.string.ss_MEDIA_CARD_PEEK_subtitle,
            categoryRes = R.string.ss_cat_experimental,
            keywords = listOf("press", "hold", "peek", "preview", "media card", "long press", "experimental"),
            route = Route.ExperimentalSettings(),
            icon = Tabler.Outline.HandFinger,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "DIRECT_ARR_INTEGRATION",
            titleRes = R.string.ss_DIRECT_ARR_INTEGRATION_title,
            subtitleRes = R.string.ss_DIRECT_ARR_INTEGRATION_subtitle,
            categoryRes = R.string.ss_cat_experimental,
            keywords = listOf("radarr", "sonarr", "arr", "download", "queue", "calendar", "coming soon", "grabbed", "imported"),
            route = Route.ExperimentalSettings(),
            icon = Tabler.Outline.Download
        ),
        SettingsSearchItem(
            id = "arr_settings",
            titleRes = R.string.ss_arr_settings_title,
            subtitleRes = R.string.ss_arr_settings_subtitle,
            categoryRes = R.string.ss_cat_integrations,
            keywords = listOf("radarr", "sonarr", "arr", "servers", "api key", "integration"),
            route = Route.ArrSettings(),
            icon = Tabler.Outline.Download
        )
    )
}
