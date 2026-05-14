package com.raulshma.jellyplay.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.SeerrSettingsScreen
import com.raulshma.jellyplay.feature.settings.ServerManagementScreen
import com.raulshma.jellyplay.feature.settings.SettingsScreen
import com.raulshma.jellyplay.feature.settings.UserManagementScreen

fun EntryProviderScope<NavKey>.settingsSection(
    navigator: Navigator,
    onLogout: () -> Unit,
) {
    entry<Route.Settings> {
        SettingsScreen(
            onBack = { navigator.goBack() },
            onLogout = onLogout,
            onServerManagement = { navigator.navigate(Route.ServerManagement) },
            onUserManagement = { navigator.navigate(Route.UserManagement) },
            onSeerrSettings = { navigator.navigate(Route.SeerrSettings) },
        )
    }

    entry<Route.ServerManagement> {
        ServerManagementScreen(
            onAddServer = { navigator.navigate(Route.AddServer) },
            onBack = { navigator.goBack() },
            onServerSwitched = { navigator.goBack() },
        )
    }

    entry<Route.UserManagement> {
        UserManagementScreen(
            onBack = { navigator.goBack() },
            onAddUser = { navigator.navigate(Route.AddServer) },
        )
    }

    entry<Route.SeerrSettings> {
        SeerrSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }
}
