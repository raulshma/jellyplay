package com.raulshma.jellyplay.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.AboutScreen
import com.raulshma.jellyplay.feature.settings.AppearanceSettingsScreen
import com.raulshma.jellyplay.feature.settings.AudioSettingsScreen
import com.raulshma.jellyplay.feature.settings.BackupSettingsScreen
import com.raulshma.jellyplay.feature.settings.LanguageSettingsScreen
import com.raulshma.jellyplay.feature.settings.LicensesScreen
import com.raulshma.jellyplay.feature.settings.NotificationSettingsScreen
import com.raulshma.jellyplay.feature.settings.PlaybackSettingsScreen
import com.raulshma.jellyplay.feature.settings.SeerrSettingsScreen
import com.raulshma.jellyplay.feature.settings.SecuritySettingsScreen
import com.raulshma.jellyplay.feature.settings.ServerManagementScreen
import com.raulshma.jellyplay.feature.settings.SettingsScreen
import com.raulshma.jellyplay.feature.settings.StorageSettingsScreen
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
            onAppearanceSettings = { navigator.navigate(Route.AppearanceSettings) },
            onPlaybackSettings = { navigator.navigate(Route.PlaybackSettings) },
            onAudioSettings = { navigator.navigate(Route.AudioSettings) },
            onLanguageSettings = { navigator.navigate(Route.LanguageSettings) },
            onNotificationSettings = { navigator.navigate(Route.NotificationSettings) },
            onStorageSettings = { navigator.navigate(Route.StorageSettings) },
            onSecuritySettings = { navigator.navigate(Route.SecuritySettings) },
            onBackupSettings = { navigator.navigate(Route.BackupSettings) },
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

    entry<Route.AppearanceSettings> {
        AppearanceSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.PlaybackSettings> {
        PlaybackSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.AudioSettings> {
        AudioSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.LanguageSettings> {
        LanguageSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.NotificationSettings> {
        NotificationSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.StorageSettings> {
        StorageSettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.SecuritySettings> {
        SecuritySettingsScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.BackupSettings> {
        BackupSettingsScreen(
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
