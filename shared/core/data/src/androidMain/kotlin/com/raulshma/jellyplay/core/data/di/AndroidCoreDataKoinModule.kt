package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.cache.CacheManager
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.GoogleCastStrategy
import com.raulshma.jellyplay.core.data.cast.dlna.DlnaCastStrategy
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntakeImpl
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioEffectsProcessor
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.focus.AndroidFocusArbiter
import com.raulshma.jellyplay.core.data.playback.focus.AudioPlaybackManagerSurface
import com.raulshma.jellyplay.core.data.playback.focus.DefaultPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.FocusArbiter
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.AudioPrefetchEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.AudioStreamCache
import com.raulshma.jellyplay.core.data.playback.AndroidPipController
import com.raulshma.jellyplay.core.data.playback.DefaultAudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.data.remote.AndroidAudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.AndroidVideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.AudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.remote.RemotePlaybackReporter
import com.raulshma.jellyplay.core.data.remote.VideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStoreImpl
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncSchedulerImpl
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * core-side Hilt extinction: Koin owns every remaining legacy
 * core:data singleton. The classes below are the Android-only remainder
 * (media3 audio playback stack, cast strategies, WorkManager schedulers and
 * reconnect listeners, receivers' helper graph) — their ctor shapes are
 * byte-identical to the old Hilt graph, with the two qualifier edges mapped
 * onto the shared modules' Koin qualifiers:
 *  - the old `@ApplicationScope CoroutineScope` →
 *    [DatastoreQualifiers.applicationScope] (datastoreCommonModule single);
 *  - the old `@Named("streaming")` / `@Named("download")` OkHttpClient →
 *    [NetworkQualifiers.streamingHttpClient] / [NetworkQualifiers.downloadHttpClient]
 *    (androidNetworkModule singles).
 *
 * Interface bindings mirror every former DataModule/SubtitleModule `@Binds`
 * (DownloadIntake, AudioQueueManager, TvWatchNextScheduler,
 * UserDataSyncScheduler, PlaybackSyncScheduler, StreamingSubtitleStore) plus
 * the AudioEffectsManager/AudioQueueFacade aliases the app interop layer used
 * to supply.
 *
 * A transitional Hilt bridge module briefly fed the still-Hilt :app
 * injectors from these singles; it died with the app Hilt extinction
 * — :app now resolves these directly.
 */
fun androidCoreDataModule(context: Context): Module = module {

    // ── Cross-player exclusivity (PlaybackFocus, ADR-0004) ─────────────
    single<FocusArbiter> { AndroidFocusArbiter(context.applicationContext) }
    single<PlaybackFocus> {
        DefaultPlaybackFocus(
            arbiter = get(),
            surfaces = listOf(AudioPlaybackManagerSurface(manager = lazy { get<AudioPlaybackManager>() })),
        )
    }
    // ── Playback stack (media3) ─────────────────────────────────────────
    single {
        AudioPlaybackManager(
            context = context,
            playbackFocus = get(),
            mediaRepository = get(),
            playlistRepository = get(),
            playbackRepository = get(),
            imageUrlProvider = get(),
            downloadRepository = get(),
            offlineRepository = get(),
            playbackSourceResolver = get(),
            sessionManager = get(),
            audioStore = get(),
            audioEffectsStore = get(),
            playbackStore = get(),
            queuePersistenceHelper = get(),
            bandwidthMonitor = get(),
            adaptiveBitrateSelector = get(),
            bandwidthInterceptor = get(),
            lyricsManager = get(),
            effectsProcessor = get(),
            sleepTimerManager = get(),
            jellyfinRemotePlayCastStrategy = get(),
            audioStreamCache = get(),
            audioPrefetchEngine = get(),
        )
    }
    // Former bindAudioQueueManager / AudioEffectsManager @Binds-style aliases:
    // the media3 manager implements the shared playback contracts — same
    // single. AudioPlayerEngine (the transport/metadata/lyrics half) moved
    // from player-audio into core/data commonMain, so the manager implements
    // it DIRECTLY — the app-side 36-member delegate died with the move.
    single<AudioQueueManager> { get<AudioPlaybackManager>() }
    single<AudioEffectsManager> { get<AudioPlaybackManager>() }
    single<AudioPlayerEngine> { get<AudioPlaybackManager>() }

    single {
        AudioStreamCache(
            context = context,
            streamingOkHttpClient = get(NetworkQualifiers.streamingHttpClient),
            audioCacheStore = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single { AudioEffectsProcessor() }
    single {
        AudioPrefetchEngine(
            audioStreamCache = get(),
            policyGuard = get(),
            playbackRepository = get(),
            audioCacheStore = get(),
            backgroundScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single { PlaybackSessionManager(context = context) }
    single {
        ThemeMusicPlayer(
            context = context,
            mediaRepository = get(),
            playbackRepository = get(),
            appearanceStore = get(),
        )
    }
    // The PiP state owner: concrete single (PlayerActivity injects the class)
    // plus the commonMain PipController port bound to the SAME instance —
    // both players resolve the port key, the Activity the concrete one.
    single { AndroidPipController() }
    single<PipController> { get<AndroidPipController>() }

    // Former provideAudioQueueFacade direct construction (the only real
    // provider left in the deleted DataModule): queue seam, never the manager.
    single<AudioQueueFacade> {
        DefaultAudioQueueFacade(
            queueManager = get(),
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }

    single { CacheManager(context = context, networkOfflineStore = get(), appScope = get(DatastoreQualifiers.applicationScope)) }

    // ── Remote-control / cast helper graph ──────────────────────────────
    // The receiver itself is a jvmShared type; the dispatchers are
    // bound through their commonMain interfaces (these Android impls marshal
    // to the main looper for the ExoPlayer-backed surfaces); the
    // ActivePlayerController registry single moved to dataJvmModule (both
    // JVM shells share it).
    single<VideoRemoteControlDispatcher> {
        AndroidVideoRemoteControlDispatcher(
            activePlayerController = get(),
            remoteNavigationBridge = get(),
        )
    }
    single<AudioRemoteControlDispatcher> {
        AndroidAudioRemoteControlDispatcher(
            audioPlaybackManager = get(),
            mediaRepository = get(),
            remoteNavigationBridge = get(),
        )
    }
    single {
        RemotePlaybackReporter(
            playbackRepository = get(),
            audioPlaybackManager = get(),
            authRepository = get(),
            activePlayerController = get(),
        )
    }
    single {
        RemoteControlReceiver(
            webSocketClient = get(),
            authRepository = get(),
            mediaRepository = get(),
            videoDispatcher = get(),
            audioDispatcher = get(),
            uiDispatcher = get(),
            activePlayerController = get(),
            securityStore = get(),
            remoteNavigationBridge = get<RemoteNavigationBridge>(),
            audioQueueManager = get<AudioQueueManager>(),
        )
    }

    single { GoogleCastStrategy(appContext = context) }
    single {
        DlnaCastStrategy(
            appContext = context,
            okHttpClient = get<OkHttpClient>(),
            appRuntimeStateStore = get(),
        )
    }
    single {
        JellyfinRemotePlayCastStrategy(
            appContext = context,
            adminApiClient = get(),
            serverIdentityStore = get(),
            webSocketClient = get(),
            imageUrlProvider = get(),
        )
    }
    single {
        CastManager(
            context = context,
            googleCastStrategy = get(),
            dlnaCastStrategy = get(),
            jellyfinRemotePlayCastStrategy = get(),
            syncPlayCastStore = get(),
        )
    }

    // ── Download intake / subtitle storage / shortcuts ──────────────────
    single {
        DownloadIntakeImpl(
            context = context,
            delegate = get(),
            downloadRepository = get(),
            mediaRepository = get(),
            downloadsStore = get(),
        )
    }
    single<DownloadIntake> { get<DownloadIntakeImpl>() }

    single {
        StreamingSubtitleStoreImpl(
            baseDir = context.filesDir,
            json = get(),
        )
    }
    single<StreamingSubtitleStore> { get<StreamingSubtitleStoreImpl>() }

    // The old ctor took dagger.Lazy<AudioPlaybackManager>; the kotlin Lazy
    // wrapper preserves the deferred construction (the media3 graph is only
    // touched on the first shortcut observation).
    single {
        AppShortcutManager(
            context = context,
            audioPlaybackManagerLazy = lazy { get<AudioPlaybackManager>() },
        )
    }

    // ── WorkManager schedulers + reconnect listeners ────────────────────
    single {
        AutoDownloadScheduler(
            context = context,
            downloadsStore = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single { TvWatchNextSchedulerImpl(context = context) }
    single<TvWatchNextScheduler> { get<TvWatchNextSchedulerImpl>() }
    single { UserDataSyncSchedulerImpl(context = context) }
    single<UserDataSyncScheduler> { get<UserDataSyncSchedulerImpl>() }
    single { PlaybackSyncSchedulerImpl(context = context) }
    single<PlaybackSyncScheduler> { get<PlaybackSyncSchedulerImpl>() }

    single {
        DownloadReconnectListener(
            networkMonitor = get(),
            offlineModeManager = get(),
            downloadRepository = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single {
        PlaybackSyncReconnectListener(
            networkMonitor = get(),
            offlineModeManager = get(),
            scheduler = get(),
            outbox = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
