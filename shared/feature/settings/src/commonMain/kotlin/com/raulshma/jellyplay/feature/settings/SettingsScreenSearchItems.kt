package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discord_presence_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_after
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_percent
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_max_parental_rating
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_ended_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_idle_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_idle_ended_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_play_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_stop_cmd
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_discord_presence_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_ended_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_idle_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_idle_ended_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_play_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_stop_cmd_subtitle
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_dim_after_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_dim_percent_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_ken_burns_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_ken_burns_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_max_parental_rating_subtitle
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
    const val SCREENSAVER_MAX_PARENTAL_RATING = "screensaver_max_parental_rating"
    const val SCREENSAVER_DIM_AFTER = "screensaver_dim_after"
    const val SCREENSAVER_DIM_PERCENT = "screensaver_dim_percent"
    const val IDLE_AMBIENT_ENABLED = "idle_ambient_enabled"
    const val IDLE_AMBIENT_TIMEOUT = "idle_ambient_timeout"
    const val DISCORD_PRESENCE_ENABLED = "discord_presence_enabled"
    const val HOOKS_ENABLED = "hooks_enabled"
    const val HOOKS_PLAY_CMD = "hooks_play_cmd"
    const val HOOKS_STOP_CMD = "hooks_stop_cmd"
    const val HOOKS_ENDED_CMD = "hooks_ended_cmd"
    const val HOOKS_IDLE_CMD = "hooks_idle_cmd"
    const val HOOKS_IDLE_ENDED_CMD = "hooks_idle_ended_cmd"
}

/**
 * Settings-search items for the "Account / Users / Servers" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 *
 * HAND-MAINTAINED: the account/session rows are navigation facts whose knobs
 * live in stores without spec machinery — they stay feature-side records.
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
 *
 * HAND-MAINTAINED: navigation facts whose knobs live in stores without spec
 * machinery — they stay feature-side records.
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


// ═══════════════════════════════════════════════════════════════════════
// The spec-derived derivation inputs for the System group: the
// dream/desktop-shell rows' searchable semantics live on
// [ScreensaverPreferenceSpecs]; this file declares only the id →
// resource/icon binding table, the routeKind → Route map, and the
// derivation call. The record list below stays the ordered spine — the
// catalog order, the [rowTitle]/[rowIcon] screen faces and the two
// navigation rows' residual hand search faces.
// ═══════════════════════════════════════════════════════════════════════

private val systemSearchRoutes: Map<String, Route> = mapOf(
    ScreensaverPreferenceSpecs.ROUTE_SETTINGS to Route.Settings,
)

private val systemSpecEntries: List<PreferenceSearchSpec> = ScreensaverPreferenceSpecs.searchEntries

private fun specsFor(bindings: List<SettingsSearchBinding>): List<PreferenceSearchSpec> {
    val ids = bindings.map { it.id }.toSet()
    val matched = systemSpecEntries.filter { it.id in ids }
    val missing = ids - matched.map { it.id }.toSet()
    require(missing.isEmpty()) { "settings-search binding ids without a spec entry: $missing" }
    return matched
}

private val systemCategory = CoreUiRes.string.ss_cat_system

/**
 * The System group's binding table — the search faces of its 18 spec-backed
 * rows (the dream group, the desktop idle-ambient pair, the Discord presence
 * toggle and the shell-hook rows). The admin-dashboard and setup-wizard
 * navigation rows are the group's feature-side residuals.
 */
private val systemBindings = listOf(
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_SHOW_TITLE, Res.string.ss_screensaver_show_title_title, Res.string.ss_screensaver_show_title_subtitle, systemCategory, Tabler.Outline.Typography),
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_CATEGORIES, Res.string.ss_screensaver_categories_title, Res.string.ss_screensaver_categories_subtitle, systemCategory, Tabler.Outline.Folders),
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_SLIDESHOW_INTERVAL, Res.string.ss_screensaver_slideshow_interval_title, Res.string.ss_screensaver_slideshow_interval_subtitle, systemCategory, Tabler.Outline.Clock),
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_KEN_BURNS, Res.string.ss_screensaver_ken_burns_title, Res.string.ss_screensaver_ken_burns_subtitle, systemCategory, Tabler.Outline.Movie),
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_TRANSITION_STYLE, Res.string.ss_screensaver_transition_style_title, Res.string.ss_screensaver_transition_style_subtitle, systemCategory, Tabler.Outline.ArrowsHorizontal),
    // The fold rows restate their screen titles.
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_MAX_PARENTAL_RATING, Res.string.settings_dream_max_parental_rating, Res.string.ss_screensaver_max_parental_rating_subtitle, systemCategory, Tabler.Outline.Shield),
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_DIM_AFTER, Res.string.settings_dream_dim_after, Res.string.ss_screensaver_dim_after_subtitle, systemCategory, Tabler.Outline.Hourglass),
    SettingsSearchBinding(SettingsScreenIds.SCREENSAVER_DIM_PERCENT, Res.string.settings_dream_dim_percent, Res.string.ss_screensaver_dim_percent_subtitle, systemCategory, Tabler.Outline.Sun),
    SettingsSearchBinding(
        SettingsScreenIds.IDLE_AMBIENT_ENABLED,
        Res.string.ss_idle_ambient_enabled_title,
        Res.string.ss_idle_ambient_enabled_subtitle,
        systemCategory,
        Tabler.Outline.Moon,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    ),
    SettingsSearchBinding(
        SettingsScreenIds.IDLE_AMBIENT_TIMEOUT,
        Res.string.ss_idle_ambient_timeout_title,
        Res.string.ss_idle_ambient_timeout_subtitle,
        systemCategory,
        Tabler.Outline.Stopwatch,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    ),
    SettingsSearchBinding(SettingsScreenIds.DISCORD_PRESENCE_ENABLED, Res.string.settings_discord_presence_enabled, Res.string.ss_discord_presence_enabled_subtitle, systemCategory, Tabler.Outline.BrandDiscord),
    SettingsSearchBinding(SettingsScreenIds.HOOKS_ENABLED, Res.string.settings_hooks_enabled, Res.string.ss_hooks_enabled_subtitle, systemCategory, Tabler.Outline.Terminal2),
    SettingsSearchBinding(SettingsScreenIds.HOOKS_PLAY_CMD, Res.string.settings_hooks_play_cmd, Res.string.ss_hooks_play_cmd_subtitle, systemCategory, Tabler.Outline.Terminal2),
    SettingsSearchBinding(SettingsScreenIds.HOOKS_STOP_CMD, Res.string.settings_hooks_stop_cmd, Res.string.ss_hooks_stop_cmd_subtitle, systemCategory, Tabler.Outline.Terminal2),
    SettingsSearchBinding(SettingsScreenIds.HOOKS_ENDED_CMD, Res.string.settings_hooks_ended_cmd, Res.string.ss_hooks_ended_cmd_subtitle, systemCategory, Tabler.Outline.Terminal2),
    SettingsSearchBinding(SettingsScreenIds.HOOKS_IDLE_CMD, Res.string.settings_hooks_idle_cmd, Res.string.ss_hooks_idle_cmd_subtitle, systemCategory, Tabler.Outline.Terminal2),
    SettingsSearchBinding(SettingsScreenIds.HOOKS_IDLE_ENDED_CMD, Res.string.settings_hooks_idle_ended_cmd, Res.string.ss_hooks_idle_ended_cmd_subtitle, systemCategory, Tabler.Outline.Terminal2),
)

/**
 * Settings-search items for the "System" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to the main SettingsScreen. Aggregated in [SettingsSearchCatalog].
 *
 * Spec-derived for the dream + desktop-shell rows ([ScreensaverPreferenceSpecs]);
 * the admin-dashboard and setup-wizard navigation rows stay feature-side
 * residuals — their knobs live in stores without spec machinery.
 */
internal val SystemRowRecords = listOf(
    // ── RESIDUAL rows (navigation facts, spec-less stores).
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
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_SHOW_TITLE, titleRes = Res.string.settings_show_title, icon = Tabler.Outline.Typography),
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_CATEGORIES, titleRes = Res.string.settings_categories, icon = Tabler.Outline.Folders),
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_SLIDESHOW_INTERVAL, titleRes = Res.string.settings_slideshow_interval, icon = Tabler.Outline.Clock),
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_KEN_BURNS, titleRes = Res.string.settings_ken_burns, icon = Tabler.Outline.Movie),
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_TRANSITION_STYLE, titleRes = Res.string.settings_transition_style, icon = Tabler.Outline.ArrowsHorizontal),
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_MAX_PARENTAL_RATING, titleRes = Res.string.settings_dream_max_parental_rating, icon = Tabler.Outline.Shield),
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_DIM_AFTER, titleRes = Res.string.settings_dream_dim_after, icon = Tabler.Outline.Hourglass),
    SettingsRowRecord(id = SettingsScreenIds.SCREENSAVER_DIM_PERCENT, titleRes = Res.string.settings_dream_dim_percent, icon = Tabler.Outline.Sun),
    SettingsRowRecord(id = SettingsScreenIds.IDLE_AMBIENT_ENABLED, titleRes = Res.string.settings_idle_ambient_enabled, icon = Tabler.Outline.Moon),
    SettingsRowRecord(id = SettingsScreenIds.IDLE_AMBIENT_TIMEOUT, titleRes = Res.string.settings_idle_ambient_timeout, icon = Tabler.Outline.Stopwatch),
    SettingsRowRecord(id = SettingsScreenIds.DISCORD_PRESENCE_ENABLED, titleRes = Res.string.settings_discord_presence_enabled, icon = Tabler.Outline.BrandDiscord),
    SettingsRowRecord(id = SettingsScreenIds.HOOKS_ENABLED, titleRes = Res.string.settings_hooks_enabled, icon = Tabler.Outline.Terminal2),
    SettingsRowRecord(id = SettingsScreenIds.HOOKS_PLAY_CMD, titleRes = Res.string.settings_hooks_play_cmd, icon = Tabler.Outline.Terminal2),
    SettingsRowRecord(id = SettingsScreenIds.HOOKS_STOP_CMD, titleRes = Res.string.settings_hooks_stop_cmd, icon = Tabler.Outline.Terminal2),
    SettingsRowRecord(id = SettingsScreenIds.HOOKS_ENDED_CMD, titleRes = Res.string.settings_hooks_ended_cmd, icon = Tabler.Outline.Terminal2),
    SettingsRowRecord(id = SettingsScreenIds.HOOKS_IDLE_CMD, titleRes = Res.string.settings_hooks_idle_cmd, icon = Tabler.Outline.Terminal2),
    SettingsRowRecord(id = SettingsScreenIds.HOOKS_IDLE_ENDED_CMD, titleRes = Res.string.settings_hooks_idle_ended_cmd, icon = Tabler.Outline.Terminal2),
)

/** The catalog projection of the spec-backed system rows + the navigation residuals. */
internal val SystemSearchItems: List<SettingsSearchItem> =
    SystemRowRecords.toSearchItems(
        specEntries = specsFor(systemBindings),
        bindings = systemBindings,
        routes = systemSearchRoutes,
        categoryRes = systemCategory,
    )
