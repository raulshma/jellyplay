package com.raulshma.jellyplay.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the single process-wide `"user_prefs"` DataStore instance.
 *
 * Multiple stores ([com.raulshma.jellyplay.core.datastore.UserPreferencesStore],
 * [com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore],
 * [com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore],
 * [com.raulshma.jellyplay.core.datastore.security.PinRateLimiter]) read and write
 * non-overlapping key sets in this file. AndroidX DataStore forbids two
 * delegates for the same file in one process ("multiple DataStores active for
 * the same file" — throws at runtime), so the file is provided once here and
 * injected into each store rather than each declaring its own
 * `Context.xxxDataStore by preferencesDataStore(...)` delegate.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferencesDataStore

private val Context.userPreferencesDataStoreDelegate: DataStore<Preferences> by
    preferencesDataStore(name = "user_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @UserPreferencesDataStore
    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.userPreferencesDataStoreDelegate
}
