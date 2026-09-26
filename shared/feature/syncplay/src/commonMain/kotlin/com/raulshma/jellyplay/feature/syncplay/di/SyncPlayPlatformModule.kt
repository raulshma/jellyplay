package com.raulshma.jellyplay.feature.syncplay.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the [com.raulshma.jellyplay.feature.syncplay.SyncPlaySession]
 * seam (the PlayerAudioPlatformModule fragment shape, composed into
 * syncPlayModule via [org.koin.core.module.Module.includes] so the app
 * composition roots keep registering the single `syncPlayModule`):
 *  - jvmShared actual: binds [com.raulshma.jellyplay.feature.syncplay.JvmSyncPlaySession]
 *    over the existing `SyncPlayManager` single from dataJvmModule —
 *    android/desktop behavior unchanged. The rest of the VM's graph
 *    (SyncPlayRepository, SyncPlayCastStore)
 *    resolves from the shared-module graph on all targets.
 */
internal expect fun platformSyncPlayModule(): Module
