package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.seerrDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_prefs")

@Singleton
class SeerrPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("seerr_server_url")
        val API_KEY = stringPreferencesKey("seerr_api_key")
        val ENABLED = booleanPreferencesKey("seerr_enabled")
        val SEARCH_ENABLED = booleanPreferencesKey("seerr_search_enabled")
        val RECOMMENDATIONS_ENABLED = booleanPreferencesKey("seerr_recommendations_enabled")
    }

    val preferences: Flow<SeerrPreferences> = context.seerrDataStore.data.map { prefs ->
        SeerrPreferences(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            apiKey = prefs[Keys.API_KEY] ?: "",
            enabled = prefs[Keys.ENABLED] ?: false,
            searchEnabled = prefs[Keys.SEARCH_ENABLED] ?: false,
            recommendationsEnabled = prefs[Keys.RECOMMENDATIONS_ENABLED] ?: false,
        )
    }

    val isConnected: Flow<Boolean> = preferences.map { it.serverUrl.isNotBlank() && it.apiKey.isNotBlank() }

    suspend fun setServerUrl(url: String) {
        context.seerrDataStore.edit { it[Keys.SERVER_URL] = url.trim() }
    }

    suspend fun setApiKey(key: String) {
        context.seerrDataStore.edit { it[Keys.API_KEY] = key.trim() }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setSearchEnabled(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.SEARCH_ENABLED] = enabled }
    }

    suspend fun setRecommendationsEnabled(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.RECOMMENDATIONS_ENABLED] = enabled }
    }

    suspend fun disconnect() {
        context.seerrDataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.API_KEY)
            prefs[Keys.ENABLED] = false
            prefs[Keys.SEARCH_ENABLED] = false
            prefs[Keys.RECOMMENDATIONS_ENABLED] = false
        }
    }
}
