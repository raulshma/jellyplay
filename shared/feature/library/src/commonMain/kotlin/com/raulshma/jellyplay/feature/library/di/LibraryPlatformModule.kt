package com.raulshma.jellyplay.feature.library.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the library feature's
 * [com.raulshma.jellyplay.feature.library.QuickDownloadActions] binding
 * (the same fragment shape the photo-export modules already use, composed
 * into libraryModule via [org.koin.core.module.Module.includes] so the app
 * composition roots keep registering the single `libraryModule`):
 *  - jvmShared actual: wraps the process-wide `MediaDownloadActions` single
 *    (dataJvmModule's binding) — android/desktop behavior unchanged;
 *  - wasmJs actual: the no-op actions (see the seam's KDoc for the web
 *    behavior).
 */
internal expect fun platformLibraryModule(): Module
