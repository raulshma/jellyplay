package com.raulshma.jellyplay.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * KMP cutover wiring (docs/kmp-migration-plan.md §Phase C4): Koin owns
 * construction of every store in :shared:core:datastore
 * ([datastoreCommonModule] + [androidDatastoreModule]); these Hilt providers
 * are thin bridges so Hilt consumers keep compiling and both frameworks
 * share the same instances. Shim deleted at Phase X.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeerrPreferencesDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArrPreferencesDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SubtitleProviderPreferencesDataStore

@Module
@InstallIn(SingletonComponent::class)
object SharedStoreModule {

    // ------------------------------------------------------------------
    // Per-file preference DataStores — created by Koin at the identical
    // paths the pre-KMP wiring used (filesDir/datastore/<name>.preferences_pb)
    // so existing installs keep their data.
    // ------------------------------------------------------------------

    @SeerrPreferencesDataStore
    @Provides
    @Singleton
    fun provideSeerrPreferencesDataStore(): DataStore<Preferences> =
        koin().get(DatastoreQualifiers.seerrPreferencesDataStore)

    @ArrPreferencesDataStore
    @Provides
    @Singleton
    fun provideArrPreferencesDataStore(): DataStore<Preferences> =
        koin().get(DatastoreQualifiers.arrPreferencesDataStore)

    @SubtitleProviderPreferencesDataStore
    @Provides
    @Singleton
    fun provideSubtitleProviderPreferencesDataStore(): DataStore<Preferences> =
        koin().get(DatastoreQualifiers.subtitleProviderPreferencesDataStore)

    // ------------------------------------------------------------------
    // Encrypted secret storage (seam actuals)
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideSeerrSecureCredentialsStore(): SeerrSecureCredentialsStore = koin().get()

    @Provides
    @Singleton
    fun provideArrSecureCredentialsStore(): ArrSecureCredentialsStore = koin().get()

    @Provides
    @Singleton
    fun provideSubtitleProviderSecureCredentialsStore(): SubtitleProviderSecureCredentialsStore = koin().get()

    // ------------------------------------------------------------------
    // Domain stores (all share the "user_prefs" file)
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideAppearanceStore(): AppearanceStore = koin().get()

    @Provides
    @Singleton
    fun provideAudioStore(): AudioStore = koin().get()

    @Provides
    @Singleton
    fun provideAudioCacheStore(): AudioCacheStore = koin().get()

    @Provides
    @Singleton
    fun provideAudioEffectsStore(): AudioEffectsStore = koin().get()

    @Provides
    @Singleton
    fun provideDownloadsStore(): DownloadsStore = koin().get()

    @Provides
    @Singleton
    fun providePlayerEngineStore(): PlayerEngineStore = koin().get()

    @Provides
    @Singleton
    fun provideExperimentalStore(): ExperimentalStore = koin().get()

    @Provides
    @Singleton
    fun provideServerIdentityStore(): ServerIdentityStore = koin().get()

    @Provides
    @Singleton
    fun provideHomeDiscoveryStore(): HomeDiscoveryStore = koin().get()

    @Provides
    @Singleton
    fun provideLibraryStore(): LibraryStore = koin().get()

    @Provides
    @Singleton
    fun provideNavigationStore(): NavigationStore = koin().get()

    @Provides
    @Singleton
    fun provideNetworkOfflineStore(): NetworkOfflineStore = koin().get()

    @Provides
    @Singleton
    fun provideNotificationStore(): NotificationStore = koin().get()

    @Provides
    @Singleton
    fun providePlaybackStore(): PlaybackStore = koin().get()

    @Provides
    @Singleton
    fun provideAppRuntimeStateStore(): AppRuntimeStateStore = koin().get()

    @Provides
    @Singleton
    fun provideScreensaverStore(): ScreensaverStore = koin().get()

    @Provides
    @Singleton
    fun provideSearchFiltersStore(): SearchFiltersStore = koin().get()

    @Provides
    @Singleton
    fun provideSettingsRecentsStore(): SettingsRecentsStore = koin().get()

    @Provides
    @Singleton
    fun providePinRateLimiter(): PinRateLimiter = koin().get()

    @Provides
    @Singleton
    fun provideSecurityStore(): SecurityStore = koin().get()

    @Provides
    @Singleton
    fun provideSubtitleLanguageStore(): SubtitleLanguageStore = koin().get()

    @Provides
    @Singleton
    fun provideSyncPlayCastStore(): SyncPlayCastStore = koin().get()

    @Provides
    @Singleton
    fun provideVideoPlayerStore(): VideoPlayerStore = koin().get()

    @Provides
    @Singleton
    fun provideWidgetDataStore(): WidgetDataStore = koin().get()

    // ------------------------------------------------------------------
    // Per-file preference facades
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideSeerrPreferencesStore(): SeerrPreferencesStore = koin().get()

    @Provides
    @Singleton
    fun provideArrPreferencesStore(): ArrPreferencesStore = koin().get()

    @Provides
    @Singleton
    fun provideSubtitleProviderPreferencesStore(): SubtitleProviderPreferencesStore = koin().get()

    // ------------------------------------------------------------------
    // Composites
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun providePreferenceProjections(): PreferenceProjections = koin().get()

    @Provides
    @Singleton
    fun provideUserPreferencesStore(): UserPreferencesStore = koin().get()

    @Provides
    @Singleton
    fun providePreferencesEditScope(): PreferencesEditScope = koin().get()

    @Provides
    @Singleton
    fun providePreferencesEditor(): PreferencesEditor = koin().get()

    @Provides
    @Singleton
    fun provideVideoPlayerAggregateStore(): VideoPlayerAggregateStore = koin().get()
}
