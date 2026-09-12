package com.raulshma.jellyplay.core.notification.di

import org.koin.core.Koin
import org.koin.mp.KoinPlatform

/**
 * Accessor for the Koin container started by the app composition root —
 * same shape as the core:data di/KoinBridge.kt helper.
 */
internal fun koin(): Koin =
    KoinPlatform.getKoin()
        ?: error("Koin not started — startKoin must run before super.onCreate()")
