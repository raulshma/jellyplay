package com.raulshma.jellyplay.feature.player.audio.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the audio player's platform seams (the
 * QuickDownloadActions fragment shape, composed into playerAudioModule via
 * [org.koin.core.module.Module.includes] so the app composition roots keep
 * registering the single `playerAudioModule`):
 *  - jvmShared actual: empty — android/desktop resolve
 *    [com.raulshma.jellyplay.core.data.playback.AudioSleepTimerManager] from
 *    the existing dataJvmModule binding, and the player's download window
 *    ([com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow],
 *    which the former feature-local AudioTrackDownloads seam was folded
 *    onto) is core:data's own binding on both platforms — an extra
 *    definition here would only risk a duplicate-single clash.
 */
internal expect fun platformPlayerAudioModule(): Module
