package com.raulshma.jellyplay.feature.admin.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.admin.dashboard.AdminDashboardScreen
import com.raulshma.jellyplay.feature.admin.devices.DevicesScreen
import com.raulshma.jellyplay.feature.admin.logs.LogsScreen
import com.raulshma.jellyplay.feature.admin.tasks.ScheduledTasksScreen

fun EntryProviderScope<NavKey>.adminSection(
    navigator: Navigator,
) {
    entry<Route.AdminDashboard> {
        AdminDashboardScreen(
            onBack = { navigator.goBack() },
            onScheduledTasks = { navigator.navigate(Route.ScheduledTasks) },
            onDevices = { navigator.navigate(Route.Devices) },
            onLogs = { navigator.navigate(Route.Logs) },
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
}
