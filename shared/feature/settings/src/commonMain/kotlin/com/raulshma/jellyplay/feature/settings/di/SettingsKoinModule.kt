package com.raulshma.jellyplay.feature.settings.di

import com.raulshma.jellyplay.feature.settings.AboutViewModel
import com.raulshma.jellyplay.feature.settings.AppearanceSettingsViewModel
import com.raulshma.jellyplay.feature.settings.ArrSettingsViewModel
import com.raulshma.jellyplay.feature.settings.AudioSettingsViewModel
import com.raulshma.jellyplay.feature.settings.ExperimentalSettingsViewModel
import com.raulshma.jellyplay.feature.settings.FactoryResetViewModel
import com.raulshma.jellyplay.feature.settings.LanguageSettingsViewModel
import com.raulshma.jellyplay.feature.settings.LicensesViewModel
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
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the settings feature (docs/kmp-migration-plan.md
 * §Phase V3, settings conveyor Waves 1a+1b). The HiltViewModel/@Inject/@ApplicationContext
 * annotations were stripped at the move — Koin is the single constructor owner
 * (one framework per type). Ctor deps split three ways:
 *  - datastore projections/stores (PreferenceProjections, UserPreferencesStore,
 *    PreferencesEditor, AppearanceStore, HomeDiscoveryStore, ...) resolve from the
 *    C4 shared-datastore graph;
 *  - repository deps (AuthRepository/SeerrRepository/AdminRepository/MediaRepository/
 *    SearchHistoryRepository/...) resolve from shared :core:data — the Admin/
 *    Media-repository cluster impls flipped to Koin singles in dataJvmModule
 *    (wave wB + MediaRepository flip), so these resolve on BOTH platforms;
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
    // The catalog object is the single SettingsSearchProvider implementation
    // (this module's own SettingsScreen uses direct object access; shared
    // consumers like feature/home resolve it from their own Koin module
    // graphs — the interim app-side SettingsSearchInteropModule Hilt bridge
    // died with wave 6B's HomeViewModel flip).
    single<SettingsSearchProvider> { SettingsSearchCatalog }

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
            adminRepository = get(),
            editor = get(),
            recentsStore = get(),
        )
    }
    viewModel {
        AppearanceSettingsViewModel(
            store = get(),
            projections = get(),
            appearanceStore = get(),
            editor = get(),
        )
    }
    viewModel {
        LanguageSettingsViewModel(
            appLocaleSetter = get(),
            store = get(),
            projections = get(),
            appearanceStore = get(),
            editor = get(),
        )
    }
    viewModel {
        PlaybackSettingsViewModel(
            store = get(),
            projections = get(),
            appearanceStore = get(),
            editor = get(),
            watchNextRefresher = get(),
        )
    }
    viewModel {
        AudioSettingsViewModel(
            store = get(),
            projections = get(),
            appearanceStore = get(),
            editor = get(),
            audioCacheClearer = get(),
        )
    }
    viewModel {
        ExperimentalSettingsViewModel(
            store = get(),
            projections = get(),
            appearanceStore = get(),
            editor = get(),
        )
    }
    viewModel {
        FactoryResetViewModel(
            playbackStore = get(),
            appearanceStore = get(),
            videoPlayerStore = get(),
            downloadsStore = get(),
            engineStore = get(),
            homeDiscoveryStore = get(),
            audioStore = get(),
            audioEffectsStore = get(),
            audioCacheStore = get(),
            libraryStore = get(),
            navigationStore = get(),
            networkOfflineStore = get(),
            notificationStore = get(),
            screensaverStore = get(),
            securityStore = get(),
            subtitleLanguageStore = get(),
            syncPlayCastStore = get(),
            experimentalStore = get(),
            appRuntimeStateStore = get(),
            pinRateLimiter = get(),
            editor = get(),
        )
    }
    // ── Wave 1b: storage / privacy / server / security / integrations / about ──
    viewModel {
        StorageSettingsViewModel(
            projections = get(),
            appearanceStore = get(),
            editor = get(),
            autoDownloadSync = get(),
            storageAreas = get(),
            storageMountsProvider = get(),
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
        )
    }
    viewModel {
        ServerSettingsViewModel(
            authRepository = get(),
        )
    }
    viewModel {
        SecuritySettingsViewModel(
            store = get(),
            projections = get(),
            appearanceStore = get(),
            editor = get(),
            authRepository = get(),
        )
    }
    viewModel {
        SeerrSettingsViewModel(
            seerrRepository = get(),
            seerrPreferencesStore = get(),
            secureCredentialsStore = get(),
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
            adminRepository = get(),
            authRepository = get(),
            experimentalStore = get(),
        )
    }
    viewModel {
        LicensesViewModel(
            jsonSource = get(),
        )
    }
    // ── Wave 1b final slice: home-layout cluster + notifications ──
    viewModel {
        LibraryLayoutViewModel(
            homeDiscoveryStore = get(),
            editor = get(),
            mediaRepository = get(),
        )
    }
    viewModel {
        NotificationSettingsViewModel(
            store = get(),
            projections = get(),
            appearanceStore = get(),
            editor = get(),
            mediaRepository = get(),
            notificationSync = get(),
        )
    }
}
