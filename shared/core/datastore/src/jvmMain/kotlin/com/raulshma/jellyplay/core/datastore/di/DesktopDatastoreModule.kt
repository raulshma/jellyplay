package com.raulshma.jellyplay.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.DesktopSecureKeyValueStorage
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import org.koin.core.module.Module
import org.koin.dsl.module
import okio.Path

/**
 * Desktop platform Koin module (docs/kmp-migration-plan.md §Phase C4).
 * [dataDir] is the app's writable data directory; preference files land
 * under `dataDir/datastore/` mirroring the Android `filesDir/datastore/`
 * layout. Credential stores use the OS-keyring-backed
 * [DesktopSecureKeyValueStorage] with the documented
 * "JellyPlay/&lt;file&gt;" service namespacing so the three credential sets
 * stay isolated, matching the per-file isolation on Android.
 */
fun desktopDatastoreModule(dataDir: Path): Module = module {

    single(qualifier = DatastoreQualifiers.userPreferencesDataStore) {
        preferencesDataStore(dataDir, "user_prefs")
    }

    single(qualifier = DatastoreQualifiers.seerrPreferencesDataStore) {
        preferencesDataStore(dataDir, "seerr_prefs")
    }

    single(qualifier = DatastoreQualifiers.arrPreferencesDataStore) {
        preferencesDataStore(dataDir, "arr_prefs")
    }

    single(qualifier = DatastoreQualifiers.subtitleProviderPreferencesDataStore) {
        preferencesDataStore(dataDir, "subtitle_provider_prefs")
    }

    single {
        SeerrSecureCredentialsStore(
            DesktopSecureKeyValueStorage("JellyPlay/seerr_secure_prefs"),
        )
    }

    single {
        ArrSecureCredentialsStore(
            DesktopSecureKeyValueStorage("JellyPlay/arr_secure_prefs"),
        )
    }

    single {
        SubtitleProviderSecureCredentialsStore(
            DesktopSecureKeyValueStorage("JellyPlay/subtitle_provider_secure_prefs"),
        )
    }
}

private fun preferencesDataStore(dataDir: Path, name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath {
        dataDir / "datastore/$name.preferences_pb"
    }
