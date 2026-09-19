package com.raulshma.jellyplay.feature.home.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the home feature's web seams (the
 * PlayerAudioPlatformModule fragment shape, composed into homeModule via
 * [org.koin.core.module.Module.includes] so the app composition roots keep
 * registering the single `homeModule`):
 *  - jvmShared actual: binds the adapters over the existing core:data
 *    jvmShared singles (TimeSource, SyncStatusStateHolderFactory + its
 *    collaborator single, NewsletterTriggerManager) — android/desktop
 *    behavior unchanged;
 *  - wasmJs actual: binds the honest web actuals (wall clock, idle sync
 *    status, no newsletter gate — see the seam KDocs). The rest of the VM's
 *    graph (MediaRepository, the promoted cluster, datastore stores,
 *    SessionIdentityProvider) resolves from the shared-module graph on all
 *    targets; the download reads (QuickDownloadActions,
 *    SeriesEpisodeDownloads) resolve from core:data's own seams on both
 *    platforms — dataJvmModule here, dataWasmModule on web; web routing
 *    stays with the orchestrator's integration pass.
 */
internal expect fun platformHomeModule(): Module
