package com.raulshma.jellyplay.feature.admin.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.admin.analytics.JellyPlayAnalyticsScreen
import com.raulshma.jellyplay.feature.admin.backups.AdminBackupsScreen
import com.raulshma.jellyplay.feature.admin.dashboard.AdminDashboardScreen
import com.raulshma.jellyplay.feature.admin.devices.DevicesScreen
import com.raulshma.jellyplay.feature.admin.logs.LogsScreen
import com.raulshma.jellyplay.feature.admin.plugins.PluginConfigHost
import com.raulshma.jellyplay.feature.admin.plugins.PluginDetailScreen
import com.raulshma.jellyplay.feature.admin.plugins.PluginsScreen
import com.raulshma.jellyplay.feature.admin.statistics.UserStatisticsScreen
import com.raulshma.jellyplay.feature.admin.statistics.detail.UserStatisticsDetailScreen
import com.raulshma.jellyplay.feature.admin.stalemedia.StaleMediaScreen
import com.raulshma.jellyplay.feature.admin.tasks.ScheduledTasksScreen
import com.raulshma.jellyplay.feature.admin.transcodes.JellyPlayTranscodesScreen
import com.raulshma.jellyplay.feature.admin.users.UsersScreen
import com.raulshma.jellyplay.feature.admin.users.detail.UserDetailScreen
import com.raulshma.jellyplay.feature.admin.watchedremoval.WatchedMediaCleanupScreen

fun EntryProviderScope<NavKey>.adminSection(
    navigator: Navigator,
    isAdmin: () -> Boolean,
    isRefreshingAdmin: () -> Boolean,
    onRefreshAdmin: () -> Unit,
) {
    // Every admin route is wrapped by AdminRouteContainer, which enforces
    // access control in one place: it re-validates admin status against the
    // server on entry and renders an AccessDeniedScreen for non-admins. This
    // covers navigate(), deep-links, and start-destination alike — closing the
    // gap where admin routes were reachable without any client-side check.
    // The wrap itself is bound once in AdminRouteEntry below — each entry
    // here supplies only its screen content.
    entry<Route.AdminDashboard> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            AdminDashboardScreen(
                onBack = { navigator.goBack() },
                onScheduledTasks = { navigator.navigate(Route.ScheduledTasks) },
                onDevices = { navigator.navigate(Route.Devices) },
                onLogs = { navigator.navigate(Route.Logs) },
                onUserStatistics = { navigator.navigate(Route.UserStatistics) },
                onStaleMedia = { navigator.navigate(Route.StaleMedia) },
                onWatchedMediaCleanup = { navigator.navigate(Route.WatchedMediaCleanup) },
                onPlugins = { navigator.navigate(Route.Plugins) },
                onUsers = { navigator.navigate(Route.Users) },
                onBackups = { navigator.navigate(Route.AdminBackups) },
                onTranscodes = { navigator.navigate(Route.JellyPlayTranscodes) },
                onAnalytics = { navigator.navigate(Route.JellyPlayAnalytics) },
            )
        }
    }

    entry<Route.ScheduledTasks> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            ScheduledTasksScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Devices> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            DevicesScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Logs> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            LogsScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.JellyPlayTranscodes> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            JellyPlayTranscodesScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.JellyPlayAnalytics> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            JellyPlayAnalyticsScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.AdminBackups> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            AdminBackupsScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.UserStatistics> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            UserStatisticsScreen(
                onBack = { navigator.goBack() },
                onUserDetail = { userId -> navigator.navigate(Route.UserStatisticsDetail(userId)) },
            )
        }
    }

    entry<Route.UserStatisticsDetail> { route ->
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            UserStatisticsDetailScreen(
                userId = route.userId,
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.StaleMedia> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            StaleMediaScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.WatchedMediaCleanup> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            WatchedMediaCleanupScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Users> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            UsersScreen(
                onBack = { navigator.goBack() },
                onUserDetail = { userId -> navigator.navigate(Route.UserDetail(userId)) },
            )
        }
    }

    entry<Route.UserDetail> { route ->
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            UserDetailScreen(
                userId = route.userId,
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Plugins> {
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            PluginsScreen(
                onBack = { navigator.goBack() },
                onPluginDetail = { pluginId, pluginName ->
                    navigator.navigate(Route.PluginDetail(pluginId, pluginName))
                },
            )
        }
    }

    entry<Route.PluginDetail> { route ->
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            PluginDetailScreen(
                pluginId = route.pluginId,
                pluginName = route.pluginName,
                onBack = { navigator.goBack() },
                onConfig = { pluginId, pluginName ->
                    navigator.navigate(Route.PluginConfig(pluginId, pluginName))
                },
            )
        }
    }

    entry<Route.PluginConfig> { route ->
        AdminRouteEntry(navigator, isAdmin, isRefreshingAdmin, onRefreshAdmin) {
            PluginConfigHost(
                pluginId = route.pluginId,
                pluginName = route.pluginName,
                onBack = { navigator.goBack() },
            )
        }
    }
}

/**
 * The [AdminRouteContainer] invocation every entry above repeats — the four
 * fixed arguments (back navigation + the admin gate trio) bound once here,
 * each entry supplying only its screen content. One AdminRouteContainer call
 * per entry composition with the same fresh lambdas as before: zero behavior
 * change, file-private so no signature changes outside this file.
 */
@Composable
private fun AdminRouteEntry(
    navigator: Navigator,
    isAdmin: () -> Boolean,
    isRefreshingAdmin: () -> Boolean,
    onRefreshAdmin: () -> Unit,
    screen: @Composable () -> Unit,
) {
    AdminRouteContainer(
        onBack = { navigator.goBack() },
        isAdmin = isAdmin,
        isRefreshingAdmin = isRefreshingAdmin,
        onRefreshAdmin = onRefreshAdmin,
    ) {
        screen()
    }
}
