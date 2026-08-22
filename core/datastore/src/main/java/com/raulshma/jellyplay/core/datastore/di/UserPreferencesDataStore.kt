package com.raulshma.jellyplay.core.datastore.di

import javax.inject.Qualifier

/**
 * Qualifier for the single process-wide `"user_prefs"` DataStore instance.
 *
 * Multiple stores ([com.raulshma.jellyplay.core.datastore.UserPreferencesStore],
 * [com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore],
 * [com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore],
 * [com.raulshma.jellyplay.core.datastore.security.PinRateLimiter]) read and write
 * non-overlapping key sets in this file. AndroidX DataStore forbids two
 * delegates for the same file in one process ("multiple DataStores active for
 * the same file" — throws at runtime), so the file is provided once and
 * injected into each store rather than each declaring its own
 * `Context.xxxDataStore by preferencesDataStore(...)` delegate.
 *
 * Phase C4 (docs/kmp-migration-plan.md): Koin owns the instance
 * (`androidDatastoreModule`'s DatastoreQualifiers.userPreferencesDataStore
 * single, created via PreferenceDataStoreFactory at
 * `filesDir/datastore/user_prefs.preferences_pb` — the exact path the former
 * Context delegate used). The delegate and its Hilt provider were removed at
 * the flip so no second DataStore can ever activate on the same file.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferencesDataStore
