package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.cache.CacheManager
import com.raulshma.jellyplay.core.data.playback.AndroidPipController
import com.raulshma.jellyplay.core.data.playback.AmbientVideoPlayer
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioEffectsProcessor
import com.raulshma.jellyplay.core.data.playback.AudioLibraryBrowser
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioPrefetchEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.AudioStreamCache
import com.raulshma.jellyplay.core.data.playback.DefaultAudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.ExoAmbientVideoPlayer
import com.raulshma.jellyplay.core.data.playback.NowPlayingVerdict
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.ShellAudioSurface
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The media3 playback-stack family of the androidCoreDataModule split (see
 * [androidCoreDataModule] for the construction-owner rules). Binding bodies
 * moved verbatim from the pre-split single-module layout.
 */
internal fun androidPlaybackStackModule(context: Context): Module = module {
    // ── Playback stack (media3) ─────────────────────────────────────────
    // The library/browse ladder is DI-OWNED since the manager's constructor
    // diet: its five family deps (music catalogue / collection reads /
    // playlists / downloads / adaptive bitrate) resolve as the same singles
    // the manager used to forward, and the streaming-quality read rides the
    // store directly (the manager used to hand a lambda over its own
    // collect-cached playback slice — same value, read at the source).
    single {
        AudioLibraryBrowser(
            // The manager's default playback scope (SupervisorJob +
            // Main.immediate) — the scope the browser rode when the manager
            // constructed it internally. Deliberately NOT the application
            // scope (Dispatchers.Default): the browser resolves media3
            // session callbacks on it.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            mediaRepository = get(),
            libraryApiClient = get(),
            musicCatalogue = get(),
            playlistRepository = get(),
            downloadRepository = get(),
            playbackRepository = get(),
            imageUrlProvider = get(),
            playbackSourceResolver = get(),
            streamingQualityProvider = { get<PlaybackStore>().playback.value.streamingQuality },
            adaptiveBitrateSelector = get(),
            // Call-time facade access — a direct dep would be circular
            // (browser ← manager ← facade). Resolved only when a controller
            // (Android Auto) issues setMediaItems, well after construction.
            audioQueueFacadeProvider = { get<AudioQueueFacade>() },
        )
    }
    single {
        AudioPlaybackManager(
            context = context,
            playbackFocus = get(),
            mediaRepository = get(),
            libraryBrowser = get(),
            playbackRepository = get(),
            imageUrlProvider = get(),
            playbackSourceResolver = get(),
            sessionManager = get(),
            audioStore = get(),
            audioEffectsStore = get(),
            playbackStore = get(),
            queuePersistenceHelper = get(),
            bandwidthMonitor = get(),
            bandwidthInterceptor = get(),
            lyricsManager = get(),
            effectsProcessor = get(),
            sleepCountdown = get(),
            jellyfinRemotePlayCastStrategy = get(),
            audioStreamCache = get(),
            audioPrefetchEngine = get(),
            // The remote-session gate over the verdict's flag cell —
            // call-time resolution on purpose: the verdict ctor-injects
            // THIS manager as its audio face, so an eager edge would be a
            // construction cycle (same shape as the browser's facade
            // provider above). The gate is only read when the audio
            // progress loop starts, long after both singles exist.
            remoteSessionActive = { get<NowPlayingVerdict>().remoteSessionActive.value },
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
    // The app shell's narrowed metadata+transport surface (MainContent and
    // its layout/overlay branches) — same single, one more alias beside the
    // three above, so shell code resolves the interface and constructs
    // against a fake on the JVM.
    single<ShellAudioSurface> { get<AudioPlaybackManager>() }
    // The Now-playing verdict (GLOSSARY): one fold over the video registry
    // (commonMain, dataJvmModule) and this module's manager as the audio
    // face. Consumed by the remote-cast family's reporter + receiver, the
    // app's widget updater and the QS tile.
    single {
        NowPlayingVerdict(
            videoEngines = get(),
            audio = get<AudioPlaybackManager>(),
        )
    }

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
            musicCatalogue = get(),
            playbackRepository = get(),
            appearanceStore = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    // The ambient hero-clip transport (muted-by-contract, looping) —
    // one process-wide single so the home hero and the detail hero can never
    // both drive video (start implicitly stops the previous clip).
    single<AmbientVideoPlayer> { ExoAmbientVideoPlayer(context = context) }
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
            libraryApiClient = get(),
            imageUrlProvider = get(),
            radioScope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single { CacheManager(context = context, networkOfflineStore = get(), appScope = get(DatastoreQualifiers.applicationScope)) }
}
