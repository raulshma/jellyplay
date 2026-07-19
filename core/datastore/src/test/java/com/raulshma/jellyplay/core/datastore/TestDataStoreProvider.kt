package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Provides the single shared `"user_prefs"` DataStore instance for datastore
 * module tests. AndroidX DataStore throws at runtime if two delegates resolve
 * to the same `.preferences_pb` file in one process, so tests cannot construct
 * each store with its own delegate — they must share one.
 *
 * The delegate lives in `di.UserPreferencesDataStore` (main source) but is
 * `private`; for tests we re-declare it here under a distinct name so both
 * the test and the main `DataStoreModule.provideUserPreferencesDataStore`
 * resolve to the same file (AndroidX guarantees one instance per
 * `(applicationContext, name)` pair).
 */
object TestDataStoreProvider {
    private val Context.testUserPrefsDataStore: DataStore<Preferences> by
        androidx.datastore.preferences.preferencesDataStore(name = "user_prefs")

    fun get(context: Context): DataStore<Preferences> =
        context.applicationContext.testUserPrefsDataStore
}
