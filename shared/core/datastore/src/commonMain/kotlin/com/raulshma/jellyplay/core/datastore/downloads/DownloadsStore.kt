package com.raulshma.jellyplay.core.datastore.downloads

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **downloads** preference domain: wifi-only + per-stream
 * connection counts (with the 1..6 max-concurrent invariant), download quality,
 * smart/auto downloads, storage cap + location, the cellular size warning, and
 * the schedule window.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the `MAX_CONCURRENT_DOWNLOADS` coerce invariant),
 * its read projection, and its reset-key list end-to-end. Mirrors the
 * `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class DownloadsStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

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
            Keys.MAX_DOWNLOAD_STORAGE_GB,
            Keys.DOWNLOAD_STORAGE_LOCATION,
            Keys.AUTO_DELETE_AFTER_WATCH,
            Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB,
            Keys.DOWNLOAD_SCHEDULE_ENABLED,
            Keys.DOWNLOAD_SCHEDULE_START,
            Keys.DOWNLOAD_SCHEDULE_END,
            Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY,
        )
        else -> emptyList()
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
            it[Keys.MAX_DOWNLOAD_STORAGE_GB] = slice.maxDownloadStorageGb
            it[Keys.DOWNLOAD_STORAGE_LOCATION] = slice.downloadStorageLocation
            it[Keys.AUTO_DELETE_AFTER_WATCH] = slice.autoDeleteAfterWatch
            it[Keys.CELLULAR_DOWNLOAD_SIZE_WARNING_MB] = slice.cellularDownloadSizeWarningMb
            it[Keys.DOWNLOAD_SCHEDULE_ENABLED] = slice.downloadScheduleEnabled
            it[Keys.DOWNLOAD_SCHEDULE_START] = slice.downloadScheduleWindow.startHour
            it[Keys.DOWNLOAD_SCHEDULE_END] = slice.downloadScheduleWindow.endHour
            it[Keys.DOWNLOAD_SCHEDULE_WIFI_ONLY] = slice.downloadScheduleWindow.wifiOnly
        }
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
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val autoDeleteAfterWatch: Boolean = false,
    val cellularDownloadSizeWarningMb: Int = 0,
    val downloadScheduleEnabled: Boolean = false,
    val downloadScheduleWindow: DownloadScheduleWindow = DownloadScheduleWindow(),
)
