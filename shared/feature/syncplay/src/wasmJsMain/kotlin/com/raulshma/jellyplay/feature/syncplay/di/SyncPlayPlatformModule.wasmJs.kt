package com.raulshma.jellyplay.feature.syncplay.di

import com.raulshma.jellyplay.feature.syncplay.SyncPlaySession
import com.raulshma.jellyplay.feature.syncplay.WasmSyncPlaySession
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformSyncPlayModule(): Module = module {
    single<SyncPlaySession> { WasmSyncPlaySession }
}
