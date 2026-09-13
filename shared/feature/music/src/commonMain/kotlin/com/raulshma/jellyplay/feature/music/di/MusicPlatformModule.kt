package com.raulshma.jellyplay.feature.music.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the music feature's web seams
 * (the PlayerAudioPlatformModule fragment shape, composed into musicModule
 * via [org.koin.core.module.Module.includes] so the app composition roots
 * keep registering the single `musicModule`):
 *  - jvmShared actual: binds [com.raulshma.jellyplay.feature.music.JvmMusicQueuePlayer]
 *    and [com.raulshma.jellyplay.feature.music.JvmMusicTrackDownloads] over
 *    the existing `AudioQueueFacade` / `DownloadRepository` singles —
 *    android/desktop behavior unchanged;
 *  - wasmJs actual: binds the honest unsupported player and no-op downloads
 *    (see the seam KDocs). The rest of the VMs' graph (MediaRepository, the
 *    promoted playlist repositories, DownloadIntake, OfflineModeManager,
 *    HomeDiscoveryStore, ImageUrlProvider, MusicMessageBus) has no web
 *    binding — web wiring stays with the orchestrator's shared-wiring pass.
 */
internal expect fun platformMusicModule(): Module
