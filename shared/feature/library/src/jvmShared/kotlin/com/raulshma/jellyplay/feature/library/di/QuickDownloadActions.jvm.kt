package com.raulshma.jellyplay.feature.library.di

import org.koin.core.module.Module
import org.koin.dsl.module

// The QuickDownloadActions binding moved to core:data's DataKoinModule
// (JvmQuickDownloadActions over the MediaDownloadActions single) with the
// seam hoist — this fragment stays so commonMain's
// includes(platformLibraryModule()) keeps a wasmJs counterpart to merge with.
internal actual fun platformLibraryModule(): Module = module {
}
