package com.raulshma.jellyplay.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.AboutScreen
import com.raulshma.jellyplay.feature.settings.LicensesScreen
import com.raulshma.jellyplay.feature.settings.SeerrSettingsScreen
import com.raulshma.jellyplay.feature.settings.ServerManagementScreen
import com.raulshma.jellyplay.feature.settings.SettingsScreen
import com.raulshma.jellyplay.feature.settings.UserManagementScreen

fun EntryProviderScope<NavKey>.settingsSection(
    navigator: Navigator,
    onLogout: () -> Unit,
    onSetupWizard: () -> Unit = {},
) {
    entry<Route.Settings> {
        SettingsScreen(
            onBack = { navigator.goBack() },
            onLogout = onLogout,
            onServerManagement = { navigator.navigate(Route.ServerManagement) },
            onUserManagement = { navigator.navigate(Route.UserManagement) },
            onSeerrSettings = { navigator.navigate(Route.SeerrSettings) },
            onAdminDashboard = { navigator.navigate(Route.AdminDashboard) },
            onSetupWizard = onSetupWizard,
            onNewsletterClick = { navigator.navigate(Route.Newsletter) },
            onFavoritesClick = { navigator.navigate(Route.Favorites) },
            onAboutClick = { navigator.navigate(Route.About) },
            onWatchProgressHeatmapClick = { navigator.navigate(Route.WatchProgressHeatmap) },
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
            onAddUser = { navigator.navigate(Route.ServerList) },
        )
    }

    entry<Route.SeerrSettings> {
        SeerrSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.About> {
        AboutScreen(
            onBack = { navigator.goBack() },
            onLicensesClick = { navigator.navigate(Route.Licenses) },
        )
    }

    entry<Route.Licenses> {
        LicensesScreen(
            onBack = { navigator.goBack() },
        )
    }
}
