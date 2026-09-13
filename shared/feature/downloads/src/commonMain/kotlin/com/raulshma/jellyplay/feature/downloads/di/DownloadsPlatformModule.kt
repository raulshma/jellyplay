package com.raulshma.jellyplay.feature.downloads.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the downloads feature's web seams
 * (the PlayerAudioPlatformModule fragment shape, composed into
 * downloadsModule via [org.koin.core.module.Module.includes] so the app
 * composition roots keep registering the single `downloadsModule`):
 *  - jvmShared actual: binds [com.raulshma.jellyplay.feature.downloads.JvmDownloadQueue]
 *    and [com.raulshma.jellyplay.feature.downloads.JvmOfflineResync] over the
 *    existing `DownloadRepository` / `OfflineSyncManager` singles —
 *    android/desktop behavior unchanged;
 *  - wasmJs actual: binds the honest empty queue and idle resync (see the
 *    seam KDocs). The rest of the VMs' graph (OfflineRepository,
 *    UserDataMutator) resolves from the shared-module graph on all targets.
 */
internal expect fun platformDownloadsModule(): Module
