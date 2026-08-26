package com.raulshma.jellyplay.core.ui.di

import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wave 8A core-side Hilt extinction: Koin owns the legacy :core:ui
 * singletons. Currently just the app-wide [UserMessageBus] (a context-free
 * buffered channel — no platform seams needed, hence the context-less val).
 * The still-Hilt :app injectors ride the app-side transitional
 * LegacyHiltBridgesModule until the app Hilt extinction.
 */
val androidCoreUiModule: Module = module {
    single { UserMessageBus() }
}
