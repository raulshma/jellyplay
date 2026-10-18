package com.raulshma.jellyplay.feature.settings.di

import com.raulshma.jellyplay.feature.settings.AboutViewModel
import com.raulshma.jellyplay.feature.settings.AdvancedSettingsGate
import com.raulshma.jellyplay.feature.settings.AppearanceSettingsViewModel
import com.raulshma.jellyplay.feature.settings.HomeSettingsViewModel
import com.raulshma.jellyplay.feature.settings.ArrSettingsViewModel
import com.raulshma.jellyplay.feature.settings.AudioSettingsViewModel
import com.raulshma.jellyplay.feature.settings.ExperimentalSettingsViewModel
import com.raulshma.jellyplay.feature.settings.FactoryResetViewModel
import com.raulshma.jellyplay.feature.settings.RestoreWizardViewModel
import com.raulshma.jellyplay.feature.settings.LanguageSettingsViewModel
import com.raulshma.jellyplay.feature.settings.LicensesViewModel
import com.raulshma.jellyplay.feature.settings.DiscoverRowsViewModel
import com.raulshma.jellyplay.feature.settings.InputBindingsViewModel
import com.raulshma.jellyplay.feature.settings.JellyPlayMessagesViewModel
import com.raulshma.jellyplay.feature.settings.JellyPlaySyncViewModel
import com.raulshma.jellyplay.feature.settings.JellyPlayUserRatingsViewModel
import com.raulshma.jellyplay.feature.settings.JellyPlayYourWatchingViewModel
import com.raulshma.jellyplay.feature.settings.LibraryLayoutViewModel
import com.raulshma.jellyplay.feature.settings.NotificationSettingsViewModel
import com.raulshma.jellyplay.feature.settings.PlaybackSettingsViewModel
import com.raulshma.jellyplay.feature.settings.PrivacyDataViewModel
import com.raulshma.jellyplay.feature.settings.SecretsBackupAssembler
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
import org.koin.compose.viewmodel.dsl.viewModelOf
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

    // The Wave-3 secrets-block gather/apply seam, shared by the export
    // (SettingsViewModel) and the restore wizard's file arm. Resolves
    // the three secure credential stores (datastore platform modules) plus
    // core:data's ServerBackupMetadataStore (the token-free server-list seam).
    single {
        SecretsBackupAssembler(
            arrStore = get(),
            seerrStore = get(),
            subtitleStore = get(),
            serverMetadataStore = get(),
        )
    }

    viewModelOf(::SettingsViewModel)

    // The companion-plugin inbox screen (ADR 0010). Reachability is gated at
    // the settings root's "Messages" entry; the VM still re-checks the
    // `messages` gate (probe AND the user's toggle) before every api call.
    viewModelOf(::JellyPlayMessagesViewModel)

    // The companion-plugin "My ratings" screen (ADR 0010). Reachability is
    // gated at the settings root's entry; the VM still re-checks the
    // `user-ratings` gate (probe AND the user's toggle) before every api call.
    viewModelOf(::JellyPlayUserRatingsViewModel)

    // The companion-plugin "Your watching" screen. Reachability is gated at
    // the settings root's entry; the VM still re-checks the `analytics` gate
    // (probe AND the user's toggle — the per-user analytics face, not the
    // admin route) before every api call.
    viewModelOf(::JellyPlayYourWatchingViewModel)

    // The companion-plugin settings-sync screen (ADR 0010). Reachability is
    // gated at the settings root's "Sync" entry (probe AND the `settings-sync`
    // meta key — no user toggle exists for meta keys); the VM still re-checks
    // the same probe-only gate before every api call.
    viewModelOf(::JellyPlaySyncViewModel)

    viewModelOf(::AppearanceSettingsViewModel)

    // The H20 home-layout DisplayPreferences bridge (core:data) — the manual
    // Upload/Restore actions + the autopush engine.
    viewModelOf(::HomeSettingsViewModel)

    viewModelOf(::LanguageSettingsViewModel)

    // Hand-written, not viewModelOf: the nullable audio-device seam resolves
    // with getOrNull (the ctor KDoc owns the rationale), which viewModelOf's
    // per-param hard get() cannot express.
    viewModel {
        PlaybackSettingsViewModel(
            projections = get(),
            advancedSettings = get(),
            editor = get(),
            watchNextRefresher = get(),
            audioDeviceEnumerator = getOrNull(),
        )
    }

    viewModelOf(::InputBindingsViewModel)

    viewModelOf(::AudioSettingsViewModel)

    viewModelOf(::ExperimentalSettingsViewModel)

    // Hand-written, not viewModelOf: diffLabelResolver is a function-typed
    // ctor default (::resolveDiffLabels) with no Koin binding — viewModelOf
    // resolves every ctor param with a hard get() and would crash here.
    viewModel {
        FactoryResetViewModel(
            snapshotReader = get(),
            editor = get(),
            // Wave 6: the pre-reset safety restore point's api + gate seams.
            pluginApiClient = get(),
            statusStore = get(),
        )
    }

    // The unified restore wizard (Wave 5): ONE flow for restoring settings
    // regardless of source. Replaces the retired ImportPreviewViewModel (the
    // file arm ports its parser/diff/restore logic verbatim over the same
    // seams) and absorbs the sync screen's import + one-click snapshot
    // restore rows.
    // Hand-written, not viewModelOf: diffLabelResolver/textResolver are
    // function-typed ctor defaults with no Koin binding — viewModelOf
    // resolves every ctor param with a hard get() and would crash here.
    viewModel {
        RestoreWizardViewModel(
            settingsBackupIo = get(),
            userPreferencesStore = get(),
            // The live diff snapshot rides the factory-reset review's seam —
            // the snapshot reader over the PreferenceStores bundle — instead
            // of enumerating the stores here, so a new slice extends the
            // bundle, not this definition.
            snapshotReader = get(),
            secretsBackupAssembler = get(),
            // The server arm: restore points, current-state diff, apply batch,
            // plus the Wave-6 pre-apply safety capture.
            pluginApiClient = get(),
            statusStore = get(),
            syncRepository = get(),
            serverIdentityStore = get(),
            messageBus = get(),
        )
    }
    // ──: storage / privacy / server / security / integrations / about ──
    viewModelOf(::StorageSettingsViewModel)

    viewModelOf(::PrivacyDataViewModel)

    // The per-server self-signed trust toggle state + grants (network
    // DataStore, same store the OkHttp config StateFlow flows from); the
    // trust-toggle DECISION seam (core:data) — grants still observed/written
    // through that store above, matching happens behind this repository so no
    // core:network type reaches here; and the app-level client certificate
    // (mTLS) import / toggle / remove seam (core:data) — delegates to the
    // same manager single the handshake layer (applyTls) reads.
    viewModelOf(::ServerManagementViewModel)

    viewModelOf(::ServerSettingsViewModel)

    viewModelOf(::SecuritySettingsViewModel)

    viewModelOf(::SeerrSettingsViewModel)

    viewModelOf(::ArrSettingsViewModel)

    viewModelOf(::SubtitleProviderSettingsViewModel)

    viewModelOf(::AboutViewModel)

    viewModelOf(::LicensesViewModel)

    viewModelOf(::WhatsNewViewModel)

    // ──  final slice: home-layout cluster + notifications ──
    viewModelOf(::LibraryLayoutViewModel)

    viewModelOf(::DiscoverRowsViewModel)

    viewModelOf(::NotificationSettingsViewModel)
}
