package com.raulshma.jellyplay.feature.admin.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.admin.dashboard.AdminDashboardScreen
import com.raulshma.jellyplay.feature.admin.devices.DevicesScreen
import com.raulshma.jellyplay.feature.admin.logs.LogsScreen
import com.raulshma.jellyplay.feature.admin.statistics.UserStatisticsScreen
import com.raulshma.jellyplay.feature.admin.statistics.detail.UserStatisticsDetailScreen
import com.raulshma.jellyplay.feature.admin.stalemedia.StaleMediaScreen
import com.raulshma.jellyplay.feature.admin.tasks.ScheduledTasksScreen
import com.raulshma.jellyplay.feature.admin.watchedremoval.WatchedMediaCleanupScreen

fun EntryProviderScope<NavKey>.adminSection(
    navigator: Navigator,
) {
    entry<Route.AdminDashboard> {
        AdminDashboardScreen(
            onBack = { navigator.goBack() },
            onScheduledTasks = { navigator.navigate(Route.ScheduledTasks) },
            onDevices = { navigator.navigate(Route.Devices) },
            onLogs = { navigator.navigate(Route.Logs) },
            onUserStatistics = { navigator.navigate(Route.UserStatistics) },
            onStaleMedia = { navigator.navigate(Route.StaleMedia) },
            onWatchedMediaCleanup = { navigator.navigate(Route.WatchedMediaCleanup) },
        )
    }

    entry<Route.ScheduledTasks> {
        ScheduledTasksScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.Devices> {
        DevicesScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.Logs> {
        LogsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.UserStatistics> {
        UserStatisticsScreen(
            onBack = { navigator.goBack() },
            onUserDetail = { userId -> navigator.navigate(Route.UserStatisticsDetail(userId)) },
        )
    }

    entry<Route.UserStatisticsDetail> { route ->
        UserStatisticsDetailScreen(
            userId = route.userId,
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.StaleMedia> {
        StaleMediaScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.WatchedMediaCleanup> {
        WatchedMediaCleanupScreen(
            onBack = { navigator.goBack() },
        )
    }
}
