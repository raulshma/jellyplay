package com.raulshma.jellyplay.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SecureKeyValueStorage
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import okio.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The JVM platform DataStore module both shells fold onto: the four
 * qualifier-bound per-file preference DataStores (`user_prefs`,
 * `seerr_prefs`, `arr_prefs`, `subtitle_provider_prefs`) plus the three
 * secure credential stores — the definitions the former
 * `AndroidDatastoreModule` / `DesktopDatastoreModule` hand-copied, differing
 * ONLY in where the preference directory roots and how a secure-storage
 * backend is constructed.
 *
 * Each shell supplies its two lambdas:
 *  - [prefsDir] — the directory `<name>.preferences_pb` files land under
 *    (Android: `filesDir/datastore`; desktop: `dataDir/datastore`, mirroring
 *    the Android layout). Android's `File`→okio `Path` translation stays in
 *    ITS lambda — the paths are byte-for-byte the legacy Hilt wiring so
 *    existing installs keep their data (install-compatibility promise).
 *  - [secureStorage] — constructs the platform secure backend for a
 *    credential-store file name. Android prefixes nothing
 *    (`seerr_secure_prefs`, `arr_secure_prefs`,
 *    `subtitle_provider_secure_prefs`); desktop namespaces under
 *    `"JellyPlay/<name>"` so the three credential sets stay isolated in the
 *    OS keyring, matching the per-file isolation on Android.
 */
fun datastorePlatformModule(
    prefsDir: () -> Path,
    secureStorage: (name: String) -> SecureKeyValueStorage,
): Module = module {

    single(qualifier = DatastoreQualifiers.userPreferencesDataStore) {
        preferencesDataStore(prefsDir, "user_prefs")
    }

    single(qualifier = DatastoreQualifiers.seerrPreferencesDataStore) {
        preferencesDataStore(prefsDir, "seerr_prefs")
    }

    single(qualifier = DatastoreQualifiers.arrPreferencesDataStore) {
        preferencesDataStore(prefsDir, "arr_prefs")
    }

    single(qualifier = DatastoreQualifiers.subtitleProviderPreferencesDataStore) {
        preferencesDataStore(prefsDir, "subtitle_provider_prefs")
    }

    single {
        SeerrSecureCredentialsStore(
            secureStorage("seerr_secure_prefs"),
        )
    }

    single {
        ArrSecureCredentialsStore(
            secureStorage("arr_secure_prefs"),
        )
    }

    single {
        SubtitleProviderSecureCredentialsStore(
            secureStorage("subtitle_provider_secure_prefs"),
        )
    }
}

private fun preferencesDataStore(prefsDir: () -> Path, name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath {
        prefsDir() / "$name.preferences_pb"
    }
