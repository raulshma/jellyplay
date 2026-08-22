package com.raulshma.jellyplay.core.datastore.di

import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named

/**
 * Koin qualifiers mirroring the legacy javax.inject qualifiers of the Android
 * shim one-to-one (docs/kmp-migration-plan.md §Phase C4). Cross-module: the
 * shared database/network Koin modules and the Android Hilt bridges resolve
 * the application scope through [applicationScope].
 */
object DatastoreQualifiers {

    /** javax @ApplicationScope — the process-wide CoroutineScope. */
    val applicationScope: Qualifier = named("applicationScope")

    /** javax @UserPreferencesDataStore — the "user_prefs" DataStore file. */
    val userPreferencesDataStore: Qualifier = named("user_prefs")

    /** javax @SeerrPreferencesDataStore — the "seerr_prefs" DataStore file. */
    val seerrPreferencesDataStore: Qualifier = named("seerr_prefs")

    /** javax @ArrPreferencesDataStore — the "arr_prefs" DataStore file. */
    val arrPreferencesDataStore: Qualifier = named("arr_prefs")

    /** javax @SubtitleProviderPreferencesDataStore — the "subtitle_provider_prefs" DataStore file. */
    val subtitleProviderPreferencesDataStore: Qualifier = named("subtitle_provider_prefs")
}
