package com.raulshma.jellyplay.core.data.di

import org.koin.core.Koin
import org.koin.mp.KoinPlatform

/**
 * Accessor for the Koin container started by the app composition root.
 * startKoin runs before Application.super.onCreate(), so any module Koin
 * access for the worker factories resolves through here.
 */
internal fun koin(): Koin =
    KoinPlatform.getKoin()
        ?: error("Koin not started — startKoin must run before super.onCreate()")
