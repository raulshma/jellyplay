package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.cache.CacheManager
import com.raulshma.jellyplay.core.data.playback.AndroidPipController
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioEffectsProcessor
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioPrefetchEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.AudioStreamCache
import com.raulshma.jellyplay.core.data.playback.DefaultAudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The media3 playback-stack family of the androidCoreDataModule split (see
 * [androidCoreDataModule] for the construction-owner rules). Binding bodies
 * moved verbatim from the pre-split single-module layout.
 */
internal fun androidPlaybackStackModule(context: Context): Module = module {
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
}
