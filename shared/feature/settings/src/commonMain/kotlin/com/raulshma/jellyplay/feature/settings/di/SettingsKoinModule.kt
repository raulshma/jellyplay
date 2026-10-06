package com.raulshma.jellyplay.feature.settings.di

import com.raulshma.jellyplay.feature.settings.AboutViewModel
import com.raulshma.jellyplay.feature.settings.AdvancedSettingsGate
import com.raulshma.jellyplay.feature.settings.AppearanceSettingsViewModel
import com.raulshma.jellyplay.feature.settings.HomeSettingsViewModel
import com.raulshma.jellyplay.feature.settings.ArrSettingsViewModel
import com.raulshma.jellyplay.feature.settings.AudioSettingsViewModel
import com.raulshma.jellyplay.feature.settings.ExperimentalSettingsViewModel
import com.raulshma.jellyplay.feature.settings.FactoryResetViewModel
import com.raulshma.jellyplay.feature.settings.ImportPreviewViewModel
import com.raulshma.jellyplay.feature.settings.LanguageSettingsViewModel
import com.raulshma.jellyplay.feature.settings.LicensesViewModel
import com.raulshma.jellyplay.feature.settings.DiscoverRowsViewModel
import com.raulshma.jellyplay.feature.settings.InputBindingsViewModel
import com.raulshma.jellyplay.feature.settings.JellyPlayMessagesViewModel
import com.raulshma.jellyplay.feature.settings.LibraryLayoutViewModel
import com.raulshma.jellyplay.feature.settings.NotificationSettingsViewModel
import com.raulshma.jellyplay.feature.settings.PlaybackSettingsViewModel
import com.raulshma.jellyplay.feature.settings.PrivacyDataViewModel
import com.raulshma.jellyplay.feature.settings.SecuritySettingsViewModel
import com.raulshma.jellyplay.feature.settings.SeerrSettingsViewModel
import com.raulshma.jellyplay.feature.settings.ServerManagementViewModel
import com.raulshma.jellyplay.feature.settings.ServerSettingsViewModel
import com.raulshma.jellyplay.feature.settings.SettingsSearchCatalog
import com.raulshma.jellyplay.feature.settings.SettingsSearchCatalogPrewarmer
import com.raulshma.jellyplay.feature.settings.SettingsViewModel
import com.raulshma.jellyplay.feature.settings.StorageSettingsViewModel
import com.raulshma.jellyplay.feature.settings.SubtitleProviderSettingsViewModel
import com.raulshma.jellyplay.feature.settings.WhatsNewViewModel
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the settings feature (docs/kmp-migration-plan.md).
 * The HiltViewModel/@Inject/@ApplicationContext
 * annotations were stripped at the move — Koin is the single constructor owner
 * (one framework per type). Ctor deps split three ways:
 *  - datastore projections/stores (PreferenceProjections, UserPreferencesStore,
 *    PreferencesEditor, AppearanceStore, HomeDiscoveryStore, ...) resolve from the
 *    C4 shared-datastore graph;
 *  - repository deps (AuthRepository/SeerrRepository/AdminRepository/MediaRepository/
 *    SearchHistoryRepository/...) resolve from shared :core:data — the Admin/
 *    Media-repository cluster impls flipped to Koin singles in dataJvmModule
 *, so these resolve on BOTH platforms;
 *  - the platform seams (SettingsBackupIo, AppLocaleSetter, StorageAreas,
 *    StorageMountsProvider, AppMetaProvider, LogCollector, AboutLibrariesJsonSource)
 *    resolve from the androidMain/jvmMain platform modules
 *    (androidSettingsPlatformModule / desktopSettingsPlatformModule).
 *
 * Android APP-SIDE-REQUIRED defs (the wrapped types live in the legacy
 * core:data/core:notification Android remainders and app-side scheduler
 * classes — this module's androidMain cannot see them): the composition root
 * must register these four before the corresponding screens open (resolution
 * is lazy, so boot stays safe):
 *  - [com.raulshma.jellyplay.feature.settings.AutoDownloadSync] (wraps the
 *    legacy AutoDownloadScheduler WorkManager sync),
 *  - [com.raulshma.jellyplay.feature.settings.NotificationSync] (wraps the
 *    legacy NotificationScheduler.scheduleOrUpdate),
 *  - [com.raulshma.jellyplay.feature.settings.WatchNextRefresher] (wraps the
 *    Android TV Watch Next scheduler),
 *  - [com.raulshma.jellyplay.feature.settings.AudioCacheClearer] (wraps the
 *    legacy AudioStreamCache).
 * The desktop actuals of all four live in desktopSettingsPlatformModule.
 */
val settingsModule: Module = module {
    includes(platformSettingsModule())

    // The catalog object is the single SettingsSearchProvider implementation
    // (this module's own SettingsScreen uses direct object access; shared
    // consumers like feature/home resolve it from their own Koin module
    // graphs — the interim app-side SettingsSearchInteropModule Hilt bridge
    // died with the HomeViewModel flip).
    single<SettingsSearchProvider> { SettingsSearchCatalog }

    // One shared instance of the stateless advanced-settings gate (read flow
    // + write command over the shared AppearanceStore) instead of a per-VM
    // rebuild in every settings ViewModel.
    single { AdvancedSettingsGate(get(), get()) }

    // Eager at startKoin so the settings string table is warm on a background
    // dispatcher LONG before the settings screen's first composition can block
    // on cold per-entry reads (the mechanism behind the settings-open ANR —
    // see [SettingsSearchCatalogPrewarmer]).
    single(createdAtStart = true) {
        SettingsSearchCatalogPrewarmer(get(DatastoreQualifiers.applicationScope))
            .apply { warm() }
    }

    viewModel {
        SettingsViewModel(
            settingsBackupIo = get(),
            preferencesStore = get(),
            projections = get(),
            authRepository = get(),
            seerrRepository = get(),
            serverAdminActions = get(),
            editor = get(),
            recentsStore = get(),
            jellyPlayStatusStore = get(),
            jellyPlaySyncRepository = get(),
            jellyPlayEventsRepository = get(),
        )
    }

    // The companion-plugin inbox screen (ADR 0010). Reachability is gated at
    // the settings root's "Messages" entry; the VM still re-checks the
    // `messages` feature key before every api call.
    viewModel {
        JellyPlayMessagesViewModel(
            eventsRepository = get(),
            statusStore = get(),
        )
    }
    viewModel {
        AppearanceSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
        )
    }
    viewModel {
        HomeSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
        )
    }
    viewModel {
        LanguageSettingsViewModel(
            appLocaleSetter = get(),
            projections = get(),
            advancedSettings = get(),
            editor = get(),
        )
    }
    viewModel {
        PlaybackSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
            watchNextRefresher = get(),
            // mpv audio-device enumeration: desktop-only — Android
            // binds no enumerator and the row is capability-hidden there.
            audioDeviceEnumerator = getOrNull(),
        )
    }
    viewModel {
        InputBindingsViewModel(
            projections = get(),
            editor = get(),
        )
    }
    viewModel {
        AudioSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
            audioCacheClearer = get(),
        )
    }
    viewModel {
        ExperimentalSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
        )
    }
    viewModel {
        FactoryResetViewModel(
            snapshotReader = get(),
            editor = get(),
        )
    }
    viewModel {
        ImportPreviewViewModel(
            settingsBackupIo = get(),
            userPreferencesStore = get(),
            // The live diff snapshot rides the factory-reset review's seam —
            // the snapshot reader over the PreferenceStores bundle — instead
            // of enumerating the stores here, so a new slice extends the
            // bundle, not this definition.
            snapshotReader = get(),
        )
    }
    // ──: storage / privacy / server / security / integrations / about ──
    viewModel {
        StorageSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
            autoDownloadSync = get(),
            autoDownloadCleanup = get(),
            storageAreas = get(),
            storageMountsProvider = get(),
            authRepository = get(),
        )
    }
    viewModel {
        PrivacyDataViewModel(
            editor = get(),
            serverIdentityStore = get(),
            searchHistoryRepository = get(),
            storageAreas = get(),
        )
    }
    viewModel {
        ServerManagementViewModel(
            authRepository = get(),
            serverIdentityStore = get(),
            // Per-server self-signed trust toggle state + grants (network
            // DataStore, same store the OkHttp config StateFlow flows from).
            networkOfflineStore = get(),
            // The trust-toggle DECISION seam (core:data) — grants still
            // observed/written through the store above; matching happens
            // behind this repository so no core:network type reaches here.
            selfSignedTrustRepository = get(),
            // the app-level client certificate (mTLS) import /
            // toggle / remove seam (core:data) — delegates to the same
            // manager single the handshake layer (applyTls) reads.
            clientCertificate = get(),
        )
    }
    viewModel {
        ServerSettingsViewModel(
            authRepository = get(),
        )
    }
    viewModel {
        SecuritySettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
            authRepository = get(),
        )
    }
    viewModel {
        SeerrSettingsViewModel(
            seerrAuthenticator = get(),
            seerrPreferencesStore = get(),
            secureCredentialsStore = get(),
            // The "via server" bridge seams (ADR 0010): the plugin api client
            // (seerr status/login/logout), the ONE availability gate, and the
            // Jellyfin Quick Connect source the plugin authorizes against.
            pluginApiClient = get(),
            pluginStatusStore = get(),
            authRepository = get(),
        )
    }
    viewModel {
        ArrSettingsViewModel(
            arrRepository = get(),
            arrPreferencesStore = get(),
            secureCredentialsStore = get(),
        )
    }
    viewModel {
        SubtitleProviderSettingsViewModel(
            preferencesStore = get(),
            subtitleProviderRepository = get(),
        )
    }
    viewModel {
        AboutViewModel(
            appMetaProvider = get(),
            logCollector = get(),
            serverAdminActions = get(),
            authRepository = get(),
            experimentalStore = get(),
        )
    }
    viewModel {
        LicensesViewModel(
            jsonSource = get(),
        )
    }
    viewModel {
        WhatsNewViewModel(
            whatsNewRepository = get(),
        )
    }
    // ──  final slice: home-layout cluster + notifications ──
    viewModel {
        LibraryLayoutViewModel(
            homeDiscoveryStore = get(),
            editor = get(),
            mediaRepository = get(),
            mediaCollectionReads = get(),
            playlistRepository = get(),
        )
    }
    viewModel {
        DiscoverRowsViewModel(
            homeDiscoveryStore = get(),
            editor = get(),
            mediaRepository = get(),
            homeFeed = get(),
            mediaBrowseReads = get(),
        )
    }
    viewModel {
        NotificationSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
            mediaRepository = get(),
            notificationSync = get(),
        )
    }
}
