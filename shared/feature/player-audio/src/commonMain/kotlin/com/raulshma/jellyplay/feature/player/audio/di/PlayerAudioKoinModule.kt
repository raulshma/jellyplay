package com.raulshma.jellyplay.feature.player.audio.di

import com.raulshma.jellyplay.feature.player.audio.AudioPlayerViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the audio player (conveyor move from
 * `:feature:player:audio`; docs/kmp-migration-plan.md ). The
 * HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). Ctor deps split four
 * ways:
 *  - [com.raulshma.jellyplay.core.data.playback.AudioQueueManager] /
 *    [com.raulshma.jellyplay.core.data.playback.AudioEffectsManager] are the
 *    shared playback contracts the legacy Hilt AudioPlaybackManager single
 *    implements; androidCoreDataModule aliases them onto that manager since
 *    the former app Hilt-interop bridge died with the conveyor move);
 *  - [com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine] is the
 *    third shared playback contract (the transport/metadata/lyrics half;
 *    the Android manager implements it directly and androidCoreDataModule
 *    aliases it onto that single, same as the queue/effects pair) and the
 *    module-local AudioPlayerCast seam is bridged app-side
 *    (`androidAppInteropAdaptersModule` adapter over the Koin-owned
 *    CastManager — details DetailAudioPlayback precedent);
 *  - AudioSleepTimerManager (dataJvmModule aliases the interface onto the
 *    SleepTimerManager single),
 *    MediaRepository /
 *    UserDataMutator (shared data
 *    cluster) and the download window TrackDownloadStatusWindow (core:data's
 *    own seam since the download-actions consolidation — jvmShared adapter
 *    over the DownloadRepository single in dataJvmModule) plus PreferenceProjections / AudioStore /
 *    AudioEffectsStore (shared datastore) resolve from the shared-module
 *    graph;
 *  - TrackDownloadActions (the track-download flip collaborator over the
 *    intake + window) is defined HERE, factory-per-resolution: its scope is
 *    the DatastoreQualifiers application scope — the MediaDownloadActions
 *    precedent in dataJvmModule (Koin has no handle on a ViewModel's
 *    viewModelScope, and a download start outliving the screen it was
 *    tapped on is the wanted semantics anyway);
 *  - desktop: LIVE since the real-audio engine — apps/desktop's
 *    desktopPlayerModule binds all four playback/cast deps:
 *    [com.raulshma.jellyplay.core.data.playback.AudioQueueManager] +
 *    [AudioPlayerEngine] over the shared DesktopAudioQueueManager single
 *    (audio-only MpvDesktopEngine behind it), state-only
 *    DesktopAudioEffectsManager, never-connected DesktopAudioPlayerCast —
 *    so this registration is live-resolvable there too and Route.AudioPlayer
 *    opens the real now-playing screen.
 */
val playerAudioModule: Module = module {
    includes(platformPlayerAudioModule())

    // The track-download flip collaborator (audio player's download CTA):
    // deps are the shared-cluster DownloadIntake + TrackDownloadStatusWindow
    // singles; the scope follows the MediaDownloadActions precedent (see the
    // module kdoc). Factory — one instance per resolving ViewModel.
    factory {
        com.raulshma.jellyplay.core.data.download.TrackDownloadActions(
            scope = get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.applicationScope),
            intake = get(),
            statusWindow = get(),
        )
    }

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
            downloads = get(),
            trackDownloadActions = get(),
            sleepTimerManager = get(),
        )
    }
}
