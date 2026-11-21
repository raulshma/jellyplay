package com.raulshma.jellyplay.feature.settings.navigation

import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.AboutScreen
import com.raulshma.jellyplay.feature.settings.AppearanceRows
import com.raulshma.jellyplay.feature.settings.ArrSettingsScreen
import com.raulshma.jellyplay.feature.settings.RestoreWizardScreen
import com.raulshma.jellyplay.feature.settings.InputBindingsScreen
import com.raulshma.jellyplay.feature.settings.SubtitleProviderSettingsScreen
import com.raulshma.jellyplay.feature.settings.AmbientSettingsScreen
import com.raulshma.jellyplay.feature.settings.AppearanceSettingsScreen
import com.raulshma.jellyplay.feature.settings.ThemeSettingsScreen
import com.raulshma.jellyplay.feature.settings.LibraryDisplaySettingsScreen
import com.raulshma.jellyplay.feature.settings.DetailDisplaySettingsScreen
import com.raulshma.jellyplay.feature.settings.NavigationDisplaySettingsScreen
import com.raulshma.jellyplay.feature.settings.AudioSettingsScreen
import com.raulshma.jellyplay.feature.settings.BackupSettingsScreen
import com.raulshma.jellyplay.feature.settings.ExperimentalSettingsScreen
import com.raulshma.jellyplay.feature.settings.FactoryResetScreen
import com.raulshma.jellyplay.feature.settings.IntegrationsScreen
import com.raulshma.jellyplay.feature.settings.LanguageSettingsScreen
import com.raulshma.jellyplay.feature.settings.LicensesScreen
import com.raulshma.jellyplay.feature.settings.HomeLayoutPresetsScreen
import com.raulshma.jellyplay.feature.settings.HomeSettingsScreen
import com.raulshma.jellyplay.feature.settings.HomeDisplaySettingsScreen
import com.raulshma.jellyplay.feature.settings.HomeRowsSettingsScreen
import com.raulshma.jellyplay.feature.settings.HomeCardsSettingsScreen
import com.raulshma.jellyplay.feature.settings.DownloadsSettingsScreen
import com.raulshma.jellyplay.feature.settings.DiscoverRowsScreen
import com.raulshma.jellyplay.feature.settings.DiscoverRowEditorScreen
import com.raulshma.jellyplay.feature.settings.LibraryHomeSectionsScreen
import com.raulshma.jellyplay.feature.settings.NotificationSettingsScreen
import com.raulshma.jellyplay.feature.settings.PinnedHomeSectionsScreen
import com.raulshma.jellyplay.feature.settings.PrivacyDataScreen
import com.raulshma.jellyplay.feature.settings.PlaybackSettingsScreen
import com.raulshma.jellyplay.feature.settings.PlaybackBasicsScreen
import com.raulshma.jellyplay.feature.settings.PlaybackControlsScreen
import com.raulshma.jellyplay.feature.settings.PlayerChromeSettingsScreen
import com.raulshma.jellyplay.feature.settings.PlaybackVideoQualityScreen
import com.raulshma.jellyplay.feature.settings.PlayerEngineSettingsScreen
import com.raulshma.jellyplay.feature.settings.MediaSegmentSettingsScreen
import com.raulshma.jellyplay.feature.settings.SyncPlayCastingSettingsScreen
import com.raulshma.jellyplay.feature.settings.LiveTvDvrSettingsScreen
import com.raulshma.jellyplay.feature.settings.PerformanceSettingsScreen
import com.raulshma.jellyplay.feature.settings.SeerrSettingsScreen
import com.raulshma.jellyplay.feature.settings.SecuritySettingsScreen
import com.raulshma.jellyplay.feature.settings.ServerManagementScreen
import com.raulshma.jellyplay.feature.settings.SettingsNavActions
import com.raulshma.jellyplay.feature.settings.JellyPlayMessagesScreen
import com.raulshma.jellyplay.feature.settings.JellyPlayPluginSettingsScreen
import com.raulshma.jellyplay.feature.settings.JellyPlaySyncScreen
import com.raulshma.jellyplay.feature.settings.JellyPlayUserRatingsScreen
import com.raulshma.jellyplay.feature.settings.JellyPlayYourWatchingScreen
import com.raulshma.jellyplay.feature.settings.SettingsScreen
import com.raulshma.jellyplay.feature.settings.StorageSettingsScreen
import com.raulshma.jellyplay.feature.settings.UserManagementScreen
import com.raulshma.jellyplay.feature.settings.WhatsNewScreen

fun EntryProviderScope<NavKey>.settingsSection(
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    onSetupWizard: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    selfUpdateSupported: Boolean = true,
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
        // Same navigator-backed facade as the Settings/Playback entries, so
        // the appearance hub's four entrances navigate through the one
        // onNavigate seam.
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        AppearanceSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
            navActions = navActions,
        )
    }

    // The appearance domain's four leaves (ADR-0017): the hub's entrances and
    // the re-pointed spec route kinds deep-link here; each leaf shares the
    // appearance ViewModel and carries exactly the advanced tier its rows do.
    entry<Route.ThemeSettings> { entry ->
        ThemeSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
            // The Performance group's "Customize…" row: the master dial lives
            // in the hub — the appearance-side quick picker deep-links there,
            // highlighting the master row.
            onOpenPerformanceHub = {
                navigator.navigate(Route.PerformanceSettings(highlightSettingId = "performance_mode"))
            },
        )
    }

    entry<Route.LibraryDisplaySettings> { entry ->
        LibraryDisplaySettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.DetailDisplaySettings> { entry ->
        DetailDisplaySettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.NavigationDisplaySettings> { entry ->
        NavigationDisplaySettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
            // The shortcuts editor opens on the ⋮ menu tab (the Home Screen
            // hub's header-shortcuts row opens the same editor on the header
            // tab).
            onCustomizeShortcuts = {
                navigator.navigate(Route.ShortcutsCustomize(com.raulshma.jellyplay.core.model.ShortcutSurface.OVERFLOW))
            },
            // The master haptics switch lives on the Library views leaf — the
            // Navigation & haptics group's link row lands there with the
            // switch highlighted (no duplicate setting).
            onOpenHapticsSwitch = {
                navigator.navigate(Route.LibraryDisplaySettings(AppearanceRows.HapticsEnabled.id))
            },
        )
    }

    entry<Route.HomeSettings> { entry ->
        // Same navigator-backed facade as the main Settings entry, so the
        // Home hub's entrances navigate through the same onNavigate seam.
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        HomeSettingsScreen(
            onBack = { navigator.goBack() },
            navActions = navActions,
            highlightSettingId = entry.highlightSettingId,
        )
    }

    // The home domain's three leaves (ADR-0017): the hub's entrances and the
    // re-pointed spec route kinds deep-link here; each leaf shares the home
    // ViewModel. The Rows & layout leaf carries the facade for its drill-ins
    // (pinned sections, presets, libraries, discover rows, the header
    // shortcuts editor).
    entry<Route.HomeDisplaySettings> { entry ->
        HomeDisplaySettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.HomeRowsSettings> { entry ->
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        HomeRowsSettingsScreen(
            onBack = { navigator.goBack() },
            navActions = navActions,
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.HomeCardsSettings> { entry ->
        HomeCardsSettingsScreen(
            onBack = { navigator.goBack() },
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

    entry<Route.DiscoverRows> { entry ->
        DiscoverRowsScreen(
            onBack = { navigator.goBack() },
            onEditRow = { rowId -> navigator.navigate(Route.DiscoverRowEditor(rowId)) },
            onAddRow = { navigator.navigate(Route.DiscoverRowEditor()) },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.DiscoverRowEditor> { entry ->
        DiscoverRowEditorScreen(
            onBack = { navigator.goBack() },
            rowId = entry.rowId,
        )
    }

    entry<Route.PlaybackSettings> { entry ->
        // Same navigator-backed facade as the Settings/Home entries, so the
        // playback hub's entrances (and the controls leaf's input-binding
        // drill-in) navigate through the one onNavigate seam instead of a
        // per-callback field.
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        PlaybackSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
            navActions = navActions,
        )
    }

    // The playback domain's eight leaves (ADR-0017): the hub's entrances and
    // the re-pointed spec route kinds deep-link here; each leaf shares the
    // playback ViewModel and carries exactly the advanced tier its rows do.
    entry<Route.PlaybackBasics> { entry ->
        PlaybackBasicsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.PlaybackControls> { entry ->
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        PlaybackControlsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
            navActions = navActions,
        )
    }

    entry<Route.PlayerChromeSettings> { entry ->
        PlayerChromeSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.PlaybackVideoQuality> { entry ->
        PlaybackVideoQualityScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.PlayerEngineSettings> { entry ->
        PlayerEngineSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.MediaSegmentSettings> { entry ->
        MediaSegmentSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.SyncPlayCastingSettings> { entry ->
        SyncPlayCastingSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.LiveTvDvrSettings> { entry ->
        LiveTvDvrSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.PerformanceSettings> { entry ->
        // The Performance hub's cross-reference rows (the device-profile
        // Re-detect row, the Exo-engine back-buffer row) drill into the
        // playback screen through the same navigator-backed facade.
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        PerformanceSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
            navActions = navActions,
        )
    }

    entry<Route.InputBindings> {
        InputBindingsScreen(
            onBack = { navigator.goBack() },
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

    // The Downloads sibling screen (ADR-0017's storage split): the root's
    // Downloads entrance and the re-pointed residual row routes land here.
    entry<Route.DownloadsSettings> { entry ->
        DownloadsSettingsScreen(
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
            onRestoreWizard = { uri -> navigator.navigate(Route.RestoreWizard(uri = uri)) },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.FactoryReset> { entry ->
        FactoryResetScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.RestoreWizard> { entry ->
        RestoreWizardScreen(
            onBack = { navigator.goBack() },
            uri = entry.uri,
            snapshotId = entry.snapshotId,
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

    // The Ambient screen: TV dream + desktop idle-ambient as ONE gated
    // screen (ADR-0017); each group composes only where its gate opens.
    entry<Route.AmbientSettings> { entry ->
        AmbientSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    // The plugin screen: the root's former inline plugin section (ADR-0017);
    // the probe gate lives in the screen body (graceful-absent).
    entry<Route.JellyPlayPluginSettings> { entry ->
        val navActions = remember(navigator) {
            SettingsNavActions(onNavigate = { route -> navigator.navigate(route) })
        }
        JellyPlayPluginSettingsScreen(
            onBack = { navigator.goBack() },
            navActions = navActions,
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.SubtitleProviderSettings> { entry ->
        SubtitleProviderSettingsScreen(
            onBack = { navigator.goBack() },
            highlightSettingId = entry.highlightSettingId,
        )
    }

    entry<Route.JellyPlayMessages> {
        JellyPlayMessagesScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.JellyPlaySync> {
        JellyPlaySyncScreen(
            onBack = { navigator.goBack() },
            onRestoreWizard = { navigator.navigate(Route.RestoreWizard()) },
            onRestoreSnapshot = { id -> navigator.navigate(Route.RestoreWizard(snapshotId = id)) },
        )
    }

    entry<Route.JellyPlayUserRatings> {
        JellyPlayUserRatingsScreen(
            onBack = { navigator.goBack() },
            onOpenItem = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
        )
    }

    entry<Route.JellyPlayYourWatching> {
        JellyPlayYourWatchingScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.About> {
        AboutScreen(
            onBack = { navigator.goBack() },
            onLicensesClick = { navigator.navigate(Route.Licenses) },
            onCheckForUpdates = onCheckForUpdates,
            // False on fdroid: the update rows (auto-check / auto-download
            // toggles, manual check, dismiss period) are hidden — updates come
            // from the F-Droid client there.
            selfUpdateSupported = selfUpdateSupported,
        )
    }

    entry<Route.Licenses> {
        LicensesScreen(
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.WhatsNew> {
        WhatsNewScreen(
            onBack = { navigator.goBack() },
            onNavigate = { route -> navigator.navigate(route) },
        )
    }
}
