package com.raulshma.jellyplay.core.datastore.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Persists the ids of the last [MAX_RECENTS] setting entries the user opened from
 * the settings search results, most-recent first.
 *
 * Each id is a [com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem.id];
 * the ids are resolved back to renderable items against the registry in the settings
 * UI, so this store only owns the ordered id list as a single JSON blob. Stale ids
 * (an entry whose setting no longer exists in the registry) are dropped silently at
 * resolve time and naturally age out as new ids displace them.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file under a
 * settings-recents-private key; no migration file. Like
 * [SearchFiltersStore], it intentionally lives outside the per-category reset
 * coverage guard (it is UX-recency state, not a user preference), so it is untouched
 * by per-section resets — but it *is* cleared by a full factory reset
 * ([com.raulshma.jellyplay.core.datastore.UserPreferencesStore.clearAllPreferencesOnly]),
 * which wipes every non-preserved key.
 */
class SettingsRecentsStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    internal object Keys {
        val SETTINGS_RECENTS = stringPreferencesKey("settings_recents")
    }

    private val codec = Json {
        ignoreUnknownKeys = true
    }
    private val listSerializer = ListSerializer(String.serializer())

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    /**
     * The persisted setting ids, most-recent first (capped at [MAX_RECENTS]).
     * Decoding is lenient: a missing or corrupt blob resolves to an empty list
     * rather than throwing, so a bad value can never break the settings screen.
     */
    val recents: StateFlow<List<String>> = sharedPrefs
        .map { prefs -> decode(prefs[Keys.SETTINGS_RECENTS]) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Records that the setting with [id] was used: removes any prior occurrence
     * (dedup), prepends it (most-recent first), and trims to [MAX_RECENTS].
     */
    suspend fun addRecent(id: String) {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val updated = decode(prefs[Keys.SETTINGS_RECENTS])
                .filterNot { it == trimmed }
                .toMutableList()
                .apply { add(0, trimmed) }
                .take(MAX_RECENTS)
            prefs[Keys.SETTINGS_RECENTS] = codec.encodeToString(listSerializer, updated)
        }
    }

    /** Clears all recorded recent setting ids. */
    suspend fun clearRecents() {
        dataStore.edit { it.remove(Keys.SETTINGS_RECENTS) }
    }

    private fun decode(blob: String?): List<String> {
        if (blob.isNullOrBlank()) return emptyList()
        return runCatching { codec.decodeFromString(listSerializer, blob) }
            .getOrDefault(emptyList())
    }

    internal companion object {
        /** Maximum number of recent setting ids retained. */
        const val MAX_RECENTS = 5
    }
}
