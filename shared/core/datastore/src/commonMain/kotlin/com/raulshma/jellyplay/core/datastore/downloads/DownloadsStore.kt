package com.raulshma.jellyplay.core.datastore.downloads

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Deep module owning the **downloads** preference domain: wifi-only + per-stream
 * connection counts (with the 1..6 max-concurrent invariant), download quality,
 * smart/auto downloads (+ its retention policy: lookahead, max-per-pass,
 * keep-days), storage cap + location, the cellular size warning, and the
 * schedule window.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the `MAX_CONCURRENT_DOWNLOADS` coerce invariant),
 * its read projection, and its reset-key list end-to-end. Mirrors the
 * `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * The auto-download **server allow-list** (`auto_download_servers`) is the one
 * per-user key here: it is stored under the `u_<userId>::<canonical>` namespace
 * (the `HomeDiscoveryStore` precedent) so two accounts on one install keep
 * independent allow-lists. The canonical key string is shared with the backup
 * format; every read/write resolves the namespace from the very
 * `Preferences` snapshot being read or edited ([ServerIdentityStore.activeUserIdIn]),
 * pre-login reads serve the default (empty = all servers).
 */
class DownloadsStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
    private val identityStore: ServerIdentityStore,
) {
    private val scope = externalScope

    private val json = Json { ignoreUnknownKeys = true }

    internal object Keys {
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_CONNECTIONS = intPreferencesKey("download_connections")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SMART_DOWNLOADS_ENABLED = booleanPreferencesKey("smart_downloads_enabled")
        val AUTO_DOWNLOAD_NEW_EPISODES = booleanPreferencesKey("auto_download_new_episodes")
        val MAX_DOWNLOAD_STORAGE_GB = intPreferencesKey("max_download_storage_gb")
        val DOWNLOAD_STORAGE_LOCATION = stringPreferencesKey("download_storage_location")
        /**
         * Auto-delete-after-watch: when ON, a downloaded item is removed from
         * disk once it reaches 100% played. This is an unrequested,
         * destructive, opt-in behaviour; see
         * `PlayedStateSync.maybeAutoDeleteAfterWatch`. Default OFF.
         */
        val AUTO_DELETE_AFTER_WATCH = booleanPreferencesKey("auto_delete_after_watch")
        val CELLULAR_DOWNLOAD_SIZE_WARNING_MB = intPreferencesKey("cellular_download_size_warning_mb")
        val DOWNLOAD_SCHEDULE_ENABLED = booleanPreferencesKey("download_schedule_enabled")
        val DOWNLOAD_SCHEDULE_START = intPreferencesKey("download_schedule_start")
        val DOWNLOAD_SCHEDULE_END = intPreferencesKey("download_schedule_end")
        val DOWNLOAD_SCHEDULE_WIFI_ONLY = booleanPreferencesKey("download_schedule_wifi_only")
        /**
         * Auto-download lookahead: how many not-yet-downloaded episodes after the
         * highest downloaded (or watched) episode of each season one check pass
         * enqueues. `0` restores the legacy all-missing-episodes behavior.
         */
        val AUTO_DOWNLOAD_LOOKAHEAD = intPreferencesKey("auto_download_lookahead")
        /**
         * Global per-pass enqueue budget across every series (`0` = unlimited) —
         * remaining series are picked up by the next pass.
         */
        val AUTO_DOWNLOAD_MAX_PER_PASS = intPreferencesKey("auto_download_max_per_pass")
        /**
         * Keep-days retention: completed downloads older than this many days are
         * swept (unwatched ones are protected). `0` = off.
         */
        val AUTO_DOWNLOAD_KEEP_DAYS = intPreferencesKey("auto_download_keep_days")
        /**
         * Per-user server allow-list for auto-download — canonical name of the
         * `u_<userId>::`-namespaced JSON string-set key. Empty = all servers.
         * Never read/written as a flat key: see the class KDoc.
         */
        val AUTO_DOWNLOAD_SERVERS = stringPreferencesKey("auto_download_servers")
    }

    val downloads: StateFlow<DownloadsSlice> =
        dataStore.sliceStateFlow(scope, seed = DownloadsSlice(), read = ::read)

    internal fun read(prefs: Preferences): DownloadsSlice = DownloadsSlice(
        wifiOnlyDownloads = PreferenceCodec.readBool(prefs, Keys.WIFI_ONLY_DOWNLOADS, "wifi_only_downloads", true),
        downloadConnections = PreferenceCodec.readInt(prefs, Keys.DOWNLOAD_CONNECTIONS, "download_connections", 4),
        maxConcurrentDownloads = PreferenceCodec.readInt(prefs, Keys.MAX_CONCURRENT_DOWNLOADS, "max_concurrent_downloads", 3)
            .coerceIn(1, 6),
        downloadQuality = readDownloadQuality(prefs),
        smartDownloadsEnabled = PreferenceCodec.readBool(prefs, Keys.SMART_DOWNLOADS_ENABLED, "smart_downloads_enabled", false),
        autoDownloadNewEpisodes = PreferenceCodec.readBool(prefs, Keys.AUTO_DOWNLOAD_NEW_EPISODES, "auto_download_new_episodes", false),
        autoDownloadLookahead = PreferenceCodec.readInt(prefs, Keys.AUTO_DOWNLOAD_LOOKAHEAD, "auto_download_lookahead", DEFAULT_LOOKAHEAD)
            .coerceIn(0, MAX_LOOKAHEAD),
        autoDownloadMaxPerPass = PreferenceCodec.readInt(prefs, Keys.AUTO_DOWNLOAD_MAX_PER_PASS, "auto_download_max_per_pass", 0)
            .coerceIn(0, MAX_PER_PASS_LIMIT),
        autoDownloadKeepDays = PreferenceCodec.readInt(prefs, Keys.AUTO_DOWNLOAD_KEEP_DAYS, "auto_download_keep_days", 0)
            .coerceAtLeast(0),
        autoDownloadServers = readAutoDownloadServers(prefs),
        maxDownloadStorageGb = PreferenceCodec.readInt(prefs, Keys.MAX_DOWNLOAD_STORAGE_GB, "max_download_storage_gb", 0),
        downloadStorageLocation = prefs[Keys.DOWNLOAD_STORAGE_LOCATION] ?: "INTERNAL",
        autoDeleteAfterWatch = prefs[Keys.AUTO_DELETE_AFTER_WATCH] ?: false,
        cellularDownloadSizeWarningMb = PreferenceCodec.readInt(prefs, Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB, "cellular_download_size_warning_mb", 0),
        downloadScheduleEnabled = prefs[Keys.DOWNLOAD_SCHEDULE_ENABLED] ?: false,
        downloadScheduleWindow = DownloadScheduleWindow(
            startHour = prefs[Keys.DOWNLOAD_SCHEDULE_START] ?: 0,
            endHour = prefs[Keys.DOWNLOAD_SCHEDULE_END] ?: 6,
            wifiOnly = prefs[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] ?: true,
        ),
    )

    /**
     * The active user's auto-download server allow-list, read straight from a
     * [Preferences] snapshot — the `HomeDiscoveryStore.read` shape: the
     * namespace is resolved from the snapshot itself, and pre-login (no active
     * user) serves the default (empty = all servers). A corrupted JSON blob
     * degrades to the default rather than throwing into the slice collector.
     */
    private fun readAutoDownloadServers(prefs: Preferences): Set<String> {
        val userId = identityStore.activeUserIdIn(prefs) ?: return emptySet()
        val raw = prefs[UserNamespacedKeys.stringKey(userId, Keys.AUTO_DOWNLOAD_SERVERS)] ?: return emptySet()
        return runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
    }

    private fun readDownloadQuality(prefs: Preferences): DownloadQuality =
        prefs[Keys.DOWNLOAD_QUALITY].toEnumOrNull() ?: DownloadQuality.ORIGINAL

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
    }

    suspend fun setDownloadConnections(count: Int) {
        dataStore.edit { it[Keys.DOWNLOAD_CONNECTIONS] = count }
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 6) }
    }

    suspend fun setDownloadQuality(quality: DownloadQuality) {
        dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = quality.name }
    }

    suspend fun setSmartDownloadsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SMART_DOWNLOADS_ENABLED] = enabled }
    }

    suspend fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_DOWNLOAD_NEW_EPISODES] = enabled }
    }

    /** Lookahead window in episodes (0 = legacy all-missing behavior). */
    suspend fun setAutoDownloadLookahead(episodes: Int) {
        dataStore.edit { it[Keys.AUTO_DOWNLOAD_LOOKAHEAD] = episodes.coerceIn(0, MAX_LOOKAHEAD) }
    }

    /** Per-pass enqueue budget across series (0 = unlimited). */
    suspend fun setAutoDownloadMaxPerPass(count: Int) {
        dataStore.edit { it[Keys.AUTO_DOWNLOAD_MAX_PER_PASS] = count.coerceIn(0, MAX_PER_PASS_LIMIT) }
    }

    /** Keep-days retention window (0 = off). */
    suspend fun setAutoDownloadKeepDays(days: Int) {
        dataStore.edit { it[Keys.AUTO_DOWNLOAD_KEEP_DAYS] = days.coerceAtLeast(0) }
    }

    /**
     * Writes the active user's server allow-list into their `u_<userId>::`
     * namespace. The user is resolved from the very snapshot being edited (the
     * `HomeDiscoveryStore.editForUser` pattern) so the write is atomic with
     * respect to concurrent user switches; pre-login there is no namespace, so
     * the write is skipped.
     */
    suspend fun setAutoDownloadServers(serverIds: Set<String>) {
        dataStore.edit { prefs ->
            val userId = identityStore.activeUserIdIn(prefs) ?: return@edit
            prefs[UserNamespacedKeys.stringKey(userId, Keys.AUTO_DOWNLOAD_SERVERS)] = json.encodeToString(serverIds)
        }
    }

    suspend fun setMaxDownloadStorageGb(gb: Int) {
        dataStore.edit { it[Keys.MAX_DOWNLOAD_STORAGE_GB] = gb }
    }

    suspend fun setDownloadStorageLocation(location: String) {
        dataStore.edit { it[Keys.DOWNLOAD_STORAGE_LOCATION] = location }
    }

    suspend fun setAutoDeleteAfterWatch(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_DELETE_AFTER_WATCH] = enabled }
    }

    suspend fun setCellularDownloadSizeWarningMb(sizeMb: Int) {
        dataStore.edit { it[Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB] = sizeMb }
    }

    suspend fun setDownloadScheduleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DOWNLOAD_SCHEDULE_ENABLED] = enabled }
    }

    suspend fun setDownloadScheduleWindow(window: DownloadScheduleWindow) {
        dataStore.edit {
            it[Keys.DOWNLOAD_SCHEDULE_START] = window.startHour
            it[Keys.DOWNLOAD_SCHEDULE_END] = window.endHour
            it[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] = window.wifiOnly
        }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. This is the downloads subset of the legacy
     * `DOWNLOADS_NETWORK` reset category — the network/offline keys now belong
     * to [com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore].
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. The download keys all sit in the legacy `DOWNLOADS_NETWORK`
     * reset category (which the network/offline keys also share).
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.DOWNLOADS_NETWORK -> listOf(
            Keys.WIFI_ONLY_DOWNLOADS,
            Keys.DOWNLOAD_CONNECTIONS,
            Keys.MAX_CONCURRENT_DOWNLOADS,
            Keys.DOWNLOAD_QUALITY,
            Keys.SMART_DOWNLOADS_ENABLED,
            Keys.AUTO_DOWNLOAD_NEW_EPISODES,
            Keys.AUTO_DOWNLOAD_LOOKAHEAD,
            Keys.AUTO_DOWNLOAD_MAX_PER_PASS,
            Keys.AUTO_DOWNLOAD_KEEP_DAYS,
            Keys.MAX_DOWNLOAD_STORAGE_GB,
            Keys.DOWNLOAD_STORAGE_LOCATION,
            Keys.AUTO_DELETE_AFTER_WATCH,
            Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB,
            Keys.DOWNLOAD_SCHEDULE_ENABLED,
            Keys.DOWNLOAD_SCHEDULE_START,
            Keys.DOWNLOAD_SCHEDULE_END,
            Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY,
            // Canonical form of the per-user namespaced allow-list key: the
            // flat slot never holds data (every read/write resolves the
            // u_<userId>:: namespace), but the coverage guard enumerates the
            // declared key objects, and the HomeDiscoveryStore precedent keeps
            // its canonical keys in the static list the same way. The namespaced
            // instances are stripped by [removeDynamicResetKeys].
            Keys.AUTO_DOWNLOAD_SERVERS,
        )
        else -> emptyList()
    }

    /**
     * Factory-reset participation for the **dynamic** allow-list key: the static
     * [resetKeysFor] list cannot express `u_<userId>::auto_download_servers`
     * entries (one set per user that has ever signed in), so the reset machinery
     * calls this inside its edit (the `HomeDiscoveryStore.removeDynamicResetKeys`
     * pattern). Canonical-suffix matching
     * ([UserNamespacedKeys.isNamespaced]) keeps it precise: an unrelated key
     * that merely starts with `u_` is never touched.
     */
    internal fun removeDynamicResetKeys(category: PreferenceResetCategory, prefs: androidx.datastore.preferences.core.MutablePreferences) {
        if (category != PreferenceResetCategory.DOWNLOADS_NETWORK) return
        prefs.asMap().keys
            .filter { UserNamespacedKeys.isNamespaced(it.name, setOf(Keys.AUTO_DOWNLOAD_SERVERS.name)) }
            .forEach { prefs.remove(it) }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences], plus the gap
     * keys [restorePreferences] omits (`cellular_download_size_warning_mb`,
     * `download_schedule_enabled`, and the schedule-window keys destructured from
     * [DownloadsSlice.downloadScheduleWindow]).
     */
    suspend fun restore(slice: DownloadsSlice) {
        dataStore.edit { it ->
            it[Keys.WIFI_ONLY_DOWNLOADS] = slice.wifiOnlyDownloads
            it[Keys.DOWNLOAD_CONNECTIONS] = slice.downloadConnections
            it[Keys.MAX_CONCURRENT_DOWNLOADS] = slice.maxConcurrentDownloads
            it[Keys.DOWNLOAD_QUALITY] = slice.downloadQuality.name
            it[Keys.SMART_DOWNLOADS_ENABLED] = slice.smartDownloadsEnabled
            it[Keys.AUTO_DOWNLOAD_NEW_EPISODES] = slice.autoDownloadNewEpisodes
            it[Keys.AUTO_DOWNLOAD_LOOKAHEAD] = slice.autoDownloadLookahead
            it[Keys.AUTO_DOWNLOAD_MAX_PER_PASS] = slice.autoDownloadMaxPerPass
            it[Keys.AUTO_DOWNLOAD_KEEP_DAYS] = slice.autoDownloadKeepDays
            it[Keys.MAX_DOWNLOAD_STORAGE_GB] = slice.maxDownloadStorageGb
            it[Keys.DOWNLOAD_STORAGE_LOCATION] = slice.downloadStorageLocation
            it[Keys.AUTO_DELETE_AFTER_WATCH] = slice.autoDeleteAfterWatch
            it[Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB] = slice.cellularDownloadSizeWarningMb
            it[Keys.DOWNLOAD_SCHEDULE_ENABLED] = slice.downloadScheduleEnabled
            it[Keys.DOWNLOAD_SCHEDULE_START] = slice.downloadScheduleWindow.startHour
            it[Keys.DOWNLOAD_SCHEDULE_END] = slice.downloadScheduleWindow.endHour
            it[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] = slice.downloadScheduleWindow.wifiOnly
            // The allow-list is namespaced: write into the active user's
            // namespace (skipped pre-login — no namespace to write into).
            val userId = identityStore.activeUserIdIn(it) ?: return@edit
            it[UserNamespacedKeys.stringKey(userId, Keys.AUTO_DOWNLOAD_SERVERS)] = json.encodeToString(slice.autoDownloadServers)
        }
    }

    companion object {
        /** Default lookahead window (episodes past the highest downloaded/watched). */
        internal const val DEFAULT_LOOKAHEAD = 3

        /** Upper bound of the lookahead picker. */
        internal const val MAX_LOOKAHEAD = 10

        /** Upper bound of the max-per-pass picker (0 = unlimited). */
        internal const val MAX_PER_PASS_LIMIT = 50
    }
}

/**
 * The downloads preference slice. Plain data class. Defaults mirror the
 * projection defaults in [DownloadsStore.read].
 */
@Immutable
@Serializable
data class DownloadsSlice(
    val wifiOnlyDownloads: Boolean = true,
    val downloadConnections: Int = 4,
    val maxConcurrentDownloads: Int = 3,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadNewEpisodes: Boolean = false,
    /** Episodes past the highest downloaded/watched per season (0 = all missing). */
    val autoDownloadLookahead: Int = DownloadsStore.DEFAULT_LOOKAHEAD,
    /** Global per-pass enqueue budget across series (0 = unlimited). */
    val autoDownloadMaxPerPass: Int = 0,
    /** Keep-days retention window for completed downloads (0 = off). */
    val autoDownloadKeepDays: Int = 0,
    /** Active user's auto-download server allow-list (empty = all servers). */
    val autoDownloadServers: Set<String> = emptySet(),
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val autoDeleteAfterWatch: Boolean = false,
    val cellularDownloadSizeWarningMb: Int = 0,
    val downloadScheduleEnabled: Boolean = false,
    val downloadScheduleWindow: DownloadScheduleWindow = DownloadScheduleWindow(),
)
