package com.raulshma.jellyplay.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.AndroidSecureKeyValueStorage
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import okio.Path.Companion.toPath

/**
 * Android platform Koin module (docs/kmp-migration-plan.md §Phase C4): the
 * per-file preference DataStores and the EncryptedSharedPreferences-backed
 * credential stores. Paths and file names are byte-for-byte the legacy Hilt
 * wiring so existing installs keep their data:
 * `filesDir/datastore/<name>.preferences_pb` and the same secure pref files.
 */
fun androidDatastoreModule(context: Context): Module = module {

    single(qualifier = DatastoreQualifiers.userPreferencesDataStore) {
        preferencesDataStore(context, "user_prefs")
    }

    single(qualifier = DatastoreQualifiers.seerrPreferencesDataStore) {
        preferencesDataStore(context, "seerr_prefs")
    }

    single(qualifier = DatastoreQualifiers.arrPreferencesDataStore) {
        preferencesDataStore(context, "arr_prefs")
    }

    single(qualifier = DatastoreQualifiers.subtitleProviderPreferencesDataStore) {
        preferencesDataStore(context, "subtitle_provider_prefs")
    }

    single {
        SeerrSecureCredentialsStore(
            AndroidSecureKeyValueStorage(context, "seerr_secure_prefs"),
        )
    }

    single {
        ArrSecureCredentialsStore(
            AndroidSecureKeyValueStorage(context, "arr_secure_prefs"),
        )
    }

    single {
        SubtitleProviderSecureCredentialsStore(
            AndroidSecureKeyValueStorage(context, "subtitle_provider_secure_prefs"),
        )
    }
}

private fun preferencesDataStore(context: Context, name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath {
        File(context.filesDir, "datastore/$name.preferences_pb").absolutePath.toPath()
    }
