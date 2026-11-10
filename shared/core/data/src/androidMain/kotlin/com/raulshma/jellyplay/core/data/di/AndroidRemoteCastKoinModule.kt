package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.GoogleCastIntegration
import com.raulshma.jellyplay.core.data.cast.dlna.DlnaCastStrategy
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.data.playback.AudioRemoteControlSurface
import com.raulshma.jellyplay.core.data.remote.AndroidAudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.AndroidVideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.AudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.remote.RemotePlaybackReporter
import com.raulshma.jellyplay.core.data.remote.VideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The remote-control / cast helper-graph family of the androidCoreDataModule
 * split (see [androidCoreDataModule] for the construction-owner rules).
 * Binding bodies moved verbatim from the pre-split single-module layout —
 * except the GMS cast family: GoogleCastStrategy and GoogleCastIntegration
 * are store-only (bound in the app's store DistributionPlatform module, so
 * Play services never reach the fdroid classpath), and [CastManager] takes
 * the integration via getOrNull with the GoogleCastIntegration.Unavailable
 * fdroid fallback.
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
            // The Audio shell seam: the dispatcher reads only the
            // AudioRemoteControlSurface face of the manager single.
            audioPlaybackManager = get<AudioRemoteControlSurface>(),
            detailFeed = get(),
            remoteNavigationBridge = get(),
        )
    }
    single {
        RemotePlaybackReporter(
            playbackRepository = get(),
            nowPlayingVerdict = get(),
            authRepository = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single {
        RemoteControlReceiver(
            webSocketClient = get(),
            authRepository = get(),
            detailFeed = get(),
            videoDispatcher = get(),
            audioDispatcher = get(),
            uiDispatcher = get(),
            activePlayerController = get(),
            securityStore = get(),
            remoteNavigationBridge = get<RemoteNavigationBridge>(),
            nowPlayingVerdict = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }

    // No GoogleCastStrategy/GoogleCastIntegration binding here: the GMS cast
    // stack is store-only (the bindings live in the app's store Distribution
    // Platform module), so core:data must compile — and this graph resolve —
    // without Play services on the classpath. getOrNull resolves null on
    // fdroid and the manager binds GoogleCastIntegration.Unavailable.
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
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single {
        CastManager(
            context = context,
            googleCastIntegration = getOrNull<GoogleCastIntegration>() ?: GoogleCastIntegration.Unavailable,
            dlnaCastStrategy = get(),
            jellyfinRemotePlayCastStrategy = get(),
            syncPlayCastStore = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
