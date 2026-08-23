package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_account
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_activity_insights
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_system
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_activity_queue_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_activity_queue_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_admin_dashboard_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_admin_dashboard_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_logout_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_logout_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_requests_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_requests_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_categories_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_categories_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_ken_burns_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_ken_burns_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_show_title_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_show_title_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_slideshow_interval_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_slideshow_interval_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_transition_style_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_transition_style_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_server_management_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_server_management_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_setup_wizard_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_setup_wizard_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_sign_out_from_server_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_sign_out_from_server_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_upcoming_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_upcoming_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_user_management_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_user_management_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_watch_progress_heatmap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_watch_progress_heatmap_title

/**
 * Settings-search items for the "Account / Users / Servers" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val AccountSearchItems = listOf(
    SettingsSearchItem(
        id = "logout",
        titleRes = Res.string.ss_logout_title,
        subtitleRes = Res.string.ss_logout_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_account,
        keywords = listOf("sign out", "logout", "exit", "disconnect"),
        route = Route.Settings,
        icon = Tabler.Outline.Logout
    ),
    SettingsSearchItem(
        id = "sign_out_from_server",
        titleRes = Res.string.ss_sign_out_from_server_title,
        subtitleRes = Res.string.ss_sign_out_from_server_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_account,
        keywords = listOf("sign out", "server", "remove device", "revoke", "session", "remote", "disconnect"),
        route = Route.Settings,
        icon = Tabler.Outline.Logout
    ),
    SettingsSearchItem(
        id = "server_management",
        titleRes = Res.string.ss_server_management_title,
        subtitleRes = Res.string.ss_server_management_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_account,
        keywords = listOf("server", "connection", "jellyfin", "address", "switch"),
        route = Route.ServerManagement(),
        icon = Tabler.Outline.Server
    ),
    SettingsSearchItem(
        id = "user_management",
        titleRes = Res.string.ss_user_management_title,
        subtitleRes = Res.string.ss_user_management_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_account,
        keywords = listOf("user", "accounts", "profile", "switch user", "admin"),
        route = Route.UserManagement(),
        icon = Tabler.Outline.Users
    ),
)

/**
 * Settings-search items for the "Activity & Insights" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val ActivityInsightsSearchItems = listOf(
    SettingsSearchItem(
        id = "favorites",
        titleRes = Res.string.ss_favorites_title,
        subtitleRes = Res.string.ss_favorites_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("favorites", "favourite", "liked", "collection", "heart"),
        route = Route.Favorites,
        icon = Tabler.Outline.Heart
    ),
    SettingsSearchItem(
        id = "watch_progress_heatmap",
        titleRes = Res.string.ss_watch_progress_heatmap_title,
        subtitleRes = Res.string.ss_watch_progress_heatmap_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("watch", "history", "heatmap", "progress", "activity", "stats"),
        route = Route.WatchProgressHeatmap,
        icon = Tabler.Outline.ChartBar
    ),
    SettingsSearchItem(
        id = "activity_queue",
        titleRes = Res.string.ss_activity_queue_title,
        subtitleRes = Res.string.ss_activity_queue_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("activity", "queue", "download", "radarr", "sonarr", "arr", "import"),
        route = Route.ArrQueue,
        icon = Tabler.Outline.Database
    ),
    SettingsSearchItem(
        id = "upcoming",
        titleRes = Res.string.ss_upcoming_title,
        subtitleRes = Res.string.ss_upcoming_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("upcoming", "calendar", "schedule", "new", "episodes", "soon"),
        route = Route.UpcomingCalendar,
        icon = Tabler.Outline.CalendarEvent
    ),
    SettingsSearchItem(
        id = "requests",
        titleRes = Res.string.ss_requests_title,
        subtitleRes = Res.string.ss_requests_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("requests", "seerr", "jellyseerr", "pending", "approve"),
        route = Route.Requests,
        icon = Tabler.Outline.Inbox
    ),
)

/**
 * Settings-search items for the "System" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val SystemSearchItems = listOf(
    SettingsSearchItem(
        id = "admin_dashboard",
        titleRes = Res.string.ss_admin_dashboard_title,
        subtitleRes = Res.string.ss_admin_dashboard_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("admin", "dashboard", "sessions", "server", "management"),
        route = Route.AdminDashboard,
        icon = Tabler.Outline.Shield
    ),
    SettingsSearchItem(
        id = "setup_wizard",
        titleRes = Res.string.ss_setup_wizard_title,
        subtitleRes = Res.string.ss_setup_wizard_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("setup", "wizard", "onboarding", "configure", "initial"),
        route = Route.Onboarding,
        icon = Tabler.Outline.Wand
    ),
    SettingsSearchItem(
        id = "screensaver_show_title",
        titleRes = Res.string.ss_screensaver_show_title_title,
        subtitleRes = Res.string.ss_screensaver_show_title_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "title", "tv", "show", "media title", "display"),
        route = Route.Settings,
        icon = Tabler.Outline.Typography
    ),
    SettingsSearchItem(
        id = "screensaver_categories",
        titleRes = Res.string.ss_screensaver_categories_title,
        subtitleRes = Res.string.ss_screensaver_categories_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "categories", "tv", "movies", "music", "content"),
        route = Route.Settings,
        icon = Tabler.Outline.Folders
    ),
    SettingsSearchItem(
        id = "screensaver_slideshow_interval",
        titleRes = Res.string.ss_screensaver_slideshow_interval_title,
        subtitleRes = Res.string.ss_screensaver_slideshow_interval_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "slideshow", "interval", "tv", "duration", "seconds"),
        route = Route.Settings,
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "screensaver_ken_burns",
        titleRes = Res.string.ss_screensaver_ken_burns_title,
        subtitleRes = Res.string.ss_screensaver_ken_burns_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "ken burns", "pan", "zoom", "animation", "tv"),
        route = Route.Settings,
        icon = Tabler.Outline.Movie
    ),
    SettingsSearchItem(
        id = "screensaver_transition_style",
        titleRes = Res.string.ss_screensaver_transition_style_title,
        subtitleRes = Res.string.ss_screensaver_transition_style_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "transition", "style", "crossfade", "slide", "tv"),
        route = Route.Settings,
        icon = Tabler.Outline.ArrowsHorizontal
    ),
)
