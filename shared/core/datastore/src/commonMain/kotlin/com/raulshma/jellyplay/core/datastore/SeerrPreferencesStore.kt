package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map


class SeerrPreferencesStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val secureCredentialsStore: SeerrSecureCredentialsStore,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private object Keys {
        val SERVER_URL = stringPreferencesKey("seerr_server_url")
        val AUTH_METHOD = stringPreferencesKey("seerr_auth_method")
        val USERNAME = stringPreferencesKey("seerr_username")
        val EMAIL = stringPreferencesKey("seerr_email")
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

        /**
         * The Seerr settings-surface connection MODE (direct fields vs the
         * jellyfin-plugin-jellyplay "via server" bridge, ADR 0010). Its
         * consumer is the data-path switch: with the mode on AND the
         * `seerr-bridge` gate open, the Seerr client's calls ride the plugin
         * proxy — see [SeerrPreferences.useServerBridge] and the SeerrBridge
         * interceptor in core/network. Deliberately NOT in
         * [BOOLEAN_DEFAULTS]: it is the user's chosen mode, not a
         * direct-connection feature toggle, so the direct pane's
         * [disconnect] (which resets the defaults map) must not flip it.
         */
        val USE_SERVER_BRIDGE = booleanPreferencesKey("seerr_use_server_bridge")
    }

    // Single source of truth for Seerr preference defaults: the read path
    // ([readSeerrPreferences]) falls back to these for absent/corrupt values,
    // and [disconnect] writes them back — mapOf preserves insertion order, so
    // the reset write order is stable. Identity keys (server URL, auth method,
    // username, email) are not listed: disconnect removes those and the read
    // path re-applies their inline fallbacks.
    // buildMap, not `key to value`: DataStore's infix Preferences.Pair `to`
    // on Key<T> would shadow kotlin's Pair `to` and break the map literal.
    private val BOOLEAN_DEFAULTS: Map<Preferences.Key<Boolean>, Boolean> = buildMap {
        put(Keys.ENABLED, false)
        put(Keys.SEARCH_ENABLED, false)
        put(Keys.RECOMMENDATIONS_ENABLED, false)
        put(Keys.DISCOVER_ENABLED, false)
        put(Keys.DISCOVER_TRENDING, true)
        put(Keys.DISCOVER_POPULAR_MOVIES, true)
        put(Keys.DISCOVER_POPULAR_TV, true)
        put(Keys.DISCOVER_UPCOMING_MOVIES, true)
        put(Keys.DISCOVER_UPCOMING_TV, true)
    }

    private val STRING_DEFAULTS: Map<Preferences.Key<String>, String> = buildMap {
        put(Keys.STREAMING_REGION, "US")
        put(Keys.DISCOVER_REGION, "US")
    }

    val preferences: StateFlow<SeerrPreferences> =
        dataStore.sliceStateFlow(scope, seed = SeerrPreferences(), read = ::readSeerrPreferences)

    val isConnected: Flow<Boolean> = preferences.map { it.serverUrl.isNotBlank() }

    private fun readSeerrPreferences(prefs: Preferences): SeerrPreferences = SeerrPreferences(
        serverUrl = prefs[Keys.SERVER_URL] ?: "",
        authMethod = prefs[Keys.AUTH_METHOD].toEnumOrNull() ?: SeerrAuthMethod.API_KEY,
        username = prefs[Keys.USERNAME] ?: "",
        email = prefs[Keys.EMAIL] ?: "",
        useServerBridge = prefs[Keys.USE_SERVER_BRIDGE] ?: false,
        enabled = prefs.booleanWithDefault(Keys.ENABLED),
        searchEnabled = prefs.booleanWithDefault(Keys.SEARCH_ENABLED),
        recommendationsEnabled = prefs.booleanWithDefault(Keys.RECOMMENDATIONS_ENABLED),
        discoverEnabled = prefs.booleanWithDefault(Keys.DISCOVER_ENABLED),
        discoverTrending = prefs.booleanWithDefault(Keys.DISCOVER_TRENDING),
        discoverPopularMovies = prefs.booleanWithDefault(Keys.DISCOVER_POPULAR_MOVIES),
        discoverPopularTv = prefs.booleanWithDefault(Keys.DISCOVER_POPULAR_TV),
        discoverUpcomingMovies = prefs.booleanWithDefault(Keys.DISCOVER_UPCOMING_MOVIES),
        discoverUpcomingTv = prefs.booleanWithDefault(Keys.DISCOVER_UPCOMING_TV),
        streamingRegion = prefs.stringWithDefault(Keys.STREAMING_REGION),
        discoverRegion = prefs.stringWithDefault(Keys.DISCOVER_REGION),
    )

    private fun Preferences.booleanWithDefault(key: Preferences.Key<Boolean>): Boolean =
        this[key] ?: BOOLEAN_DEFAULTS.getValue(key)

    private fun Preferences.stringWithDefault(key: Preferences.Key<String>): String =
        this[key] ?: STRING_DEFAULTS.getValue(key)

    suspend fun setServerUrl(url: String) {
        dataStore.edit { it[Keys.SERVER_URL] = url.trim() }
    }

    suspend fun setAuthMethod(method: SeerrAuthMethod) {
        dataStore.edit { it[Keys.AUTH_METHOD] = method.name }
    }

    suspend fun setUsername(username: String) {
        dataStore.edit { it[Keys.USERNAME] = username.trim() }
    }

    suspend fun setEmail(email: String) {
        dataStore.edit { it[Keys.EMAIL] = email.trim() }
    }

    /**
     * Persists the Seerr settings-surface connection MODE. Mode selection only —
     * the data-path switch itself (pointing the Seerr client at the plugin's
     * `jellyplay/seerr` proxy once the bridge is linked) reads this flag in
     * core/network's SeerrBridge interceptor, gated on the `seerr-bridge`
     * feature key (see [SeerrPreferences.useServerBridge]).
     */
    suspend fun setUseServerBridge(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_SERVER_BRIDGE] = enabled }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setSearchEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SEARCH_ENABLED] = enabled }
    }

    suspend fun setRecommendationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.RECOMMENDATIONS_ENABLED] = enabled }
    }

    suspend fun setDiscoverEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCOVER_ENABLED] = enabled }
    }

    suspend fun setDiscoverTrending(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCOVER_TRENDING] = enabled }
    }

    suspend fun setDiscoverPopularMovies(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCOVER_POPULAR_MOVIES] = enabled }
    }

    suspend fun setDiscoverPopularTv(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCOVER_POPULAR_TV] = enabled }
    }

    suspend fun setDiscoverUpcomingMovies(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCOVER_UPCOMING_MOVIES] = enabled }
    }

    suspend fun setDiscoverUpcomingTv(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCOVER_UPCOMING_TV] = enabled }
    }

    suspend fun setStreamingRegion(region: String) {
        dataStore.edit { it[Keys.STREAMING_REGION] = region }
    }

    suspend fun setDiscoverRegion(region: String) {
        dataStore.edit { it[Keys.DISCOVER_REGION] = region }
    }

    suspend fun disconnect() {
        secureCredentialsStore.clearAll()
        dataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.AUTH_METHOD)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.EMAIL)
            BOOLEAN_DEFAULTS.forEach { (key, default) -> prefs[key] = default }
            STRING_DEFAULTS.forEach { (key, default) -> prefs[key] = default }
        }
    }
}
