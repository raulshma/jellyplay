package com.raulshma.jellyplay.feature.settings.navigation

import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.AboutScreen
import com.raulshma.jellyplay.feature.settings.ArrSettingsScreen
import com.raulshma.jellyplay.feature.settings.SubtitleProviderSettingsScreen
import com.raulshma.jellyplay.feature.settings.AppearanceSettingsScreen
import com.raulshma.jellyplay.feature.settings.AudioSettingsScreen
import com.raulshma.jellyplay.feature.settings.BackupSettingsScreen
import com.raulshma.jellyplay.feature.settings.ExperimentalSettingsScreen
import com.raulshma.jellyplay.feature.settings.FactoryResetScreen
import com.raulshma.jellyplay.feature.settings.IntegrationsScreen
import com.raulshma.jellyplay.feature.settings.LanguageSettingsScreen
import com.raulshma.jellyplay.feature.settings.LicensesScreen
import com.raulshma.jellyplay.feature.settings.HomeLayoutPresetsScreen
import com.raulshma.jellyplay.feature.settings.LibraryHomeSectionsScreen
import com.raulshma.jellyplay.feature.settings.NotificationSettingsScreen
import com.raulshma.jellyplay.feature.settings.PinnedHomeSectionsScreen
import com.raulshma.jellyplay.feature.settings.PlaybackSettingsScreen
import com.raulshma.jellyplay.feature.settings.SeerrSettingsScreen
import com.raulshma.jellyplay.feature.settings.SecuritySettingsScreen
import com.raulshma.jellyplay.feature.settings.ServerManagementScreen
import com.raulshma.jellyplay.feature.settings.SettingsCallbacks
import com.raulshma.jellyplay.feature.settings.SettingsScreen
import com.raulshma.jellyplay.feature.settings.StorageSettingsScreen
import com.raulshma.jellyplay.feature.settings.UserManagementScreen

fun EntryProviderScope<NavKey>.settingsSection(
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    onSetupWizard: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
) {
    entry<Route.Settings> {
        // Build the callbacks once per (navigator, onLogout, onSetupWizard) lifetime so
        // the SettingsScreen subtree sees a single stable SettingsCallbacks instance
        // (treated as @Immutable by the Compose compiler) instead of fresh lambda
        // allocations on every recomposition.
        val callbacks = remember(navigator, onLogout, onSetupWizard) {
            SettingsCallbacks(
                onServerManagement = { id -> navigator.navigate(Route.ServerManagement(id)) },
                onUserManagement = { id -> navigator.navigate(Route.UserManagement(id)) },
                onSeerrSettings = { id -> navigator.navigate(Route.SeerrSettings(id)) },
                onArrSettings = { id -> navigator.navigate(Route.ArrSettings(id)) },
                onIntegrations = { id -> navigator.navigate(Route.Integrations(id)) },
                onAdminDashboard = { navigator.navigate(Route.AdminDashboard) },
                onSetupWizard = onSetupWizard,
                onNewsletterClick = { navigator.navigate(Route.Newsletter) },
                onFavoritesClick = { navigator.navigate(Route.Favorites) },
                onAboutClick = { navigator.navigate(Route.About) },
                onWatchProgressHeatmapClick = { navigator.navigate(Route.WatchProgressHeatmap) },
                onActivityQueueClick = { navigator.navigate(Route.ArrQueue) },
                onUpcomingClick = { navigator.navigate(Route.UpcomingCalendar) },
                onRequestsClick = { navigator.navigate(Route.Requests) },
                onAppearanceSettings = { id -> navigator.navigate(Route.AppearanceSettings(id)) },
                onPinnedHomeSections = { id -> navigator.navigate(Route.PinnedHomeSections(id)) },
                onHomeLayoutPresets = { id -> navigator.navigate(Route.HomeLayoutPresets(id)) },
                onConfigureLibraries = { id -> navigator.navigate(Route.LibraryHomeSections(id)) },
                onPlaybackSettings = { id -> navigator.navigate(Route.PlaybackSettings(id)) },
                onAudioSettings = { id -> navigator.navigate(Route.AudioSettings(id)) },
                onLanguageSettings = { id -> navigator.navigate(Route.LanguageSettings(id)) },
                onNotificationSettings = { id -> navigator.navigate(Route.NotificationSettings(id)) },
                onStorageSettings = { id -> navigator.navigate(Route.StorageSettings(id)) },
                onSecuritySettings = { id -> navigator.navigate(Route.SecuritySettings(id)) },
                onBackupSettings = { id -> navigator.navigate(Route.BackupSettings(id)) },
                onExperimentalSettings = { id -> navigator.navigate(Route.ExperimentalSettings(id)) },
                onOpenSubtitleTester = { navigator.navigate(Route.SubtitleTester) },
            )
        }
        SettingsScreen(
            onBack = { navigator.goBack() },
            onLogout = onLogout,
            callbacks = callbacks,
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
            onConfigureLibraries = { id -> navigator.navigate(Route.LibraryHomeSections(id)) },
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

    entry<Route.LibraryHomeSections> { entry ->
        LibraryHomeSectionsScreen(
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
            onOpenSubtitleTester = { navigator.navigate(Route.SubtitleTester) },
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
            onFactoryReset = { navigator.navigate(Route.FactoryReset()) },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.FactoryReset> { entry ->
        FactoryResetScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.ExperimentalSettings> { entry ->
        ExperimentalSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.Integrations> { entry ->
        IntegrationsScreen(
            onBack = { navigator.goBack() },
            onSeerrSettings = { navigator.navigate(Route.SeerrSettings()) },
            onArrSettings = { navigator.navigate(Route.ArrSettings()) },
            onSubtitleProviderSettings = { navigator.navigate(Route.SubtitleProviderSettings()) },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.ArrSettings> { entry ->
        ArrSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.SubtitleProviderSettings> { entry ->
        SubtitleProviderSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.About> {
        AboutScreen(
            onBack = { navigator.goBack() },
            onLicensesClick = { navigator.navigate(Route.Licenses) },
            onCheckForUpdates = onCheckForUpdates,
        )
    }

    entry<Route.Licenses> {
        LicensesScreen(
            onBack = { navigator.goBack() },
        )
    }
}
