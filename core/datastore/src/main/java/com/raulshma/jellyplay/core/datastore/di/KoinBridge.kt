package com.raulshma.jellyplay.core.datastore.di

import org.koin.core.Koin
import org.koin.mp.KoinPlatform

/**
 * Phase C4 bridge (docs/kmp-migration-plan.md §Phase C4): Hilt providers in
 * this shim delegate to the Koin container started by the app entry point.
 * Throws "Koin not started — startKoin must run before super.onCreate()" if
 * the container has not been started yet.
 */
internal fun koin(): Koin = KoinPlatform.getKoin()
