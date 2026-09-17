package com.raulshma.jellyplay.core.datastore.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the search feature's active filter set (media types, genres, years,
 * tags, minimum rating, sort option, played status) as a single JSON blob.
 *
 * Unlike [com.raulshma.jellyplay.core.datastore.library.LibraryStore] (which
 * keys filters per-library), search has no folder scoping, so a single key holds
 * the whole [SearchFilters] snapshot. The JSON encode/decode of the
 * feature-module `SearchFilters` type is the caller's responsibility
 * (mirroring how `LibraryViewModel` owns its `libraryJson` codec around
 * `LibraryStore`'s raw string) — this store only reads/writes the string, so it
 * has no dependency on the feature module's data class.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file under a
 * search-private key; no migration file.
 */
class SearchFiltersStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val SEARCH_FILTERS = stringPreferencesKey("search_filters")
    }

    /**
     * The persisted filter blob as a raw JSON string, or `null` when no filters
     * have ever been saved. Decoded by the caller (the search ViewModel) with its
     * own lenient kotlinx.serialization codec so forward-compatible field
     * additions don't break older snapshots.
     */
    val searchFiltersJson: StateFlow<String?> =
        dataStore.sliceStateFlow(scope, seed = null, read = { it[Keys.SEARCH_FILTERS] })

    /**
     * Persists the [filtersJson] snapshot (the caller-encoded JSON of the search
     * feature's `SearchFilters`). Overwrites any prior value.
     */
    suspend fun setSearchFilters(filtersJson: String) {
        dataStore.edit { it[Keys.SEARCH_FILTERS] = filtersJson }
    }

    /** Clears the persisted filter blob (used by "clear all"). */
    suspend fun clearSearchFilters() {
        dataStore.edit { it.remove(Keys.SEARCH_FILTERS) }
    }
}
