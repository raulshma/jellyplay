package com.raulshma.jellyplay.feature.player.audio.di

import com.raulshma.jellyplay.feature.player.audio.AudioPlayerViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the audio player (wave 7A conveyor move from
 * `:feature:player:audio`; docs/kmp-migration-plan.md §Phase V3). The
 * HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). Ctor deps split four
 * ways:
 *  - [com.raulshma.jellyplay.core.data.playback.AudioQueueManager] /
 *    [com.raulshma.jellyplay.core.data.playback.AudioEffectsManager] are the
 *    shared playback contracts the legacy Hilt AudioPlaybackManager single
 *    implements; androidCoreDataModule aliases them onto that manager since
 *    wave 8A (the former app Hilt-interop bridge died with wave 8B);
 *  - the module-local AudioPlayerEngine / AudioPlayerCast seams are bridged
 *    the same way (app-side `androidAppInteropAdaptersModule` delegate
 *    adapters over the Koin-owned AudioPlaybackManager / CastManager —
 *    details DetailAudioPlayback precedent);
 *  - SleepTimerManager (shared core:data single), MediaRepository /
 *    UserDataMutator / DownloadRepository / DownloadIntake (shared data
 *    cluster) and PreferenceProjections / AudioStore / AudioEffectsStore
 *    (shared datastore) resolve from the shared-module graph;
 *  - desktop: LIVE since wave 9B real audio — apps/desktop's
 *    desktopPlayerModule binds all four playback/cast deps:
 *    [com.raulshma.jellyplay.core.data.playback.AudioQueueManager] +
 *    [AudioPlayerEngine] over the shared DesktopAudioQueueManager single
 *    (audio-only MpvDesktopEngine behind it), state-only
 *    DesktopAudioEffectsManager, never-connected DesktopAudioPlayerCast —
 *    so this registration is live-resolvable there too and Route.AudioPlayer
 *    opens the real now-playing screen.
 */
val playerAudioModule: Module = module {
    viewModel {
        AudioPlayerViewModel(
            queueManager = get(),
            effectsManager = get(),
            engine = get(),
            cast = get(),
            projections = get(),
            audioStore = get(),
            audioEffectsStore = get(),
            mediaRepository = get(),
            playlistRepository = get(),
            userDataMutator = get(),
            downloadRepository = get(),
            downloadIntake = get(),
            sleepTimerManager = get(),
        )
    }
}
