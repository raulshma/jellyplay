// TRANSITIONAL, deleted by app Hilt extinction (builder 8B).
//
// The FILE lives at core/data/di/ but its package is the qualifier's ORIGINAL
// `com.raulshma.jellyplay.core.datastore.di` — :app and :core:notification
// sources import @ApplicationScope from there, and Kotlin allows a
// declaration's package to differ from its directory. The qualifier moved
// here when the :core:datastore shim module was deleted (wave 8A); it dies
// with this file.
package com.raulshma.jellyplay.core.datastore.di

import com.raulshma.jellyplay.core.data.di.koin
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.network.ServerHealthMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueuer
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.update.ApkInstallBuilder
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * Qualifier for the process-wide application [CoroutineScope] (SupervisorJob
 * + Dispatchers.Default). Koin owns the scope itself
 * ([DatastoreQualifiers.applicationScope] single in datastoreCommonModule);
 * Hilt consumers reach it through the bridge provider in
 * [LegacyHiltBridgesModule] below.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * TRANSITIONAL, deleted by app Hilt extinction (builder 8B).
 *
 * Wave 8A moved construction of every legacy :core:data/:core:datastore/
 * :core:network/:core:database type to Koin (androidCoreDataModule + the
 * shared modules' definitions) and deleted the old Hilt provider modules.
 * :app still field-/ctor-injects a subset of those types through Hilt until
 * its own extinction — every provider below is a parameterless
 * `koin().get()` fetch, so Hilt never constructs anything and both
 * frameworks share the Koin singles.
 *
 * Types not visible from this module (UserMessageBus in :core:ui,
 * NotificationScheduler/NotificationReconnectListener in :core:notification)
 * ride an app-side sibling: app/.../di/LegacyHiltBridgesModule.kt.
 */
@Module
@InstallIn(SingletonComponent::class)
object LegacyHiltBridgesModule {

    // ── Framework primitives ────────────────────────────────────────────

    /** Base OkHttp client (cache + interceptor stack); Koin: androidNetworkModule. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = koin().get()

    /** Application scope; Koin: datastoreCommonModule's applicationScope single. */
    @ApplicationScope
    @Provides
    @Singleton
    fun provideApplicationCoroutineScope(): CoroutineScope =
        koin().get(DatastoreQualifiers.applicationScope)

    // ── legacy :core:data singletons (Koin: androidCoreDataModule) ──────

    @Provides
    @Singleton
    fun provideAudioPlaybackManager(): AudioPlaybackManager = koin().get()

    @Provides
    @Singleton
    fun provideRemoteControlReceiver(): RemoteControlReceiver = koin().get()

    @Provides
    @Singleton
    fun provideAppShortcutManager(): AppShortcutManager = koin().get()

    @Provides
    @Singleton
    fun providePipController(): PipController = koin().get()

    @Provides
    @Singleton
    fun provideActivePlayerController(): ActivePlayerController = koin().get()

    @Provides
    @Singleton
    fun provideJellyfinRemotePlayCastStrategy(): JellyfinRemotePlayCastStrategy = koin().get()

    @Provides
    @Singleton
    fun provideAutoDownloadScheduler(): AutoDownloadScheduler = koin().get()

    @Provides
    @Singleton
    fun provideUserDataSyncScheduler(): UserDataSyncScheduler = koin().get()

    @Provides
    @Singleton
    fun providePlaybackSyncScheduler(): PlaybackSyncScheduler = koin().get()

    @Provides
    @Singleton
    fun providePlaybackSyncReconnectListener(): PlaybackSyncReconnectListener = koin().get()

    @Provides
    @Singleton
    fun provideDownloadReconnectListener(): DownloadReconnectListener = koin().get()

    // ── shared-module singles :app still Hilt-injects ───────────────────
    // (dataJvmModule / androidDataModule / datastoreCommonModule /
    // androidDatastoreModule owners)

    @Provides
    @Singleton
    fun provideMediaRepository(): MediaRepository = koin().get()

    @Provides
    @Singleton
    fun providePlaybackRepository(): PlaybackRepository = koin().get()

    @Provides
    @Singleton
    fun provideDownloadRepository(): DownloadRepository = koin().get()

    @Provides
    @Singleton
    fun provideOfflineRepository(): OfflineRepository = koin().get()

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = koin().get()

    @Provides
    @Singleton
    fun provideRealtimeConnection(): RealtimeConnection = koin().get()

    @Provides
    @Singleton
    fun provideSeerrRepository(): SeerrRepository = koin().get()

    @Provides
    @Singleton
    fun providePlaybackSourceResolver(): PlaybackSourceResolver = koin().get()

    @Provides
    @Singleton
    fun provideOfflineModeManager(): OfflineModeManager = koin().get()

    @Provides
    @Singleton
    fun provideNetworkMonitor(): NetworkMonitor = koin().get()

    @Provides
    @Singleton
    fun provideRemoteNavigationBridge(): RemoteNavigationBridge = koin().get()

    @Provides
    @Singleton
    fun provideServerHealthMonitor(): ServerHealthMonitor = koin().get()

    @Provides
    @Singleton
    fun provideSyncPlayManager(): SyncPlayManager = koin().get()

    @Provides
    @Singleton
    fun provideImageUrlProvider(): ImageUrlProvider = koin().get()

    @Provides
    @Singleton
    fun provideVideoMiniPlayerState(): VideoMiniPlayerState = koin().get()

    @Provides
    @Singleton
    fun providePlayerLifecycleManager(): PlayerLifecycleManager = koin().get()

    @Provides
    @Singleton
    fun provideAppUpdateRepository(): AppUpdateRepository = koin().get()

    @Provides
    @Singleton
    fun provideApkInstallBuilder(): ApkInstallBuilder = koin().get()

    // ── shared datastore singles :app still Hilt-injects ────────────────

    @Provides
    @Singleton
    fun provideNetworkOfflineStore(): NetworkOfflineStore = koin().get()

    @Provides
    @Singleton
    fun providePinRateLimiter(): PinRateLimiter = koin().get()

    @Provides
    @Singleton
    fun provideSecurityStore(): SecurityStore = koin().get()

    @Provides
    @Singleton
    fun providePreferenceProjections(): PreferenceProjections = koin().get()

    @Provides
    @Singleton
    fun provideHomeDiscoveryStore(): HomeDiscoveryStore = koin().get()

    @Provides
    @Singleton
    fun provideAppRuntimeStateStore(): com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore =
        koin().get()

    @Provides
    @Singleton
    fun provideExperimentalStore(): ExperimentalStore = koin().get()

    @Provides
    @Singleton
    fun provideServerIdentityStore(): ServerIdentityStore = koin().get()

    @Provides
    @Singleton
    fun provideScreensaverStore(): ScreensaverStore = koin().get()

    @Provides
    @Singleton
    fun provideWidgetDataStore(): WidgetDataStore = koin().get()

    @Provides
    @Singleton
    fun provideSeerrPreferencesStore(): SeerrPreferencesStore = koin().get()

    // ── database / app-composition singles :app still Hilt-injects ──────

    @Provides
    @Singleton
    fun provideDownloadDao(): DownloadDao = koin().get()

    /** Koin: the app composition root's androidDownloadSeamsModule single. */
    @Provides
    @Singleton
    fun provideDownloadEnqueuer(): DownloadEnqueuer = koin().get()
}
