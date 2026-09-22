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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_timeout_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_timeout_subtitle
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
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object SettingsScreenIds {
    const val LOGOUT = "logout"
    const val SIGN_OUT_FROM_SERVER = "sign_out_from_server"
    const val SERVER_MANAGEMENT = "server_management"
    const val USER_MANAGEMENT = "user_management"
    const val FAVORITES = "favorites"
    const val WATCH_PROGRESS_HEATMAP = "watch_progress_heatmap"
    const val ACTIVITY_QUEUE = "activity_queue"
    const val UPCOMING = "upcoming"
    const val REQUESTS = "requests"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val SETUP_WIZARD = "setup_wizard"
    const val SCREENSAVER_SHOW_TITLE = "screensaver_show_title"
    const val SCREENSAVER_CATEGORIES = "screensaver_categories"
    const val SCREENSAVER_SLIDESHOW_INTERVAL = "screensaver_slideshow_interval"
    const val SCREENSAVER_KEN_BURNS = "screensaver_ken_burns"
    const val SCREENSAVER_TRANSITION_STYLE = "screensaver_transition_style"
    const val IDLE_AMBIENT_ENABLED = "idle_ambient_enabled"
    const val IDLE_AMBIENT_TIMEOUT = "idle_ambient_timeout"
}

/**
 * Settings-search items for the "Account / Users / Servers" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val AccountSearchItems = listOf(
    SettingsSearchItem(
        id = SettingsScreenIds.LOGOUT,
        titleRes = Res.string.ss_logout_title,
        subtitleRes = Res.string.ss_logout_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_account,
        keywords = listOf("sign out", "logout", "exit", "disconnect"),
        route = Route.Settings,
        icon = Tabler.Outline.Logout
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SIGN_OUT_FROM_SERVER,
        titleRes = Res.string.ss_sign_out_from_server_title,
        subtitleRes = Res.string.ss_sign_out_from_server_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_account,
        keywords = listOf("sign out", "server", "remove device", "revoke", "session", "remote", "disconnect"),
        route = Route.Settings,
        icon = Tabler.Outline.Logout
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SERVER_MANAGEMENT,
        titleRes = Res.string.ss_server_management_title,
        subtitleRes = Res.string.ss_server_management_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_account,
        keywords = listOf("server", "connection", "jellyfin", "address", "switch"),
        route = Route.ServerManagement(),
        icon = Tabler.Outline.Server
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.USER_MANAGEMENT,
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
        id = SettingsScreenIds.FAVORITES,
        titleRes = Res.string.ss_favorites_title,
        subtitleRes = Res.string.ss_favorites_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("favorites", "favourite", "liked", "collection", "heart"),
        route = Route.Favorites,
        icon = Tabler.Outline.Heart
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.WATCH_PROGRESS_HEATMAP,
        titleRes = Res.string.ss_watch_progress_heatmap_title,
        subtitleRes = Res.string.ss_watch_progress_heatmap_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("watch", "history", "heatmap", "progress", "activity", "stats"),
        route = Route.WatchProgressHeatmap,
        icon = Tabler.Outline.ChartBar
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.ACTIVITY_QUEUE,
        titleRes = Res.string.ss_activity_queue_title,
        subtitleRes = Res.string.ss_activity_queue_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("activity", "queue", "download", "radarr", "sonarr", "arr", "import"),
        route = Route.ArrQueue,
        icon = Tabler.Outline.Database
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.UPCOMING,
        titleRes = Res.string.ss_upcoming_title,
        subtitleRes = Res.string.ss_upcoming_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_activity_insights,
        keywords = listOf("upcoming", "calendar", "schedule", "new", "episodes", "soon"),
        route = Route.UpcomingCalendar,
        icon = Tabler.Outline.CalendarEvent
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.REQUESTS,
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
        id = SettingsScreenIds.ADMIN_DASHBOARD,
        titleRes = Res.string.ss_admin_dashboard_title,
        subtitleRes = Res.string.ss_admin_dashboard_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("admin", "dashboard", "sessions", "server", "management"),
        route = Route.AdminDashboard,
        icon = Tabler.Outline.Shield
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SETUP_WIZARD,
        titleRes = Res.string.ss_setup_wizard_title,
        subtitleRes = Res.string.ss_setup_wizard_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("setup", "wizard", "onboarding", "configure", "initial"),
        route = Route.Onboarding,
        icon = Tabler.Outline.Wand
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SCREENSAVER_SHOW_TITLE,
        titleRes = Res.string.ss_screensaver_show_title_title,
        subtitleRes = Res.string.ss_screensaver_show_title_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "title", "tv", "show", "media title", "display"),
        route = Route.Settings,
        icon = Tabler.Outline.Typography
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SCREENSAVER_CATEGORIES,
        titleRes = Res.string.ss_screensaver_categories_title,
        subtitleRes = Res.string.ss_screensaver_categories_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "categories", "tv", "movies", "music", "content"),
        route = Route.Settings,
        icon = Tabler.Outline.Folders
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SCREENSAVER_SLIDESHOW_INTERVAL,
        titleRes = Res.string.ss_screensaver_slideshow_interval_title,
        subtitleRes = Res.string.ss_screensaver_slideshow_interval_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "slideshow", "interval", "tv", "duration", "seconds"),
        route = Route.Settings,
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SCREENSAVER_KEN_BURNS,
        titleRes = Res.string.ss_screensaver_ken_burns_title,
        subtitleRes = Res.string.ss_screensaver_ken_burns_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "ken burns", "pan", "zoom", "animation", "tv"),
        route = Route.Settings,
        icon = Tabler.Outline.Movie
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.SCREENSAVER_TRANSITION_STYLE,
        titleRes = Res.string.ss_screensaver_transition_style_title,
        subtitleRes = Res.string.ss_screensaver_transition_style_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("screensaver", "dream", "transition", "style", "crossfade", "slide", "tv"),
        route = Route.Settings,
        icon = Tabler.Outline.ArrowsHorizontal
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.IDLE_AMBIENT_ENABLED,
        titleRes = Res.string.ss_idle_ambient_enabled_title,
        subtitleRes = Res.string.ss_idle_ambient_enabled_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("idle", "ambient", "ready to play", "screensaver", "desktop", "standby"),
        route = Route.Settings,
        icon = Tabler.Outline.Moon,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    ),
    SettingsSearchItem(
        id = SettingsScreenIds.IDLE_AMBIENT_TIMEOUT,
        titleRes = Res.string.ss_idle_ambient_timeout_title,
        subtitleRes = Res.string.ss_idle_ambient_timeout_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_system,
        keywords = listOf("idle", "ambient", "timeout", "minutes", "screensaver", "desktop", "standby"),
        route = Route.Settings,
        icon = Tabler.Outline.Stopwatch,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    ),
)
