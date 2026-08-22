package com.raulshma.jellyplay.core.datastore.di

import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin construction owner for every store in :shared:core:datastore
 * (docs/kmp-migration-plan.md §Phase C4). Construction bodies are copied
 * byte-for-byte from the legacy Android Hilt [SharedStoreModule] providers;
 * the Android shim's @Provides bodies now bridge here.
 *
 * Platform-bound definitions (the per-file DataStores and the
 * SecureKeyValueStorage-backed credential stores) live in
 * [androidDatastoreModule] / [desktopDatastoreModule].
 */
val datastoreCommonModule = module {

    single(qualifier = DatastoreQualifiers.applicationScope) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // ------------------------------------------------------------------
    // Domain stores (all share the "user_prefs" file)
    // ------------------------------------------------------------------

    single {
        AppearanceStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        AudioStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        AudioCacheStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        AudioEffectsStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        DownloadsStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        PlayerEngineStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        ExperimentalStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        ServerIdentityStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        HomeDiscoveryStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
            get<ServerIdentityStore>(),
        )
    }

    single {
        LibraryStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        NavigationStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        NetworkOfflineStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        NotificationStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        PlaybackStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        AppRuntimeStateStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        ScreensaverStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        SearchFiltersStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        SettingsRecentsStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        PinRateLimiter(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        SecurityStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        SubtitleLanguageStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        SyncPlayCastStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        VideoPlayerStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        WidgetDataStore(
            get(DatastoreQualifiers.userPreferencesDataStore),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    // ------------------------------------------------------------------
    // Per-file preference facades (credential stores come from the
    // platform modules)
    // ------------------------------------------------------------------

    single {
        SeerrPreferencesStore(
            get(DatastoreQualifiers.seerrPreferencesDataStore),
            get<SeerrSecureCredentialsStore>(),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        ArrPreferencesStore(
            get(DatastoreQualifiers.arrPreferencesDataStore),
            get<ArrSecureCredentialsStore>(),
            get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        SubtitleProviderPreferencesStore(
            get(DatastoreQualifiers.subtitleProviderPreferencesDataStore),
            get<SubtitleProviderSecureCredentialsStore>(),
        )
    }

    // ------------------------------------------------------------------
    // Composites
    // ------------------------------------------------------------------

    single {
        PreferenceProjections(
            get(DatastoreQualifiers.applicationScope),
            get<PlaybackStore>(),
            get<VideoPlayerStore>(),
            get<PlayerEngineStore>(),
            get<SubtitleLanguageStore>(),
            get<AudioStore>(),
            get<AudioEffectsStore>(),
            get<AudioCacheStore>(),
            get<AppearanceStore>(),
            get<HomeDiscoveryStore>(),
            get<LibraryStore>(),
            get<NavigationStore>(),
            get<DownloadsStore>(),
            get<NetworkOfflineStore>(),
            get<NotificationStore>(),
            get<SyncPlayCastStore>(),
            get<SecurityStore>(),
            get<ExperimentalStore>(),
            get<ScreensaverStore>(),
        )
    }

    single {
        UserPreferencesStore(
            get(DatastoreQualifiers.applicationScope),
            get(DatastoreQualifiers.userPreferencesDataStore),
            get<PreferenceProjections>(),
            get<PlaybackStore>(),
            get<AppearanceStore>(),
            get<VideoPlayerStore>(),
            get<DownloadsStore>(),
            get<PlayerEngineStore>(),
            get<HomeDiscoveryStore>(),
            get<AudioStore>(),
            get<AudioEffectsStore>(),
            get<AudioCacheStore>(),
            get<LibraryStore>(),
            get<NavigationStore>(),
            get<NetworkOfflineStore>(),
            get<NotificationStore>(),
            get<ScreensaverStore>(),
            get<SecurityStore>(),
            get<SubtitleLanguageStore>(),
            get<SyncPlayCastStore>(),
            get<ExperimentalStore>(),
            get<AppRuntimeStateStore>(),
        )
    }

    single {
        PreferencesEditScope(
            get<PlaybackStore>(),
            get<AppearanceStore>(),
            get<VideoPlayerStore>(),
            get<DownloadsStore>(),
            get<PlayerEngineStore>(),
            get<HomeDiscoveryStore>(),
            get<AudioStore>(),
            get<AudioEffectsStore>(),
            get<AudioCacheStore>(),
            get<LibraryStore>(),
            get<NavigationStore>(),
            get<NetworkOfflineStore>(),
            get<NotificationStore>(),
            get<ScreensaverStore>(),
            get<SecurityStore>(),
            get<SubtitleLanguageStore>(),
            get<SyncPlayCastStore>(),
            get<ExperimentalStore>(),
            get<AppRuntimeStateStore>(),
        )
    }

    single {
        PreferencesEditor(
            get(DatastoreQualifiers.applicationScope),
            get<PreferencesEditScope>(),
            get<UserPreferencesStore>(),
        )
    }

    single {
        VideoPlayerAggregateStore(
            get(DatastoreQualifiers.applicationScope),
            get<PlaybackStore>(),
            get<VideoPlayerStore>(),
            get<AudioStore>(),
            get<AudioEffectsStore>(),
            get<SubtitleLanguageStore>(),
            get<PlayerEngineStore>(),
            get<SecurityStore>(),
        )
    }
}
