package com.raulshma.jellyplay.feature.player.audio.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the audio player's web seams (the
 * QuickDownloadActions fragment shape, composed into playerAudioModule via
 * [org.koin.core.module.Module.includes] so the app composition roots keep
 * registering the single `playerAudioModule`):
 *  - jvmShared actual: empty — android/desktop resolve
 *    [com.raulshma.jellyplay.core.data.playback.AudioSleepTimerManager] from
 *    the existing dataJvmModule binding, and the player's download window
 *    ([com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow],
 *    which the former feature-local AudioTrackDownloads seam was folded
 *    onto) is core:data's own binding on both platforms — an extra
 *    definition here would only risk a duplicate-single clash;
 *  - wasmJs actual: binds the wall-clock sleep-timer impl (the download
 *    window's honest no-op actual lives in core:data's dataWasmModule). The
 *    rest of the VM's graph (queue/effects managers, repositories) has no
 *    web binding — web wiring stays with the orchestrator's shared-wiring
 *    pass.
 */
internal expect fun platformPlayerAudioModule(): Module
