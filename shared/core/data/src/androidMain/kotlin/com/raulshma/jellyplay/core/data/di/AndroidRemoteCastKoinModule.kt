package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.GoogleCastStrategy
import com.raulshma.jellyplay.core.data.cast.dlna.DlnaCastStrategy
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.remote.AndroidAudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.AndroidVideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.AudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.remote.RemotePlaybackReporter
import com.raulshma.jellyplay.core.data.remote.VideoRemoteControlDispatcher
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The remote-control / cast helper-graph family of the androidCoreDataModule
 * split (see [androidCoreDataModule] for the construction-owner rules).
 * Binding bodies moved verbatim from the pre-split single-module layout.
 */
internal fun androidRemoteCastModule(context: Context): Module = module {
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
}
