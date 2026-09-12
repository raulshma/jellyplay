package com.raulshma.jellyplay.feature.search.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the search feature's
 * [com.raulshma.jellyplay.feature.search.QuickDownloadActions] binding
 * (PhotoExport's androidPhotoExportModule/desktopPhotoExportModule shape,
 * composed into searchModule via [org.koin.core.module.Module.includes] so
 * the app composition roots keep registering the single `searchModule`):
 *  - jvmShared actual: wraps the process-wide `MediaDownloadActions` single
 *    (dataJvmModule's binding) — android/desktop behavior unchanged;
 *  - wasmJs actual: the no-op actions (see the seam's KDoc for the web
 *    behavior).
 */
internal expect fun platformSearchModule(): Module
