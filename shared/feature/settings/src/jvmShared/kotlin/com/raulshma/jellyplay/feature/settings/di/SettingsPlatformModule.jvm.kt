package com.raulshma.jellyplay.feature.settings.di

import com.raulshma.jellyplay.feature.settings.JvmServerAdminActions
import com.raulshma.jellyplay.feature.settings.ServerAdminActions
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformSettingsModule(): Module = module {
    single<ServerAdminActions> { JvmServerAdminActions(get()) }
}
