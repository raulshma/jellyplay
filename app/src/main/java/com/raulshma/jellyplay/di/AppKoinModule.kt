package com.raulshma.jellyplay.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.PlayOnViewModel
import com.raulshma.jellyplay.shared.core.data.R
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.download.DownloadOutcomeMessenger
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.notification.scheduler.NotificationReconnectListener
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import com.raulshma.jellyplay.feature.details.DetailThemeMusic
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import com.raulshma.jellyplay.feature.player.video.engine.VideoStreamCache
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.floating.FloatingPlayerState
import com.raulshma.jellyplay.shell.AppLockState
import com.raulshma.jellyplay.shell.SessionCoordinator
import com.raulshma.jellyplay.shell.SyncPlayOpenCoordinator
import com.raulshma.jellyplay.shell.UpdateCoordinator
import com.raulshma.jellyplay.shell.WhatsNewCoordinator
import com.raulshma.jellyplay.startup.AppStartupPrewarms
import com.raulshma.jellyplay.startup.CacheMaintenanceInitializer
import com.raulshma.jellyplay.startup.DownloadRecoveryInitializer
import com.raulshma.jellyplay.widget.ContinueWatchingBroadcasterImpl
import com.raulshma.jellyplay.widget.LibrarySyncHookImpl
import com.raulshma.jellyplay.widget.NowPlayingWidgetUpdater
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import com.raulshma.jellyplay.widget.WidgetWorkSchedulerImpl
import com.raulshma.jellyplay.widget.config.WidgetConfigViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.module.Module
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * App-side Koin graph (Hilt removal): every former @Inject/@Singleton
 * ctor class and @Binds interface pair that used to live in :app's Hilt
 * component (plus the deleted WidgetModule's three @Binds) constructs here.
 * 1:1 with the old constructors — Context params are the application context,
 * the application [CoroutineScope] is the shared DatastoreQualifiers single
 * (the javax @ApplicationScope qualifier died with Hilt).
 *
 * Data-layer deps (AuthRepository, RealtimeConnection, the schedulers, …)
 * resolve from the core graphs: the legacy remainder lands in
 * androidCoreDataModule/androidNotificationModule and the shared
 * stores from datastoreCommonModule/androidDatastoreModule — one framework
 * per type, this module owns only :app classes.
 */
fun androidAppModule(context: Context): Module = module {
    single { DeepLinkHandler() }

    // App-scoped PIN/biometric lock flag: the single source of
    // truth for "unlocked" that MainActivity's compose gate renders AND
    // PlayerActivity's locked-redirect check reads — hoisted off
    // MainActivity's former compose-local state so the media-notification
    // class-name PendingIntent can no longer reach playback without a
    // challenge (see AppLockState KDoc).
    single { AppLockState() }

    // Floating overlay bridge: both deps resolve from the core graphs
    // (ActivePlayerController from androidCoreDataModule's legacy remainder,
    // VideoMiniPlayerState from the shared playback state module).
    single {
        FloatingPlayerState(
            activePlayerController = get(),
            miniPlayerState = get(),
        )
    }

    // Shell coordinators started on the activity-scoped MainViewModel's scope.
    single {
        SessionCoordinator(
            context = context,
            authRepository = get(),
            realtimeConnection = get(),
            experimentalStore = get(),
            serverIdentityStore = get(),
            serverHealthMonitor = get(),
            remoteControlReceiver = get(),
            widgetWorkScheduler = get(),
            cacheMaintenanceInitializer = get(),
            mediaRepository = get(),
        )
    }
    single {
        UpdateCoordinator(
            appUpdateRepository = get(),
            apkInstallBuilder = get(),
            experimentalStore = get(),
        )
    }
    single {
        WhatsNewCoordinator(
            whatsNewRepository = get(),
            experimentalStore = get(),
            appRuntimeStateStore = get(),
            currentVersionName = { installedVersionName(context) },
        )
    }
    single { SyncPlayOpenCoordinator(syncPlayManager = get()) }

    // Startup initializers (formerly field-injected into the Application and
    // driven off the @ApplicationScope coroutine scope).
    single {
        DownloadRecoveryInitializer(
            downloadDao = get(),
            downloadEnqueuer = get(),
        )
    }
    single {
        CacheMaintenanceInitializer(
            lyricsRepository = get(),
            offlineRepository = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }

    // Cold-start prewarm choreography (formerly ~15 inline `by lazyFromKoin`
    // Application fields + the launch blocks in onCreate). Every collaborator
    // arrives as a memoizing kotlin.Lazy over this container — `lazy { get() }`
    // — so construction still defers to the IO launch block that first touches
    // it, exactly like the former Application fields; only the DataStore
    // application scope single resolves eagerly, and it was constructed at
    // container start anyway.
    single {
        AppStartupPrewarms(
            applicationScope = get(DatastoreQualifiers.applicationScope),
            networkOfflineStore = lazy { get<NetworkOfflineStore>() },
            serverIdentityStore = lazy { get<ServerIdentityStore>() },
            securityStore = lazy { get<SecurityStore>() },
            fontProvider = lazy { get<FontProvider>() },
            videoStreamCache = lazy { get<VideoStreamCache>() },
            audioPlaybackManager = lazy { get<AudioPlaybackManager>() },
            nowPlayingWidgetUpdater = lazy { get<NowPlayingWidgetUpdater>() },
            widgetWorkScheduler = lazy { get<WidgetWorkScheduler>() },
            userDataSyncScheduler = lazy { get<UserDataSyncScheduler>() },
            playbackSyncScheduler = lazy { get<PlaybackSyncScheduler>() },
            playbackSyncReconnectListener = lazy { get<PlaybackSyncReconnectListener>() },
            downloadReconnectListener = lazy { get<DownloadReconnectListener>() },
            notificationReconnectListener = lazy { get<NotificationReconnectListener>() },
            autoDownloadScheduler = lazy { get<AutoDownloadScheduler>() },
            notificationScheduler = lazy { get<NotificationScheduler>() },
            downloadRecoveryInitializer = lazy { get<DownloadRecoveryInitializer>() },
            appUpdateRepository = lazy { get<AppUpdateRepository>() },
        )
    }

    // Widgets: the Now Playing push engine plus the three interface bindings
    // the shared home feature consumes (former WidgetModule @Binds pairs).
    single {
        NowPlayingWidgetUpdater(
            context = context,
            audioPlaybackManager = get(),
        )
    }
    single<WidgetWorkScheduler> {
        WidgetWorkSchedulerImpl(context = context)
    }
    single<ContinueWatchingBroadcaster> {
        ContinueWatchingBroadcasterImpl(
            context = context,
            widgetDataStore = get(),
            playbackRepository = get(),
        )
    }
    single<LibrarySyncHook> {
        LibrarySyncHookImpl(
            autoDownloadScheduler = get(),
            widgetWorkScheduler = get(),
        )
    }
}

/**
 * App-shell ViewModels. Definitions are plain factories: the Activity sites
 * resolve them through the AndroidX ViewModelStore (see [KoinViewModelFactory]
 * below), so per-owner instance semantics — not Koin's own scoping — decide
 * sharing, exactly like the former Hilt-ViewModel integration's
 * viewModels() pair.
 */
val androidAppViewModelsModule: Module = module {
    viewModel {
        MainViewModel(
            authRepository = get(),
            projections = get(),
            homeDiscoveryStore = get(),
            appRuntimeStateStore = get(),
            pinRateLimiter = get(),
            remoteControlReceiver = get(),
            appShortcutManager = get(),
            deepLinkHandler = get(),
            playbackRepository = get(),
            downloadRepository = get(),
            playbackSourceResolver = get(),
            offlineModeManager = get(),
            userMessageBus = get(),
            sessionCoordinator = get(),
            updateCoordinator = get(),
            syncPlayOpenCoordinator = get(),
            whatsNewCoordinator = get(),
        )
    }
    viewModel {
        PlayOnViewModel(
            jellyfinStrategy = get(),
            audioPlaybackManager = get(),
        )
    }
    viewModel {
        WidgetConfigViewModel(widgetDataStore = get())
    }
}

/**
 * Shared-feature seam adapters over the core data singletons (formerly the
 * HiltMusicMessageBus/HiltDetailThemeMusic/
 * HiltAudioPlayerEngine/HiltAudioPlayerCast classes in the deleted
 * HiltInteropModule). Same adapter bodies, direct Koin resolution: the
 * CastManager/UserMessageBus/ThemeMusicPlayer targets
 * are Koin-owned by the core graphs now, so no EntryPoint bridge
 * remains. Desktop halves are the no-op defs in each shared module's jvmMain.
 * (DetailAudioPlayback is gone entirely — its only caller, the detail
 * local-track play command, was production-unreachable and died with the
 * DetailUiEvent fold.)
 *
 * AudioPlayerEngine is NOT bridged here anymore: the contract moved into
 * core/data (beside AudioQueueManager/AudioEffectsManager) and the media3
 * manager implements it directly — androidCoreDataModule holds the alias.
 */
fun androidAppInteropAdaptersModule(application: Application): Module = module {
    single<MusicMessageBus> { AppMusicMessageBus(bus = get()) }
    single<DetailThemeMusic> { AppDetailThemeMusic(player = get()) }
    single<AudioPlayerCast> { AppAudioPlayerCast(castManager = get(), application = application) }
    // Dev v0.10.7 quick-action flow: core/data's download-outcome seam
    // (MediaDownloadActions.downloadAndReport) bridged to the UserMessageBus
    // single — the app graph is the only one that sees both (shared:core:data
    // cannot depend on core/ui; desktop ships its own def in desktopDataModule).
    single<DownloadOutcomeMessenger> { AppDownloadOutcomeMessenger(userMessageBus = get()) }
}

/** Bridges the shared music module's [MusicMessageBus] seam to the core bus. */
private class AppMusicMessageBus(
    private val bus: UserMessageBus,
) : MusicMessageBus {
    override fun error(message: String) = bus.error(message)
}

/** core/data download-outcome seam: exact info/error toasts over the core bus. */
private class AppDownloadOutcomeMessenger(
    private val userMessageBus: UserMessageBus,
) : DownloadOutcomeMessenger {
    override fun downloadStarted() {
        userMessageBus.info(UiText.Resource(R.string.data_download_started))
    }

    override fun downloadStartFailed() {
        userMessageBus.error(UiText.Resource(R.string.data_download_start_failed))
    }
}

/** Details feature seam: ambient theme music over the shared player. */
private class AppDetailThemeMusic(
    private val player: ThemeMusicPlayer,
) : DetailThemeMusic {
    override fun playThemeFor(itemId: String) = player.playThemeFor(itemId)
    override fun stop() = player.stop()
}

/**
 * Audio-player feature seam: the cast half over the core CastManager. Holds
 * the application context the legacy discovery/connect calls need and hides
 * the media3 MediaItem/Player.Listener construction the player VM used to
 * build inline (audio casts carry no subtitle/quality variants, so
 * CastMediaOptions stays the empty default). Devices surface as display
 * names only, exactly like the legacy dialog's devices[which].
 */
private class AppAudioPlayerCast(
    private val castManager: CastManager,
    application: Application,
) : AudioPlayerCast {
    private val appContext = application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override val isConnected get() = castManager.isConnectedFlow
    override val discoveredDeviceNames = castManager.discoveredDevices
        .map { devices -> devices.map { it.name } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override fun startDiscovery() = castManager.startDiscovery(appContext)
    override fun stopDiscovery() = castManager.stopDiscovery()
    override fun connect(deviceName: String) {
        castManager.discoveredDevices.value
            .firstOrNull { it.name == deviceName }
            ?.let { castManager.connect(appContext, it) }
    }
    override fun disconnect() = castManager.disconnect(appContext)
    override fun acquireConsumer() = castManager.acquireConsumer()
    override fun releaseConsumer() = castManager.releaseConsumer()
    override fun loadMedia(itemId: String, startPositionMs: Long) {
        castManager.loadMedia(
            mediaItem = androidx.media3.common.MediaItem.Builder().setMediaId(itemId).build(),
            startPositionMs = startPositionMs,
            listener = object : androidx.media3.common.Player.Listener {},
            options = com.raulshma.jellyplay.core.data.cast.CastMediaOptions(),
        )
    }
    override fun play() = castManager.play()
    override fun pause() = castManager.pause()
    override fun seekTo(positionMs: Long) = castManager.seekTo(positionMs)
    override fun setVolume(volume: Float) = castManager.setVolume(volume)
}

/**
 * The installed version name for the What's New coordinator's show-once
 * comparison — a twin of core:data AndroidDataModule's update-check probe
 * (same PackageManager read, same versionName-first preference). Kept here
 * because that seam is module-private and update-flow-local; when a third
 * consumer appears, hoist both into one shared Koin single.
 */
private fun installedVersionName(context: Context): String {
    val pm = context.packageManager
    val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    @Suppress("DEPRECATION")
    return info.versionName ?: androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toString()
}

/**
 * [ViewModelProvider.Factory] that constructs ViewModels from the Koin
 * container while leaving ownership — instance caching, config-change
 * survival, onCleared — to the AndroidX ViewModelStore it is handed to.
 *
 * This is the explicit "bind to the Activity ViewModelStore" decision for the
 * two resolution sites that MUST observe one shared activity-scoped
 * MainViewModel instance (MainActivity's `by viewModels` delegate and the
 * MainNavDisplay admin gate in JellyPlayApp): both go through
 * ViewModelProvider with the default canonical-name key, so they resolve the
 * same store entry — the exact contract the former androidx Hilt
 * ViewModel composable provided.
 * (koinViewModel()'s Koin-scoped store keying cannot be reached from the
 * pre-composition splash-gate path, so the ViewModelProvider contract is the
 * deterministic single mechanism for this VM.)
 */
object KoinViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KoinPlatform.getKoin().get(modelClass.kotlin, null)
}
