package com.raulshma.jellyplay.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.PlayOnViewModel
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import com.raulshma.jellyplay.feature.details.DetailAudioPlayback
import com.raulshma.jellyplay.feature.details.DetailThemeMusic
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerEngine
import com.raulshma.jellyplay.floating.FloatingPlayerState
import com.raulshma.jellyplay.shell.AppLockState
import com.raulshma.jellyplay.shell.SessionCoordinator
import com.raulshma.jellyplay.shell.SyncPlayOpenCoordinator
import com.raulshma.jellyplay.shell.UpdateCoordinator
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
 * App-side Koin graph (wave 8B — Hilt removal): every former @Inject/@Singleton
 * ctor class and @Binds interface pair that used to live in :app's Hilt
 * component (plus the deleted WidgetModule's three @Binds) constructs here.
 * 1:1 with the old constructors — Context params are the application context,
 * the application [CoroutineScope] is the shared DatastoreQualifiers single
 * (the javax @ApplicationScope qualifier died with Hilt).
 *
 * Data-layer deps (AuthRepository, RealtimeConnection, the schedulers, …)
 * resolve from the core graphs: the legacy remainder lands in
 * androidCoreDataModule/androidNotificationModule (wave 8A) and the shared
 * stores from datastoreCommonModule/androidDatastoreModule — one framework
 * per type, this module owns only :app classes.
 */
fun androidAppModule(context: Context): Module = module {
    single { DeepLinkHandler() }

    // App-scoped PIN/biometric lock flag (wave 20E): the single source of
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
    single { SyncPlayOpenCoordinator(syncPlayManager = get()) }

    // Startup initializers (formerly field-injected into the Application and
    // driven off the @ApplicationScope coroutine scope).
    single {
        DownloadRecoveryInitializer(
            context = context,
            downloadDao = get(),
            downloadEnqueuer = get(),
        )
    }
    single {
        CacheMaintenanceInitializer(
            mediaRepository = get(),
            offlineRepository = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
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
        ContinueWatchingBroadcasterImpl(context = context)
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
 * HiltMusicMessageBus/HiltDetailAudioPlayback/HiltDetailThemeMusic/
 * HiltAudioPlayerEngine/HiltAudioPlayerCast classes in the deleted
 * HiltInteropModule). Same adapter bodies, direct Koin resolution: the
 * AudioPlaybackManager/CastManager/UserMessageBus/ThemeMusicPlayer targets
 * are Koin-owned by the core graphs now (wave 8A), so no EntryPoint bridge
 * remains. Desktop halves are the no-op defs in each shared module's jvmMain.
 */
fun androidAppInteropAdaptersModule(application: Application): Module = module {
    single<MusicMessageBus> { AppMusicMessageBus(bus = get()) }
    single<DetailAudioPlayback> { AppDetailAudioPlayback(manager = get()) }
    single<DetailThemeMusic> { AppDetailThemeMusic(player = get()) }
    single<AudioPlayerEngine> { AppAudioPlayerEngine(manager = get()) }
    single<AudioPlayerCast> { AppAudioPlayerCast(castManager = get(), application = application) }
}

/** Bridges the shared music module's [MusicMessageBus] seam to the core bus. */
private class AppMusicMessageBus(
    private val bus: UserMessageBus,
) : MusicMessageBus {
    override fun error(message: String) = bus.error(message)
}

/** Details feature seam: per-item audio playback over the shared manager. */
private class AppDetailAudioPlayback(
    private val manager: AudioPlaybackManager,
) : DetailAudioPlayback {
    override fun play(itemId: String) = manager.play(itemId)
}

/** Details feature seam: ambient theme music over the shared player. */
private class AppDetailThemeMusic(
    private val player: ThemeMusicPlayer,
) : DetailThemeMusic {
    override fun playThemeFor(itemId: String) = player.playThemeFor(itemId)
    override fun stop() = player.stop()
}

/**
 * Audio-player feature seam: the transport/metadata/lyrics half of
 * AudioPlaybackManager that is not on the shared AudioQueueManager /
 * AudioEffectsManager contracts — a pure delegate.
 */
private class AppAudioPlayerEngine(
    private val manager: AudioPlaybackManager,
) : AudioPlayerEngine {
    override val title get() = manager.title
    override val artist get() = manager.artist
    override val artistId get() = manager.artistId
    override val album get() = manager.album
    override val albumArtUrl get() = manager.albumArtUrl
    override val isPlaying get() = manager.isPlaying
    override val currentPosition get() = manager.currentPosition
    override val duration get() = manager.duration
    override val speed get() = manager.speed
    override val playbackError get() = manager.playbackError
    override val isLoadingItem get() = manager.isLoadingItem
    override val crossfadeDurationMs get() = manager.crossfadeDurationMs
    override val undoEvents get() = manager.undoEvents
    override val abLoopStartMs get() = manager.abLoopStartMs
    override val abLoopEndMs get() = manager.abLoopEndMs
    override val lyrics get() = manager.lyrics
    override val currentLyricIndex get() = manager.currentLyricIndex
    override val lyricsSource get() = manager.lyricsSource
    override val isFetchingLyrics get() = manager.isFetchingLyrics
    override val lyricsOffsetMs get() = manager.lyricsOffsetMs
    override fun play(itemId: String) = manager.play(itemId)
    override fun seekTo(positionMs: Long) = manager.seekTo(positionMs)
    override fun togglePlayPause() = manager.togglePlayPause()
    override fun pause() = manager.pause()
    override fun changePlaybackSpeed(value: Float) = manager.changePlaybackSpeed(value)
    override fun setSkipPreviousThreshold(ms: Long) = manager.setSkipPreviousThreshold(ms)
    override fun setCrossfadeDurationMs(ms: Long) = manager.setCrossfadeDurationMs(ms)
    override fun setGaplessEnabled(enabled: Boolean) = manager.setGaplessEnabled(enabled)
    override fun getImageUrl(itemId: String): String = manager.getImageUrl(itemId)
    override fun searchLyrics(query: String, callback: (Result<List<com.raulshma.jellyplay.core.model.LrcLibTrack>>) -> Unit) =
        manager.searchLyrics(query, callback)
    override fun applyLyrics(lrcLibId: Long) = manager.applyLyrics(lrcLibId)
    override fun setLyricsOffset(offsetMs: Long) = manager.setLyricsOffset(offsetMs)
    override fun stopAndRelease() = manager.stopAndRelease()
    override fun undoLastQueueOperation(): Boolean = manager.undoLastQueueOperation()
    override fun cycleAbLoop() = manager.cycleAbLoop()
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

/**
 * Resolves the activity-scoped [MainViewModel] from a composable context.
 * Uses the plain ViewModelProvider default key, so the instance is the SAME
 * one MainActivity's `by viewModels` delegate holds (same store, same key) —
 * do not swap this for koinViewModel() without re-checking that identity.
 */
fun mainViewModelFromKoin(owner: ViewModelStoreOwner): MainViewModel =
    ViewModelProvider(owner, KoinViewModelFactory)[MainViewModel::class.java]
