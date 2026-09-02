package com.raulshma.jellyplay.core.ui.di

import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin owner for the shared user-message stack (v0.10.6 merge): the
 * [UserMessageBus] single the root host collects for Snackbar/Toast
 * rendering, and which the migrated ViewModels (home, player session
 * manager, library) receive via `get()`.
 */
val coreUiMessageModule: Module = module {
    single { UserMessageBus() }
}
