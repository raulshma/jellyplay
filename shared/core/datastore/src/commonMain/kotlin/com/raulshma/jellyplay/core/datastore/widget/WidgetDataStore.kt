package com.raulshma.jellyplay.core.datastore.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.SyncAllowlist
import com.raulshma.jellyplay.core.datastore.stringEntry
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

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
 * ) — same file as `UserPreferencesStore` and the other extracted
 * stores, never a second DataStore instance (AndroidX forbids that).
 */
class WidgetDataStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val sharedPrefs: Flow<Preferences> = dataStore.data
    private val json = Json { ignoreUnknownKeys = true }

    // Per-flow JSON memo fields — one per eager widget-item flow, replacing
    // the old function-local `var cached` inside [decodedListStateFlow]
    // (behavior-identical memoisation, but inspectable/ownable like every
    // other store's cache field).
    private var cachedContinueWatching: ParsedCache<List<MediaItem>> = ParsedCache(null, emptyList())
    private var cachedLibraryWidgetItems: ParsedCache<List<LibraryWidgetItem>> = ParsedCache(null, emptyList())
    private var cachedSeerrWidgetItems: ParsedCache<List<SeerrWidgetItem>> = ParsedCache(null, emptyList())

    val continueWatching: StateFlow<List<MediaItem>> =
        decodedListStateFlow(
            Keys.CONTINUE_WATCHING,
            cache = { cachedContinueWatching },
            cacheRef = { cachedContinueWatching = it },
        )

    val widgetConfig: Flow<WidgetConfig> =
        sharedPrefs.map { prefs ->
            prefs[Keys.WIDGET_CONFIG]?.let {
                try { json.decodeFromString<WidgetConfig>(it) } catch (_: Exception) { null }
            } ?: WidgetConfig()
        }

    val libraryWidgetItems: StateFlow<List<LibraryWidgetItem>> =
        decodedListStateFlow(
            Keys.LIBRARY_WIDGET_ITEMS,
            cache = { cachedLibraryWidgetItems },
            cacheRef = { cachedLibraryWidgetItems = it },
        )

    val seerrWidgetItems: StateFlow<List<SeerrWidgetItem>> =
        decodedListStateFlow(
            Keys.SEERR_WIDGET_ITEMS,
            cache = { cachedSeerrWidgetItems },
            cacheRef = { cachedSeerrWidgetItems = it },
        )

    suspend fun setWidgetConfig(config: WidgetConfig) {
        dataStore.edit { it[Keys.WIDGET_CONFIG] = json.encodeToString(config) }
    }

    private var cachedPerWidgetConfigs: ParsedCache<Map<Int, WidgetConfig>> = ParsedCache(null, emptyMap())
    private var cachedLegacyWidgetConfig: ParsedCache<WidgetConfig?> = ParsedCache(null, null)

    /**
     * Snapshot of (per-widget configs, legacy global config) eagerly cached so
     * [getWidgetConfigForIdSync] can return a value without suspending. Used by
     * AppWidget providers which must render synchronously in `onUpdate`.
     *
     * MemoizeNull (this store's pre-promotion policy at both sites): a null raw
     * is a cacheable input — the decoded value depends only on the raw string,
     * so a memoised default (empty map / null config) is safe to serve.
     */
    private val widgetConfigSnapshot: StateFlow<Pair<Map<Int, WidgetConfig>, WidgetConfig?>> =
        sharedPrefs.map { prefs ->
            val perWidget = PreferenceCodec.cachedJson(
                raw = prefs[Keys.WIDGET_CONFIGS],
                cache = cachedPerWidgetConfigs,
                default = emptyMap(),
                parse = { json.decodeFromString<Map<Int, WidgetConfig>>(it) },
                cacheRef = { cachedPerWidgetConfigs = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )
            val legacy = PreferenceCodec.cachedJson(
                raw = prefs[Keys.WIDGET_CONFIG],
                cache = cachedLegacyWidgetConfig,
                default = null,
                parse = { json.decodeFromString<WidgetConfig>(it) },
                cacheRef = { cachedLegacyWidgetConfig = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )
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
     * still warming) each makes ONE bounded wait (1 s cap) for the first real
     * emission so the first widget refresh after process death renders the
     * persisted payload instead of the empty placeholder — which could
     * otherwise sit on the home screen until the next worker-triggered
     * refresh. A timed-out warmup falls back to the placeholder, same as
     * before.
     */
    fun continueWatchingSnapshot(): List<MediaItem> =
        snapshotOrFallback(continueWatching, emptyList())

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

    suspend fun setLibraryWidgetItems(items: List<LibraryWidgetItem>) {
        dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_WIDGET_ITEMS] = json.encodeToString(items)
        }
    }

    suspend fun setSeerrWidgetItems(items: List<SeerrWidgetItem>) {
        dataStore.edit { prefs ->
            prefs[Keys.SEERR_WIDGET_ITEMS] = json.encodeToString(items)
        }
    }

    /** Persists the current continue-watching shelf so widgets can render it offline / on cold start. */
    suspend fun setContinueWatching(items: List<MediaItem>) {
        dataStore.edit { it[Keys.CONTINUE_WATCHING] = json.encodeToString(items) }
    }

    // ------------------------------------------------------------------
    // CONFIG-ONLY BACKUP SURFACE (the local settings backup's `widget`
    // slice): the same allowlisted raw-string read/apply shape the
    // integration stores' sync-only surfaces use (SeerrPreferencesStore et
    // al.) — but this is NOT a server-sync face; it exists so the LOCAL
    // backup can carry the user's widget CONFIG. The payload-cache keys
    // (`continue_watching` / `library_widget_items` / `seerr_widget_items`)
    // are deliberately absent from the allowlist: they are an I/O buffer
    // between the refresh workers and the AppWidget providers (derived
    // state), never a setting, so they can never enter a backup.
    // ------------------------------------------------------------------

    /**
     * The config allowlist: raw key name → typed entry (the integration
     * stores' shared [com.raulshma.jellyplay.core.datastore.SyncEntry] idiom —
     * renames land here). The entries' `default` is unused by this surface:
     * its reset REMOVES the key (absent = default config) rather than writing
     * a default.
     */
    private val ConfigTypedKeys = SyncAllowlist(
        mapOf(
            "widget_config" to stringEntry(Keys.WIDGET_CONFIG, default = ""),
            "widget_configs" to stringEntry(Keys.WIDGET_CONFIGS, default = ""),
        ),
    )

    /**
     * The raw key names [configSnapshot]/[configApply] may ever touch — the
     * config allowlist (see the surface KDoc above).
     */
    val ConfigKeys: Set<String> get() = ConfigTypedKeys.keys

    /**
     * The raw stored value per allowlisted key (`null` = the key is absent —
     * readers fall back to the default config). The backup slice source's
     * snapshot face; the values are the persisted JSON blobs verbatim.
     */
    suspend fun configSnapshot(): Map<String, String?> {
        val prefs = sharedPrefs.first()
        return ConfigTypedKeys.snapshot(prefs)
    }

    /**
     * Writes one allowlisted raw value (the backup slice source's adopt
     * face), or resets the key when [value] is `null` — the entry is removed
     * so readers fall back to the default config (the same "absent = default"
     * the read paths above implement). Unallowlisted keys are ignored in BOTH
     * directions — a payload-cache key can never be written here, and a
     * hostile backup slice can never smuggle one in.
     */
    suspend fun configApply(key: String, value: String?) {
        val entry = ConfigTypedKeys.entry(key) ?: return
        dataStore.edit { prefs ->
            if (value == null) {
                // Removal is name-based: the entry goes whatever kind is
                // stored under the name.
                prefs.remove(stringPreferencesKey(key))
                return@edit
            }
            entry.write(prefs, value)
        }
    }

    private companion object {
        /** Cap on the one-time cold-process disk read behind the *Snapshot() accessors. */
        private const val SNAPSHOT_WARMUP_TIMEOUT_MS = 1_000L
    }

    /**
     * The warm/cold read every *Snapshot() accessor shares: await the eager
     * snapshot's settled value (see [blockingAwaitFresh]) — instant once the
     * eager flow has emitted, else ONE bounded wait for the first real
     * emission to replace the stateIn seed placeholder.
     */
    private fun <T> snapshotOrFallback(
        flow: StateFlow<T>,
        seed: T,
    ): T = blockingAwaitFresh(flow, seed, SNAPSHOT_WARMUP_TIMEOUT_MS)

    /**
     * The shape every eager widget-item flow shares: leniently decode the JSON
     * column (a corrupt blob degrades to the empty placeholder, never throws)
     * and hot-start in the application scope.
     *
     * The memo stays a hand-rolled compare instead of
     * [PreferenceCodec.cachedJson] because the decode is `reified` — a parse
     * closure using `T` cannot cross into the helper's non-inline lambda — so
     * each flow instead owns a proper [ParsedCache] field (passed as
     * [cache]/[cacheRef]). The semantics are deliberately the helper's
     * [CachedJsonNullPolicy.MemoizeNull] policy, unchanged from the
     * function-local `var cached` this replaced: a null raw memoises the empty
     * placeholder like any other input, and a decode failure caches the
     * placeholder under the raw key.
     */
    private inline fun <reified T> decodedListStateFlow(
        key: Preferences.Key<String>,
        crossinline cache: () -> ParsedCache<List<T>>,
        crossinline cacheRef: (ParsedCache<List<T>>) -> Unit,
    ): StateFlow<List<T>> {
        return sharedPrefs.map { prefs ->
            val raw = prefs[key]
            if (raw == cache().key) {
                cache().value
            } else {
                decodeList<T>(raw).also { cacheRef(ParsedCache(raw, it)) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> =
        raw?.let {
            try { json.decodeFromString<List<T>>(it) }
            catch (_: Exception) { emptyList() }
        } ?: emptyList()

    private object Keys {
        val CONTINUE_WATCHING = stringPreferencesKey("continue_watching")
        val WIDGET_CONFIG = stringPreferencesKey("widget_config")
        val WIDGET_CONFIGS = stringPreferencesKey("widget_configs")
        val LIBRARY_WIDGET_ITEMS = stringPreferencesKey("library_widget_items")
        val SEERR_WIDGET_ITEMS = stringPreferencesKey("seerr_widget_items")
    }
}
