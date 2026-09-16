package com.raulshma.jellyplay.feature.library.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the library feature's
 * [com.raulshma.jellyplay.core.data.download.QuickDownloadActions] binding
 * (the same fragment shape the photo-export modules already use, composed
 * into libraryModule via [org.koin.core.module.Module.includes] so the app
 * composition roots keep registering the single `libraryModule`). The seam
 * itself was hoisted to core:data (the feature's internal twin deleted):
 *  - jvmShared actual: empty — the binding lives in DataKoinModule
 *    (JvmQuickDownloadActions over the process-wide `MediaDownloadActions`
 *    single) — android/desktop behavior unchanged;
 *  - wasmJs actual: binds core:data's no-op [com.raulshma.jellyplay.core.data.download.WasmQuickDownloadActions]
 *    (see the seam's KDoc for the web behavior).
 */
internal expect fun platformLibraryModule(): Module
