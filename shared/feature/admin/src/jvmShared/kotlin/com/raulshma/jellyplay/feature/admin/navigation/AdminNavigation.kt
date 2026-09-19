package com.raulshma.jellyplay.feature.admin.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
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
    entry<Route.AdminDashboard> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
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
            )
        }
    }

    entry<Route.ScheduledTasks> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            ScheduledTasksScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Devices> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            DevicesScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Logs> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            LogsScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.UserStatistics> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            UserStatisticsScreen(
                onBack = { navigator.goBack() },
                onUserDetail = { userId -> navigator.navigate(Route.UserStatisticsDetail(userId)) },
            )
        }
    }

    entry<Route.UserStatisticsDetail> { route ->
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            UserStatisticsDetailScreen(
                userId = route.userId,
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.StaleMedia> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            StaleMediaScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.WatchedMediaCleanup> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            WatchedMediaCleanupScreen(
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Users> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            UsersScreen(
                onBack = { navigator.goBack() },
                onUserDetail = { userId -> navigator.navigate(Route.UserDetail(userId)) },
            )
        }
    }

    entry<Route.UserDetail> { route ->
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            UserDetailScreen(
                userId = route.userId,
                onBack = { navigator.goBack() },
            )
        }
    }

    entry<Route.Plugins> {
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            PluginsScreen(
                onBack = { navigator.goBack() },
                onPluginDetail = { pluginId, pluginName ->
                    navigator.navigate(Route.PluginDetail(pluginId, pluginName))
                },
            )
        }
    }

    entry<Route.PluginDetail> { route ->
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
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
        AdminRouteContainer(
            onBack = { navigator.goBack() },
            isAdmin = isAdmin,
            isRefreshingAdmin = isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ) {
            PluginConfigHost(
                pluginId = route.pluginId,
                pluginName = route.pluginName,
                onBack = { navigator.goBack() },
            )
        }
    }
}
