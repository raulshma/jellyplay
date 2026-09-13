package com.raulshma.jellyplay.feature.syncplay.di

import com.raulshma.jellyplay.feature.syncplay.JvmSyncPlaySession
import com.raulshma.jellyplay.feature.syncplay.SyncPlaySession
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformSyncPlayModule(): Module = module {
    // The VM resolves `SyncPlaySession` via get() — the adapter delegates to
    // the process-wide SyncPlayManager single (dataJvmModule binding).
    single<SyncPlaySession> { JvmSyncPlaySession(get()) }
}
