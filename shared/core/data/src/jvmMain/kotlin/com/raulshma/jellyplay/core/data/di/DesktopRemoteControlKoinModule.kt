package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.remote.AudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.DesktopAudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.DesktopVideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.VideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.data.playback.NowPlayingVerdict
import org.koin.core.module.Module
import org.koin.dsl.module
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers

/**
 * Remote-control family of the desktopDataModule split: the desktop
 * dispatcher twins and the jvmShared receiver over them. Binding bodies
 * moved verbatim from the pre-split single-module layout — see
 * [desktopDataModule] for the aggregate and the family map.
 */
internal val desktopRemoteControlModule: Module = module {
    // ── Remote-control receiver (desktop port) ────────────────
    // The jvmShared receiver over the desktop dispatcher twins. The
    // audio-core ports (AudioQueueManager / AudioPlayerEngine) resolve
    // lazily from apps/desktop's desktopPlayerModule single (the one
    // DesktopAudioQueueManager), so nothing here constructs eagerly.
    single<VideoRemoteControlDispatcher> {
        DesktopVideoRemoteControlDispatcher(
            activePlayerController = get(),
            remoteNavigationBridge = get(),
        )
    }
    single<AudioRemoteControlDispatcher> {
        DesktopAudioRemoteControlDispatcher(
            audioQueueManager = get(),
            audioPlayerEngine = get(),
            mediaRepository = get(),
            remoteNavigationBridge = get(),
        )
    }
    // The Now-playing verdict's desktop half: the video registry (common,
    // dataJvmModule) over the desktop manager as the audio face (its
    // AudioNowPlayingFace members are the same re-exposed chassis flows the
    // dispatcher twins read). The concrete manager single lives in
    // apps/desktop's desktopPlayerModule — resolved lazily like the ports
    // above, so this definition stays inert until the receiver asks.
    single {
        NowPlayingVerdict(
            videoEngines = get(),
            audio = get<DesktopAudioQueueManager>(),
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
            remoteNavigationBridge = get(),
            nowPlayingVerdict = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
