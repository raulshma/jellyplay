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
import com.raulshma.jellyplay.feature.settings.HomeLayoutPresetsScreen
import com.raulshma.jellyplay.feature.settings.NotificationSettingsScreen
import com.raulshma.jellyplay.feature.settings.PinnedHomeSectionsScreen
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
            onServerManagement = { id -> navigator.navigate(Route.ServerManagement(id)) },
            onUserManagement = { id -> navigator.navigate(Route.UserManagement(id)) },
            onSeerrSettings = { id -> navigator.navigate(Route.SeerrSettings(id)) },
            onAdminDashboard = { navigator.navigate(Route.AdminDashboard) },
            onSetupWizard = onSetupWizard,
            onNewsletterClick = { navigator.navigate(Route.Newsletter) },
            onFavoritesClick = { navigator.navigate(Route.Favorites) },
            onAboutClick = { navigator.navigate(Route.About) },
            onWatchProgressHeatmapClick = { navigator.navigate(Route.WatchProgressHeatmap) },
            onAppearanceSettings = { id -> navigator.navigate(Route.AppearanceSettings(id)) },
            onPlaybackSettings = { id -> navigator.navigate(Route.PlaybackSettings(id)) },
            onAudioSettings = { id -> navigator.navigate(Route.AudioSettings(id)) },
            onLanguageSettings = { id -> navigator.navigate(Route.LanguageSettings(id)) },
            onNotificationSettings = { id -> navigator.navigate(Route.NotificationSettings(id)) },
            onStorageSettings = { id -> navigator.navigate(Route.StorageSettings(id)) },
            onSecuritySettings = { id -> navigator.navigate(Route.SecuritySettings(id)) },
            onBackupSettings = { id -> navigator.navigate(Route.BackupSettings(id)) },
        )
    }

    entry<Route.ServerManagement> { entry ->
        ServerManagementScreen(
            onAddServer = { navigator.navigate(Route.AddServer) },
            onBack = { navigator.goBack() },
            onServerSwitched = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.UserManagement> { entry ->
        UserManagementScreen(
            onBack = { navigator.goBack() },
            onAddUser = { navigator.navigate(Route.ServerList) },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.SeerrSettings> { entry ->
        SeerrSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.AppearanceSettings> { entry ->
        AppearanceSettingsScreen(
            onBack = { navigator.goBack() },
            onPinnedHomeSections = { id -> navigator.navigate(Route.PinnedHomeSections(id)) },
            onHomeLayoutPresets = { id -> navigator.navigate(Route.HomeLayoutPresets(id)) },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.PinnedHomeSections> { entry ->
        PinnedHomeSectionsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.HomeLayoutPresets> { entry ->
        HomeLayoutPresetsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.PlaybackSettings> { entry ->
        PlaybackSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.AudioSettings> { entry ->
        AudioSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.LanguageSettings> { entry ->
        LanguageSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.NotificationSettings> { entry ->
        NotificationSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.StorageSettings> { entry ->
        StorageSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.SecuritySettings> { entry ->
        SecuritySettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.BackupSettings> { entry ->
        BackupSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
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
