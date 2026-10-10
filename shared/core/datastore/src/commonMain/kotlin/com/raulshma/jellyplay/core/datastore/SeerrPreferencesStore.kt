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
    // the reset write order is stable. The sync surface's reset face
    // ([syncApply] with a `null`) writes each allowlisted entry's declared
    // default too: value-presence resets roam as value writes, absence does
    // not (see [syncApply]). Identity keys (server URL, auth method,
    // username, email) are not listed: their resets write the blank
    // connection state directly (see [disconnect] and the SyncTypedKeys
    // declarations) — the read paths treat the written blanks exactly like
    // absence (blank degrades to the inline fallback).
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

    /**
     * Resets every direct-connection preference to its default and clears the
     * secure credentials. Every reset WRITES its default value instead of
     * removing the key (value-presence resets): the identity keys sit in the
     * sync allowlist below and the integrations adapter is value-only, so a
     * removal would never roam — the engine's next adopt pass would
     * re-install the server's still-standing rows and silently undo the
     * disconnect. The written blanks roam as ordinary value writes; the read
     * paths treat them exactly like absence (a blank server URL keeps
     * [isConnected] false, a blank auth method degrades to
     * [SeerrAuthMethod.API_KEY]). The settings-surface bridge mode
     * ([SeerrPreferences.useServerBridge]) is deliberately untouched — the
     * pane choice, not a connection setting.
     */
    suspend fun disconnect() {
        secureCredentialsStore.clearAll()
        dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = ""
            prefs[Keys.AUTH_METHOD] = ""
            prefs[Keys.USERNAME] = ""
            prefs[Keys.EMAIL] = ""
            BOOLEAN_DEFAULTS.forEach { (key, default) -> prefs[key] = default }
            STRING_DEFAULTS.forEach { (key, default) -> prefs[key] = default }
        }
    }

    // ------------------------------------------------------------------
    // SYNC-ONLY SURFACE (jellyplay-plugin-jellyplay settings sync, the
    // `integrations` namespace): a minimal allowlisted read/write pair the
    // sync adapter translates into wire values. NOT a general editing API —
    // the UI setters above remain the only user-facing write path. The
    // allowlist below is the SINGLE SOURCE OF TRUTH for which key names may
    // ever leave the device or be written by sync; the secure credentials
    // (api key / password / session cookie) live in the separate encrypted
    // store and are deliberately absent — they can never sync.
    // ------------------------------------------------------------------

    /**
     * The sync allowlist: raw key name → its typed read/write entry (the
     * shared [SyncEntry] builders — see SyncAllowlist.kt). Key names are
     * private to this store (the adapter never hardcodes them); this map is
     * where renames land. The identity entries' blank defaults mirror the
     * read path's inline fallbacks ([isConnected] maps the blank server URL
     * to false; a blank auth method degrades to [SeerrAuthMethod.API_KEY]) —
     * the same blanks [disconnect] writes.
     */
    private val SyncTypedKeys = SyncAllowlist(
        mapOf(
            "seerr_server_url" to stringEntry(Keys.SERVER_URL, default = "") { it.trim() },
            "seerr_auth_method" to stringEntry(Keys.AUTH_METHOD, default = ""),
            "seerr_username" to stringEntry(Keys.USERNAME, default = "") { it.trim() },
            "seerr_email" to stringEntry(Keys.EMAIL, default = "") { it.trim() },
            "seerr_enabled" to booleanEntry(Keys.ENABLED, BOOLEAN_DEFAULTS.getValue(Keys.ENABLED)),
            "seerr_search_enabled" to booleanEntry(Keys.SEARCH_ENABLED, BOOLEAN_DEFAULTS.getValue(Keys.SEARCH_ENABLED)),
            "seerr_recommendations_enabled" to booleanEntry(
                Keys.RECOMMENDATIONS_ENABLED,
                BOOLEAN_DEFAULTS.getValue(Keys.RECOMMENDATIONS_ENABLED),
            ),
            "seerr_discover_enabled" to booleanEntry(Keys.DISCOVER_ENABLED, BOOLEAN_DEFAULTS.getValue(Keys.DISCOVER_ENABLED)),
            "seerr_discover_trending" to booleanEntry(Keys.DISCOVER_TRENDING, BOOLEAN_DEFAULTS.getValue(Keys.DISCOVER_TRENDING)),
            "seerr_discover_popular_movies" to booleanEntry(
                Keys.DISCOVER_POPULAR_MOVIES,
                BOOLEAN_DEFAULTS.getValue(Keys.DISCOVER_POPULAR_MOVIES),
            ),
            "seerr_discover_popular_tv" to booleanEntry(Keys.DISCOVER_POPULAR_TV, BOOLEAN_DEFAULTS.getValue(Keys.DISCOVER_POPULAR_TV)),
            "seerr_discover_upcoming_movies" to booleanEntry(
                Keys.DISCOVER_UPCOMING_MOVIES,
                BOOLEAN_DEFAULTS.getValue(Keys.DISCOVER_UPCOMING_MOVIES),
            ),
            "seerr_discover_upcoming_tv" to booleanEntry(Keys.DISCOVER_UPCOMING_TV, BOOLEAN_DEFAULTS.getValue(Keys.DISCOVER_UPCOMING_TV)),
            "seerr_streaming_region" to stringEntry(Keys.STREAMING_REGION, STRING_DEFAULTS.getValue(Keys.STREAMING_REGION)),
            "seerr_discover_region" to stringEntry(Keys.DISCOVER_REGION, STRING_DEFAULTS.getValue(Keys.DISCOVER_REGION)),
        ),
    )

    /**
     * The raw key names [syncSnapshot]/[syncApply] may ever touch — the sync
     * allowlist (all non-secret Seerr configuration; see [SyncTypedKeys]).
     */
    val SyncKeys: Set<String> get() = SyncTypedKeys.keys

    /**
     * The raw stored value per allowlisted key (`null` = the key is absent —
     * readers fall back to that key's default). The sync adapter's snapshot
     * face; a corrupt DataStore read degrades to all-absent per the module's
     * corrupt-read policy.
     */
    suspend fun syncSnapshot(): Map<String, String?> = SyncTypedKeys.snapshotFrom(dataStore)

    /**
     * Writes one allowlisted raw value (the sync adapter's adopt face), or
     * resets the key when [value] is `null` — the reset WRITES the entry's
     * default (never a removal: value-presence resets roam as value writes
     * through the value-only adapter, absence does not, so a removal would
     * leave the server's still-standing row to re-adopt on a later cycle and
     * undo the reset). Unallowlisted keys are ignored in BOTH directions (a
     * hostile or stale server row can never write a key this store does not
     * sync); an uncoercible value (e.g. a non-boolean string for a boolean
     * key) skips the write, leaving the local value — the prefs-adapter
     * coercion rule. String identity fields get the same trim normalization
     * their UI setters apply.
     */
    suspend fun syncApply(key: String, value: String?) =
        SyncTypedKeys.applyTo(dataStore, key, value)
}
