package com.raulshma.jellyplay.feature.library.di

import com.raulshma.jellyplay.feature.library.JvmQuickDownloadActions
import com.raulshma.jellyplay.feature.library.QuickDownloadActions
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformLibraryModule(): Module = module {
    single<QuickDownloadActions> { JvmQuickDownloadActions(get()) }
}
