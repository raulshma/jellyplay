package com.raulshma.jellyplay.feature.library.di

import com.raulshma.jellyplay.core.data.download.QuickDownloadActions
import com.raulshma.jellyplay.core.data.download.WasmQuickDownloadActions
import org.koin.core.module.Module
import org.koin.dsl.module

// The wasmJs actual of the quick-download seam: the honest no-op stub
// hoisted to core:data (see WasmQuickDownloadActions' KDoc for the web
// behavior). The JVM binding lives in DataKoinModule instead.
internal actual fun platformLibraryModule(): Module = module {
    single<QuickDownloadActions> { WasmQuickDownloadActions }
}
