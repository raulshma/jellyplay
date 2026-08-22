package com.raulshma.jellyplay.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.AndroidSecureKeyValueStorage
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import okio.Path
import okio.Path.Companion.toPath

/**
 * KMP cutover wiring (docs/kmp-migration-plan.md §Phase C2): the store classes
 * moved to :shared:core:datastore as plain constructors (DI annotations don't
 * exist in commonMain), so this Android-side Hilt module owns construction
 * until the Koin flip (§Phase C4/X). Replaced by Koin modules at cutover.
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
    // Per-file preference DataStores — paths identical to the pre-KMP
    // Context delegates (filesDir/datastore/<name>.preferences_pb) so
    // existing installs keep their data.
    // ------------------------------------------------------------------

    @SeerrPreferencesDataStore
    @Provides
    @Singleton
    fun provideSeerrPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = preferencesDataStore(context, "seerr_prefs")

    @ArrPreferencesDataStore
    @Provides
    @Singleton
    fun provideArrPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = preferencesDataStore(context, "arr_prefs")

    @SubtitleProviderPreferencesDataStore
    @Provides
    @Singleton
    fun provideSubtitleProviderPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = preferencesDataStore(context, "subtitle_provider_prefs")

    private fun preferencesDataStore(context: Context, name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath {
            File(context.filesDir, "datastore/$name.preferences_pb").absolutePath.toPath()
        }

    // ------------------------------------------------------------------
    // Encrypted secret storage (seam actuals)
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideSeerrSecureCredentialsStore(
        @ApplicationContext context: Context,
    ): SeerrSecureCredentialsStore =
        SeerrSecureCredentialsStore(AndroidSecureKeyValueStorage(context, "seerr_secure_prefs"))

    @Provides
    @Singleton
    fun provideArrSecureCredentialsStore(
        @ApplicationContext context: Context,
    ): ArrSecureCredentialsStore =
        ArrSecureCredentialsStore(AndroidSecureKeyValueStorage(context, "arr_secure_prefs"))

    @Provides
    @Singleton
    fun provideSubtitleProviderSecureCredentialsStore(
        @ApplicationContext context: Context,
    ): SubtitleProviderSecureCredentialsStore =
        SubtitleProviderSecureCredentialsStore(
            AndroidSecureKeyValueStorage(context, "subtitle_provider_secure_prefs"),
        )

    // ------------------------------------------------------------------
    // Domain stores (all share the "user_prefs" file)
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideAppearanceStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): AppearanceStore = AppearanceStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideAudioStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): AudioStore = AudioStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideAudioCacheStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): AudioCacheStore = AudioCacheStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideAudioEffectsStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): AudioEffectsStore = AudioEffectsStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideDownloadsStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): DownloadsStore = DownloadsStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun providePlayerEngineStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): PlayerEngineStore = PlayerEngineStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideExperimentalStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): ExperimentalStore = ExperimentalStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideServerIdentityStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): ServerIdentityStore = ServerIdentityStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideHomeDiscoveryStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
        identityStore: ServerIdentityStore,
    ): HomeDiscoveryStore = HomeDiscoveryStore(dataStore, externalScope, identityStore)

    @Provides
    @Singleton
    fun provideLibraryStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): LibraryStore = LibraryStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideNavigationStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): NavigationStore = NavigationStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideNetworkOfflineStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): NetworkOfflineStore = NetworkOfflineStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideNotificationStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): NotificationStore = NotificationStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun providePlaybackStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): PlaybackStore = PlaybackStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideAppRuntimeStateStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): AppRuntimeStateStore = AppRuntimeStateStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideScreensaverStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): ScreensaverStore = ScreensaverStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideSearchFiltersStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): SearchFiltersStore = SearchFiltersStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideSettingsRecentsStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope scope: CoroutineScope,
    ): SettingsRecentsStore = SettingsRecentsStore(dataStore, scope)

    @Provides
    @Singleton
    fun providePinRateLimiter(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): PinRateLimiter = PinRateLimiter(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideSecurityStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): SecurityStore = SecurityStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideSubtitleLanguageStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): SubtitleLanguageStore = SubtitleLanguageStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideSyncPlayCastStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): SyncPlayCastStore = SyncPlayCastStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideVideoPlayerStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): VideoPlayerStore = VideoPlayerStore(dataStore, externalScope)

    @Provides
    @Singleton
    fun provideWidgetDataStore(
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): WidgetDataStore = WidgetDataStore(dataStore, externalScope)

    // ------------------------------------------------------------------
    // Per-file preference facades
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideSeerrPreferencesStore(
        @SeerrPreferencesDataStore dataStore: DataStore<Preferences>,
        secureCredentialsStore: SeerrSecureCredentialsStore,
        @ApplicationScope externalScope: CoroutineScope,
    ): SeerrPreferencesStore = SeerrPreferencesStore(dataStore, secureCredentialsStore, externalScope)

    @Provides
    @Singleton
    fun provideArrPreferencesStore(
        @ArrPreferencesDataStore dataStore: DataStore<Preferences>,
        secureCredentialsStore: ArrSecureCredentialsStore,
        @ApplicationScope externalScope: CoroutineScope,
    ): ArrPreferencesStore = ArrPreferencesStore(dataStore, secureCredentialsStore, externalScope)

    @Provides
    @Singleton
    fun provideSubtitleProviderPreferencesStore(
        @SubtitleProviderPreferencesDataStore dataStore: DataStore<Preferences>,
        secureCredentialsStore: SubtitleProviderSecureCredentialsStore,
    ): SubtitleProviderPreferencesStore =
        SubtitleProviderPreferencesStore(dataStore, secureCredentialsStore)

    // ------------------------------------------------------------------
    // Composites
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun providePreferenceProjections(
        @ApplicationScope scope: CoroutineScope,
        playbackStore: PlaybackStore,
        videoPlayerStore: VideoPlayerStore,
        engineStore: PlayerEngineStore,
        subtitleStore: SubtitleLanguageStore,
        audioStore: AudioStore,
        audioEffectsStore: AudioEffectsStore,
        audioCacheStore: AudioCacheStore,
        appearanceStore: AppearanceStore,
        homeDiscoveryStore: HomeDiscoveryStore,
        libraryStore: LibraryStore,
        navigationStore: NavigationStore,
        downloadsStore: DownloadsStore,
        networkOfflineStore: NetworkOfflineStore,
        notificationStore: NotificationStore,
        syncPlayCastStore: SyncPlayCastStore,
        securityStore: SecurityStore,
        experimentalStore: ExperimentalStore,
        screensaverStore: ScreensaverStore,
    ): PreferenceProjections = PreferenceProjections(
        scope, playbackStore, videoPlayerStore, engineStore, subtitleStore,
        audioStore, audioEffectsStore, audioCacheStore, appearanceStore,
        homeDiscoveryStore, libraryStore, navigationStore, downloadsStore,
        networkOfflineStore, notificationStore, syncPlayCastStore,
        securityStore, experimentalStore, screensaverStore,
    )

    @Provides
    @Singleton
    fun provideUserPreferencesStore(
        @ApplicationScope externalScope: CoroutineScope,
        @UserPreferencesDataStore dataStore: DataStore<Preferences>,
        projections: PreferenceProjections,
        playbackStore: PlaybackStore,
        appearanceStore: AppearanceStore,
        videoPlayerStore: VideoPlayerStore,
        downloadsStore: DownloadsStore,
        engineStore: PlayerEngineStore,
        homeDiscoveryStore: HomeDiscoveryStore,
        audioStore: AudioStore,
        audioEffectsStore: AudioEffectsStore,
        audioCacheStore: AudioCacheStore,
        libraryStore: LibraryStore,
        navigationStore: NavigationStore,
        networkOfflineStore: NetworkOfflineStore,
        notificationStore: NotificationStore,
        screensaverStore: ScreensaverStore,
        securityStore: SecurityStore,
        subtitleLanguageStore: SubtitleLanguageStore,
        syncPlayCastStore: SyncPlayCastStore,
        experimentalStore: ExperimentalStore,
        appRuntimeStateStore: AppRuntimeStateStore,
    ): UserPreferencesStore = UserPreferencesStore(
        externalScope, dataStore, projections, playbackStore, appearanceStore,
        videoPlayerStore, downloadsStore, engineStore, homeDiscoveryStore,
        audioStore, audioEffectsStore, audioCacheStore, libraryStore,
        navigationStore, networkOfflineStore, notificationStore,
        screensaverStore, securityStore, subtitleLanguageStore,
        syncPlayCastStore, experimentalStore, appRuntimeStateStore,
    )

    @Provides
    @Singleton
    fun providePreferencesEditScope(
        playback: PlaybackStore,
        appearance: AppearanceStore,
        videoPlayer: VideoPlayerStore,
        downloads: DownloadsStore,
        engine: PlayerEngineStore,
        homeDiscovery: HomeDiscoveryStore,
        audio: AudioStore,
        audioEffects: AudioEffectsStore,
        audioCache: AudioCacheStore,
        library: LibraryStore,
        navigation: NavigationStore,
        networkOffline: NetworkOfflineStore,
        notification: NotificationStore,
        screensaver: ScreensaverStore,
        security: SecurityStore,
        subtitle: SubtitleLanguageStore,
        syncPlayCast: SyncPlayCastStore,
        experimental: ExperimentalStore,
        appRuntimeState: AppRuntimeStateStore,
    ): PreferencesEditScope = PreferencesEditScope(
        playback, appearance, videoPlayer, downloads, engine, homeDiscovery,
        audio, audioEffects, audioCache, library, navigation, networkOffline,
        notification, screensaver, security, subtitle, syncPlayCast,
        experimental, appRuntimeState,
    )

    @Provides
    @Singleton
    fun providePreferencesEditor(
        @ApplicationScope scope: CoroutineScope,
        editScope: PreferencesEditScope,
        store: UserPreferencesStore,
    ): PreferencesEditor = PreferencesEditor(scope, editScope, store)

    @Provides
    @Singleton
    fun provideVideoPlayerAggregateStore(
        @ApplicationScope scope: CoroutineScope,
        playbackStore: PlaybackStore,
        videoPlayerStore: VideoPlayerStore,
        audioStore: AudioStore,
        audioEffectsStore: AudioEffectsStore,
        subtitleStore: SubtitleLanguageStore,
        engineStore: PlayerEngineStore,
        securityStore: SecurityStore,
    ): VideoPlayerAggregateStore = VideoPlayerAggregateStore(
        scope, playbackStore, videoPlayerStore, audioStore,
        audioEffectsStore, subtitleStore, engineStore, securityStore,
    )
}
