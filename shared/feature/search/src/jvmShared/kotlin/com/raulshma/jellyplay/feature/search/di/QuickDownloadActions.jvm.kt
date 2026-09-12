package com.raulshma.jellyplay.feature.search.di

import com.raulshma.jellyplay.feature.search.JvmQuickDownloadActions
import com.raulshma.jellyplay.feature.search.QuickDownloadActions
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformSearchModule(): Module = module {
    single<QuickDownloadActions> { JvmQuickDownloadActions(get()) }
}
