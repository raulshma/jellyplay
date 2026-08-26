package com.raulshma.jellyplay.feature.player.audio.di

import com.raulshma.jellyplay.feature.player.audio.AudioPlayerViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the audio player (wave 7A conveyor move from
 * `:feature:player:audio`; docs/kmp-migration-plan.md §Phase V3). The
 * @HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). Ctor deps split four
 * ways:
 *  - [com.raulshma.jellyplay.core.data.playback.AudioQueueManager] /
 *    [com.raulshma.jellyplay.core.data.playback.AudioEffectsManager] are the
 *    shared playback contracts the legacy Hilt AudioPlaybackManager single
 *    implements; they reach Koin through the app's Hilt interop module until
 *    the manager flips (Phase X);
 *  - the module-local AudioPlayerEngine / AudioPlayerCast seams are bridged
 *    the same way (app-side lazy interop adapters over the Hilt-owned
 *    AudioPlaybackManager / CastManager — details DetailAudioPlayback
 *    precedent);
 *  - SleepTimerManager (shared core:data single), MediaRepository /
 *    UserDataMutator / DownloadRepository / DownloadIntake (shared data
 *    cluster) and PreferenceProjections / AudioStore / AudioEffectsStore
 *    (shared datastore) resolve from the shared-module graph;
 *  - desktop: this registration is LATENT — the four playback/cast deps above
 *    have no desktop definitions yet, so resolving the ViewModel there would
 *    throw NoDefinitionFound; Route.AudioPlayer/Route.Ambient stay guarded in
 *    DesktopAppRoot so nothing instantiates it (editor/home precedent).
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
            userDataMutator = get(),
            downloadRepository = get(),
            downloadIntake = get(),
            sleepTimerManager = get(),
        )
    }
}
