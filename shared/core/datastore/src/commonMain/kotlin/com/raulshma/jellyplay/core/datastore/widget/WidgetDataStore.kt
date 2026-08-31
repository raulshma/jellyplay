package com.raulshma.jellyplay.core.datastore.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * Common replacement for the JVM atomic boolean — the flag is only ever
 * set once from a flow collector and read on the UI thread, so volatile
 * write/read visibility is all that is required.
 */
private class LoadedFlag {
    @Volatile var value: Boolean = false
}

/**
 * Widget cache sink for home-screen widgets (continue-watching, library
 * recommendations, Seerr recommendations).
 *
 * These flows are not "preferences" — they are an I/O buffer between the
 * widget refresh workers (producers) and the AppWidget providers (consumers).
 * Persisting them in DataStore lets widgets render the last-known payload
 * across process death and cold start. Extracted from `UserPreferencesStore`
 * (where they accreted) so the god store no longer carries widget-I/O concerns.
 *
 * **Storage**: injects the shared `"user_prefs"` DataStore (Koin definition
 * in `di.DatastoreKoinModules` / platform `di.AndroidDatastoreModule` since
 * Phase C4) — same file as `UserPreferencesStore` and the other extracted
 * stores, never a second DataStore instance (AndroidX forbids that).
 */
class WidgetDataStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val sharedPrefs: Flow<Preferences> = dataStore.data
    private val json = Json { ignoreUnknownKeys = true }

    // Set by the onEach probes below the moment each eager snapshot receives
    // its first real emission — separates "warmed, memory reads from here on"
    // from "still warming, the StateFlow holds the placeholder default" for
    // the sync *Snapshot() accessors.
    private val continueWatchingLoaded = LoadedFlag()
    private val libraryItemsLoaded = LoadedFlag()
    private val seerrItemsLoaded = LoadedFlag()

    val continueWatching: StateFlow<List<MediaItem>> =
        decodedListStateFlow(Keys.CONTINUE_WATCHING, continueWatchingLoaded)

    val widgetConfig: Flow<WidgetConfig> =
        sharedPrefs.map { prefs ->
            prefs[Keys.WIDGET_CONFIG]?.let {
                try { json.decodeFromString<WidgetConfig>(it) } catch (_: Exception) { null }
            } ?: WidgetConfig()
        }

    val libraryWidgetItems: StateFlow<List<LibraryWidgetItem>> =
        decodedListStateFlow(Keys.LIBRARY_WIDGET_ITEMS, libraryItemsLoaded)

    val libraryWidgetVersion: StateFlow<Long> =
        sharedPrefs.map { it[Keys.LIBRARY_WIDGET_VERSION] ?: 0L }
            .stateIn(scope, SharingStarted.Eagerly, 0L)

    val libraryWidgetUpdatedAtMs: Flow<Long> =
        sharedPrefs.map { it[Keys.LIBRARY_WIDGET_UPDATED_AT_MS] ?: 0L }

    val seerrWidgetItems: StateFlow<List<SeerrWidgetItem>> =
        decodedListStateFlow(Keys.SEERR_WIDGET_ITEMS, seerrItemsLoaded)

    val seerrWidgetVersion: StateFlow<Long> =
        sharedPrefs.map { it[Keys.SEERR_WIDGET_VERSION] ?: 0L }
            .stateIn(scope, SharingStarted.Eagerly, 0L)

    val seerrWidgetUpdatedAtMs: Flow<Long> =
        sharedPrefs.map { it[Keys.SEERR_WIDGET_UPDATED_AT_MS] ?: 0L }

    val widgetLastRefreshMs: Flow<Long> =
        sharedPrefs.map { it[Keys.WIDGET_LAST_REFRESH_MS] ?: 0L }

    suspend fun setWidgetConfig(config: WidgetConfig) {
        dataStore.edit { it[Keys.WIDGET_CONFIG] = json.encodeToString(config) }
    }

    private var cachedPerWidgetConfigs: ParsedCache<Map<Int, WidgetConfig>> = ParsedCache(null, emptyMap())
    private var cachedLegacyWidgetConfig: ParsedCache<WidgetConfig?> = ParsedCache(null, null)

    /**
     * Snapshot of (per-widget configs, legacy global config) eagerly cached so
     * [getWidgetConfigForIdSync] can return a value without suspending. Used by
     * AppWidget providers which must render synchronously in `onUpdate`.
     */
    private val widgetConfigSnapshot: StateFlow<Pair<Map<Int, WidgetConfig>, WidgetConfig?>> =
        sharedPrefs.map { prefs ->
            val perWidgetRaw = prefs[Keys.WIDGET_CONFIGS]
            val perWidget = if (perWidgetRaw == cachedPerWidgetConfigs.raw) {
                cachedPerWidgetConfigs.value
            } else {
                decodeWidgetConfigs(perWidgetRaw).also { cachedPerWidgetConfigs = ParsedCache(perWidgetRaw, it) }
            }
            val legacyRaw = prefs[Keys.WIDGET_CONFIG]
            val legacy = if (legacyRaw == cachedLegacyWidgetConfig.raw) {
                cachedLegacyWidgetConfig.value
            } else {
                decodeWidgetConfig(legacyRaw).also { cachedLegacyWidgetConfig = ParsedCache(legacyRaw, it) }
            }
            perWidget to legacy
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap<Int, WidgetConfig>() to null)

    fun getWidgetConfigForIdSync(appWidgetId: Int): WidgetConfig {
        val (perWidget, legacy) = widgetConfigSnapshot.value
        return perWidget[appWidgetId] ?: legacy ?: WidgetConfig()
    }

    /**
     * Sync item snapshots for the AppWidget render path — `onDataSetChanged`
     * runs on the main thread. Instant memory read once the eager snapshot
     * has materialized. On a cold process (store just constructed, snapshot
     * still warming) each does ONE bounded disk read (1 s cap) so the first
     * widget refresh after process death renders the persisted payload
     * instead of the empty placeholder — which could otherwise sit on the
     * home screen until the next worker-triggered refresh. A timed-out
     * warmup falls back to the placeholder, same as before.
     */
    fun continueWatchingSnapshot(): List<MediaItem> =
        snapshotOrFallback(continueWatchingLoaded, continueWatching)

    fun libraryWidgetItemsSnapshot(): List<LibraryWidgetItem> =
        snapshotOrFallback(libraryItemsLoaded, libraryWidgetItems)

    fun seerrWidgetItemsSnapshot(): List<SeerrWidgetItem> =
        snapshotOrFallback(seerrItemsLoaded, seerrWidgetItems)

    fun getWidgetConfigForId(appWidgetId: Int): Flow<WidgetConfig> =
        sharedPrefs.map { prefs ->
            val perWidgetConfig = prefs[Keys.WIDGET_CONFIGS]?.let { configsJson ->
                try {
                    val configs = json.decodeFromString<Map<Int, WidgetConfig>>(configsJson)
                    configs[appWidgetId]
                } catch (_: Exception) { null }
            }
            perWidgetConfig ?: run {
                prefs[Keys.WIDGET_CONFIG]?.let {
                    try { json.decodeFromString<WidgetConfig>(it) } catch (_: Exception) { null }
                } ?: WidgetConfig()
            }
        }

    suspend fun setWidgetConfigForId(appWidgetId: Int, config: WidgetConfig) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.WIDGET_CONFIGS]?.let {
                try { json.decodeFromString<Map<Int, WidgetConfig>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { put(appWidgetId, config) }
            prefs[Keys.WIDGET_CONFIGS] = json.encodeToString(next)
        }
    }

    suspend fun removeWidgetConfigForId(appWidgetId: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.WIDGET_CONFIGS]?.let {
                try { json.decodeFromString<Map<Int, WidgetConfig>>(it) } catch (_: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current.toMutableMap().apply { remove(appWidgetId) }
            prefs[Keys.WIDGET_CONFIGS] = json.encodeToString(next)
        }
    }

    suspend fun setLibraryWidgetItems(
        items: List<LibraryWidgetItem>,
        version: Long,
        updatedAtMs: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_WIDGET_ITEMS] = json.encodeToString(items)
            prefs[Keys.LIBRARY_WIDGET_VERSION] = version
            prefs[Keys.LIBRARY_WIDGET_UPDATED_AT_MS] = updatedAtMs
        }
    }

    suspend fun setSeerrWidgetItems(
        items: List<SeerrWidgetItem>,
        version: Long,
        updatedAtMs: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SEERR_WIDGET_ITEMS] = json.encodeToString(items)
            prefs[Keys.SEERR_WIDGET_VERSION] = version
            prefs[Keys.SEERR_WIDGET_UPDATED_AT_MS] = updatedAtMs
        }
    }

    suspend fun setWidgetLastRefreshMs(ms: Long) {
        dataStore.edit { it[Keys.WIDGET_LAST_REFRESH_MS] = ms }
    }

    /** Persists the current continue-watching shelf so widgets can render it offline / on cold start. */
    suspend fun setContinueWatching(items: List<MediaItem>) {
        dataStore.edit { it[Keys.CONTINUE_WATCHING] = json.encodeToString(items) }
    }

    private companion object {
        /** Cap on the one-time cold-process disk read behind the *Snapshot() accessors. */
        private const val SNAPSHOT_WARMUP_TIMEOUT_MS = 1_000L
    }

    /**
     * The shape every eager widget-item flow shares: flag the store as warmed
     * on first emission, leniently decode the JSON column (a corrupt blob
     * degrades to the empty placeholder, never throws), and hot-start in the
     * application scope.
     */
    private inline fun <reified T> decodedListStateFlow(
        key: Preferences.Key<String>,
        loaded: LoadedFlag,
    ): StateFlow<List<T>> {
        var cached = ParsedCache(null, emptyList<T>())
        return sharedPrefs.onEach { loaded.value = true }.map { prefs ->
            val raw = prefs[key]
            if (raw == cached.raw) {
                cached.value
            } else {
                decodeList<T>(raw).also { cached = ParsedCache(raw, it) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> =
        raw?.let {
            try { json.decodeFromString<List<T>>(it) }
            catch (_: Exception) { emptyList() }
        } ?: emptyList()

    private fun decodeWidgetConfigs(raw: String?): Map<Int, WidgetConfig> =
        raw?.let {
            try { json.decodeFromString<Map<Int, WidgetConfig>>(it) }
            catch (_: Exception) { null }
        } ?: emptyMap()

    private fun decodeWidgetConfig(raw: String?): WidgetConfig? =
        raw?.let {
            try { json.decodeFromString<WidgetConfig>(it) } catch (_: Exception) { null }
        }

    /**
     * The warm/cold read every *Snapshot() accessor shares: instant memory
     * read once the eager flow has emitted, else ONE bounded disk read (see
     * [SNAPSHOT_WARMUP_TIMEOUT_MS]) so the first widget refresh after process
     * death renders the persisted payload; a timed-out warmup falls back to
     * the placeholder.
     */
    private fun <T> snapshotOrFallback(
        loaded: LoadedFlag,
        flow: StateFlow<T>,
    ): T =
        if (loaded.value) {
            flow.value
        } else {
            blockingFirstOrNull(flow, SNAPSHOT_WARMUP_TIMEOUT_MS) ?: flow.value
        }

    private object Keys {
        val CONTINUE_WATCHING = stringPreferencesKey("continue_watching")
        val WIDGET_CONFIG = stringPreferencesKey("widget_config")
        val WIDGET_CONFIGS = stringPreferencesKey("widget_configs")
        val LIBRARY_WIDGET_ITEMS = stringPreferencesKey("library_widget_items")
        val LIBRARY_WIDGET_VERSION = longPreferencesKey("library_widget_version")
        val LIBRARY_WIDGET_UPDATED_AT_MS = longPreferencesKey("library_widget_updated_at_ms")
        val SEERR_WIDGET_ITEMS = stringPreferencesKey("seerr_widget_items")
        val SEERR_WIDGET_VERSION = longPreferencesKey("seerr_widget_version")
        val SEERR_WIDGET_UPDATED_AT_MS = longPreferencesKey("seerr_widget_updated_at_ms")
        val WIDGET_LAST_REFRESH_MS = longPreferencesKey("widget_last_refresh_ms")
    }
}
