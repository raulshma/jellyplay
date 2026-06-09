package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.seerrDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_prefs")

@Singleton
class SeerrPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val SERVER_URL = stringPreferencesKey("seerr_server_url")
        val API_KEY = stringPreferencesKey("seerr_api_key")
        val ENABLED = booleanPreferencesKey("seerr_enabled")
        val SEARCH_ENABLED = booleanPreferencesKey("seerr_search_enabled")
        val RECOMMENDATIONS_ENABLED = booleanPreferencesKey("seerr_recommendations_enabled")
        val DISCOVER_ENABLED = booleanPreferencesKey("seerr_discover_enabled")
        val DISCOVER_TRENDING = booleanPreferencesKey("seerr_discover_trending")
        val DISCOVER_POPULAR_MOVIES = booleanPreferencesKey("seerr_discover_popular_movies")
        val DISCOVER_POPULAR_TV = booleanPreferencesKey("seerr_discover_popular_tv")
        val DISCOVER_UPCOMING_MOVIES = booleanPreferencesKey("seerr_discover_upcoming_movies")
        val DISCOVER_UPCOMING_TV = booleanPreferencesKey("seerr_discover_upcoming_tv")
        val STREAMING_REGION = stringPreferencesKey("seerr_streaming_region")
        val DISCOVER_REGION = stringPreferencesKey("seerr_discover_region")
    }

    val preferences: StateFlow<SeerrPreferences> = context.seerrDataStore.data
        .catch { _ -> emit(emptyPreferences()) }
        .map { prefs ->
            SeerrPreferences(
                serverUrl = prefs[Keys.SERVER_URL] ?: "",
                apiKey = prefs[Keys.API_KEY] ?: "",
                enabled = prefs[Keys.ENABLED] ?: false,
                searchEnabled = prefs[Keys.SEARCH_ENABLED] ?: false,
                recommendationsEnabled = prefs[Keys.RECOMMENDATIONS_ENABLED] ?: false,
                discoverEnabled = prefs[Keys.DISCOVER_ENABLED] ?: false,
                discoverTrending = prefs[Keys.DISCOVER_TRENDING] ?: true,
                discoverPopularMovies = prefs[Keys.DISCOVER_POPULAR_MOVIES] ?: true,
                discoverPopularTv = prefs[Keys.DISCOVER_POPULAR_TV] ?: true,
                discoverUpcomingMovies = prefs[Keys.DISCOVER_UPCOMING_MOVIES] ?: true,
                discoverUpcomingTv = prefs[Keys.DISCOVER_UPCOMING_TV] ?: true,
                streamingRegion = prefs[Keys.STREAMING_REGION] ?: "US",
                discoverRegion = prefs[Keys.DISCOVER_REGION] ?: "US",
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, SeerrPreferences())

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

    suspend fun setDiscoverEnabled(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.DISCOVER_ENABLED] = enabled }
    }

    suspend fun setDiscoverTrending(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.DISCOVER_TRENDING] = enabled }
    }

    suspend fun setDiscoverPopularMovies(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.DISCOVER_POPULAR_MOVIES] = enabled }
    }

    suspend fun setDiscoverPopularTv(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.DISCOVER_POPULAR_TV] = enabled }
    }

    suspend fun setDiscoverUpcomingMovies(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.DISCOVER_UPCOMING_MOVIES] = enabled }
    }

    suspend fun setDiscoverUpcomingTv(enabled: Boolean) {
        context.seerrDataStore.edit { it[Keys.DISCOVER_UPCOMING_TV] = enabled }
    }

    suspend fun setStreamingRegion(region: String) {
        context.seerrDataStore.edit { it[Keys.STREAMING_REGION] = region }
    }

    suspend fun setDiscoverRegion(region: String) {
        context.seerrDataStore.edit { it[Keys.DISCOVER_REGION] = region }
    }

    suspend fun disconnect() {
        context.seerrDataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.API_KEY)
            prefs[Keys.ENABLED] = false
            prefs[Keys.SEARCH_ENABLED] = false
            prefs[Keys.RECOMMENDATIONS_ENABLED] = false
            prefs[Keys.DISCOVER_ENABLED] = false
            prefs[Keys.DISCOVER_TRENDING] = true
            prefs[Keys.DISCOVER_POPULAR_MOVIES] = true
            prefs[Keys.DISCOVER_POPULAR_TV] = true
            prefs[Keys.DISCOVER_UPCOMING_MOVIES] = true
            prefs[Keys.DISCOVER_UPCOMING_TV] = true
            prefs[Keys.STREAMING_REGION] = "US"
            prefs[Keys.DISCOVER_REGION] = "US"
        }
    }
}
