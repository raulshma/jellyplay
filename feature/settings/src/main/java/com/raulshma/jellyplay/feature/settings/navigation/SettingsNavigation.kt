package com.raulshma.jellyplay.feature.settings.navigation

import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.AboutScreen
import com.raulshma.jellyplay.feature.settings.ArrSettingsScreen
import com.raulshma.jellyplay.feature.settings.ImportPreviewScreen
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
import com.raulshma.jellyplay.feature.settings.PrivacyDataScreen
import com.raulshma.jellyplay.feature.settings.PlaybackSettingsScreen
import com.raulshma.jellyplay.feature.settings.SeerrSettingsScreen
import com.raulshma.jellyplay.feature.settings.SecuritySettingsScreen
import com.raulshma.jellyplay.feature.settings.ServerManagementScreen
import com.raulshma.jellyplay.feature.settings.SettingsNavActions
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
        // Build the facade once per (navigator, host actions) lifetime so the
        // SettingsScreen subtree sees a single stable SettingsNavActions
        // instance (treated as @Immutable by the Compose compiler) instead of
        // fresh lambda allocations on every recomposition. Every drill-in is
        // the same one-liner: navigate to the route the screen supplies (with
        // its highlight id already baked in).
        val navActions = remember(navigator, onLogout, onSetupWizard, onCheckForUpdates) {
            SettingsNavActions(
                onNavigate = { route -> navigator.navigate(route) },
                onLogout = { onLogout(false) },
                onSetupWizard = onSetupWizard,
                onCheckForUpdates = onCheckForUpdates,
            )
        }
        SettingsScreen(
            onBack = { navigator.goBack() },
            onLogout = onLogout,
            navActions = navActions,
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
        // Same navigator-backed facade as the main Settings entry, so the
        // Appearance drill-ins navigate through the same onNavigate seam.
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        AppearanceSettingsScreen(
            onBack = { navigator.goBack() },
            navActions = navActions,
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

    entry<Route.PrivacyData> {
        PrivacyDataScreen(
            onBack = { navigator.goBack() },
            onLogout = onLogout,
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
            onImportPreview = { uri -> navigator.navigate(Route.ImportPreview(uri)) },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.FactoryReset> { entry ->
        FactoryResetScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.ImportPreview> { entry ->
        ImportPreviewScreen(
            onBack = { navigator.goBack() },
            uri = entry.uri,
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
