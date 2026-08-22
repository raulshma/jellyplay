package com.raulshma.jellyplay.core.network.di

/**
 * Accessor for the process-wide Koin instance backing the Hilt bridges in
 * this shim (Phase C4): Hilt remains the injector for legacy consumers, but
 * every network type is now CONSTRUCTED by Koin — the @Provides functions in
 * [NetworkModule] only fetch the Koin singleton, so there is exactly one
 * instance of each type per framework boundary.
 */
internal fun koin(): org.koin.core.Koin =
    org.koin.mp.KoinPlatform.getKoin()
        ?: error("Koin not started — startKoin must run before super.onCreate()")
