package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_account
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_activity_insights
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_system
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_queue
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_admin_dashboard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_browse_favorites
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_categories
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_timeout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ken_burns
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_requests
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_management
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_setup_wizard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_from_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_slideshow_interval
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_switch_user
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_history_heatmap
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_activity_queue_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_activity_queue_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_admin_dashboard_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_admin_dashboard_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_timeout_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_timeout_title
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
internal val AccountRowRecords = listOf(
    SettingsRowRecord(
        id = SettingsScreenIds.LOGOUT,
        titleRes = Res.string.settings_sign_out,
        searchTitleRes = Res.string.ss_logout_title,
        searchSubtitleRes = Res.string.ss_logout_subtitle,
        keywords = listOf("sign out", "logout", "exit", "disconnect"),
        route = Route.Settings,
        icon = Tabler.Outline.Logout
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SIGN_OUT_FROM_SERVER,
        titleRes = Res.string.settings_sign_out_from_server,
        searchTitleRes = Res.string.ss_sign_out_from_server_title,
        searchSubtitleRes = Res.string.ss_sign_out_from_server_subtitle,
        keywords = listOf("sign out", "server", "remove device", "revoke", "session", "remote", "disconnect"),
        route = Route.Settings,
        icon = Tabler.Outline.Logout
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SERVER_MANAGEMENT,
        titleRes = Res.string.settings_server_management,
        searchTitleRes = Res.string.ss_server_management_title,
        searchSubtitleRes = Res.string.ss_server_management_subtitle,
        keywords = listOf("server", "connection", "jellyfin", "address", "switch"),
        route = Route.ServerManagement(),
        icon = Tabler.Outline.Server
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.USER_MANAGEMENT,
        titleRes = Res.string.settings_switch_user,
        searchTitleRes = Res.string.ss_user_management_title,
        searchSubtitleRes = Res.string.ss_user_management_subtitle,
        keywords = listOf("user", "accounts", "profile", "switch user", "admin"),
        route = Route.UserManagement(),
        icon = Tabler.Outline.Users
    ))

/** The catalog projection of `AccountRowRecords`: the search faces + the shared category. */
internal val AccountSearchItems: List<SettingsSearchItem> = AccountRowRecords.toSearchItems(CoreUiRes.string.ss_cat_account)


/**
 * Settings-search items for the "Activity & Insights" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val ActivityInsightsRowRecords = listOf(
    SettingsRowRecord(
        id = SettingsScreenIds.FAVORITES,
        titleRes = Res.string.settings_browse_favorites,
        searchTitleRes = Res.string.ss_favorites_title,
        searchSubtitleRes = Res.string.ss_favorites_subtitle,
        keywords = listOf("favorites", "favourite", "liked", "collection", "heart"),
        route = Route.Favorites,
        icon = Tabler.Outline.Heart
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.WATCH_PROGRESS_HEATMAP,
        titleRes = Res.string.settings_watch_history_heatmap,
        searchTitleRes = Res.string.ss_watch_progress_heatmap_title,
        searchSubtitleRes = Res.string.ss_watch_progress_heatmap_subtitle,
        keywords = listOf("watch", "history", "heatmap", "progress", "activity", "stats"),
        route = Route.WatchProgressHeatmap,
        icon = Tabler.Outline.ChartBar
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.ACTIVITY_QUEUE,
        titleRes = Res.string.settings_activity_queue,
        searchTitleRes = Res.string.ss_activity_queue_title,
        searchSubtitleRes = Res.string.ss_activity_queue_subtitle,
        keywords = listOf("activity", "queue", "download", "radarr", "sonarr", "arr", "import"),
        route = Route.ArrQueue,
        icon = Tabler.Outline.Database
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.UPCOMING,
        titleRes = Res.string.settings_upcoming,
        searchTitleRes = Res.string.ss_upcoming_title,
        searchSubtitleRes = Res.string.ss_upcoming_subtitle,
        keywords = listOf("upcoming", "calendar", "schedule", "new", "episodes", "soon"),
        route = Route.UpcomingCalendar,
        icon = Tabler.Outline.CalendarEvent
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.REQUESTS,
        titleRes = Res.string.settings_requests,
        searchTitleRes = Res.string.ss_requests_title,
        searchSubtitleRes = Res.string.ss_requests_subtitle,
        keywords = listOf("requests", "seerr", "jellyseerr", "pending", "approve"),
        route = Route.Requests,
        icon = Tabler.Outline.Inbox
    ))

/** The catalog projection of `ActivityInsightsRowRecords`: the search faces + the shared category. */
internal val ActivityInsightsSearchItems: List<SettingsSearchItem> = ActivityInsightsRowRecords.toSearchItems(CoreUiRes.string.ss_cat_activity_insights)


/**
 * Settings-search items for the "System" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val SystemRowRecords = listOf(
    SettingsRowRecord(
        id = SettingsScreenIds.ADMIN_DASHBOARD,
        titleRes = Res.string.settings_admin_dashboard,
        searchTitleRes = Res.string.ss_admin_dashboard_title,
        searchSubtitleRes = Res.string.ss_admin_dashboard_subtitle,
        keywords = listOf("admin", "dashboard", "sessions", "server", "management"),
        route = Route.AdminDashboard,
        icon = Tabler.Outline.Shield
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SETUP_WIZARD,
        titleRes = Res.string.settings_setup_wizard,
        searchTitleRes = Res.string.ss_setup_wizard_title,
        searchSubtitleRes = Res.string.ss_setup_wizard_subtitle,
        keywords = listOf("setup", "wizard", "onboarding", "configure", "initial"),
        route = Route.Onboarding,
        icon = Tabler.Outline.Wand
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SCREENSAVER_SHOW_TITLE,
        titleRes = Res.string.settings_show_title,
        searchTitleRes = Res.string.ss_screensaver_show_title_title,
        searchSubtitleRes = Res.string.ss_screensaver_show_title_subtitle,
        keywords = listOf("screensaver", "dream", "title", "tv", "show", "media title", "display"),
        route = Route.Settings,
        icon = Tabler.Outline.Typography
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SCREENSAVER_CATEGORIES,
        titleRes = Res.string.settings_categories,
        searchTitleRes = Res.string.ss_screensaver_categories_title,
        searchSubtitleRes = Res.string.ss_screensaver_categories_subtitle,
        keywords = listOf("screensaver", "dream", "categories", "tv", "movies", "music", "content"),
        route = Route.Settings,
        icon = Tabler.Outline.Folders
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SCREENSAVER_SLIDESHOW_INTERVAL,
        titleRes = Res.string.settings_slideshow_interval,
        searchTitleRes = Res.string.ss_screensaver_slideshow_interval_title,
        searchSubtitleRes = Res.string.ss_screensaver_slideshow_interval_subtitle,
        keywords = listOf("screensaver", "dream", "slideshow", "interval", "tv", "duration", "seconds"),
        route = Route.Settings,
        icon = Tabler.Outline.Clock
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SCREENSAVER_KEN_BURNS,
        titleRes = Res.string.settings_ken_burns,
        searchTitleRes = Res.string.ss_screensaver_ken_burns_title,
        searchSubtitleRes = Res.string.ss_screensaver_ken_burns_subtitle,
        keywords = listOf("screensaver", "dream", "ken burns", "pan", "zoom", "animation", "tv"),
        route = Route.Settings,
        icon = Tabler.Outline.Movie
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.SCREENSAVER_TRANSITION_STYLE,
        titleRes = Res.string.settings_transition_style,
        searchTitleRes = Res.string.ss_screensaver_transition_style_title,
        searchSubtitleRes = Res.string.ss_screensaver_transition_style_subtitle,
        keywords = listOf("screensaver", "dream", "transition", "style", "crossfade", "slide", "tv"),
        route = Route.Settings,
        icon = Tabler.Outline.ArrowsHorizontal
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.IDLE_AMBIENT_ENABLED,
        titleRes = Res.string.settings_idle_ambient_enabled,
        searchTitleRes = Res.string.ss_idle_ambient_enabled_title,
        searchSubtitleRes = Res.string.ss_idle_ambient_enabled_subtitle,
        keywords = listOf("idle", "ambient", "ready to play", "screensaver", "desktop", "standby"),
        route = Route.Settings,
        icon = Tabler.Outline.Moon,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    ),
    SettingsRowRecord(
        id = SettingsScreenIds.IDLE_AMBIENT_TIMEOUT,
        titleRes = Res.string.settings_idle_ambient_timeout,
        searchTitleRes = Res.string.ss_idle_ambient_timeout_title,
        searchSubtitleRes = Res.string.ss_idle_ambient_timeout_subtitle,
        keywords = listOf("idle", "ambient", "timeout", "minutes", "screensaver", "desktop", "standby"),
        route = Route.Settings,
        icon = Tabler.Outline.Stopwatch,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    ))

/** The catalog projection of `SystemRowRecords`: the search faces + the shared category. */
internal val SystemSearchItems: List<SettingsSearchItem> = SystemRowRecords.toSearchItems(CoreUiRes.string.ss_cat_system)

