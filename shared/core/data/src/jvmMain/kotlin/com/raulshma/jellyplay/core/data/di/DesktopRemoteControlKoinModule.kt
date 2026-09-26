package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.remote.AudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.DesktopAudioRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.DesktopVideoRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.VideoRemoteControlDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

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
            audioQueueManager = get(),
        )
    }
}
