package com.raulshma.jellyplay.core.data.di

import org.koin.core.Koin
import org.koin.mp.KoinPlatform

/**
 * Accessor for the Koin container started by the app composition root
 * (Phase C4). startKoin runs before Application.super.onCreate(), so any
 * Hilt provider that bridges into Koin resolves here safely.
 */
internal fun koin(): Koin =
    KoinPlatform.getKoin()
        ?: error("Koin not started — startKoin must run before super.onCreate()")
