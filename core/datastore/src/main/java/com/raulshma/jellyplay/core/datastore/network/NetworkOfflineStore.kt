package com.raulshma.jellyplay.core.datastore.network

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning the **network / offline-mode** preference domain: manual +
 * auto offline mode, the manual bandwidth cap, the metered-network behaviour,
 * adaptive-bitrate, data-saver, verbose-logging, the network timeout preset,
 * and the image-cache size + auto-delete toggles.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters, its read projection, and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Note:** `incognito_mode_enabled` is intentionally NOT owned here — it
 * belongs to the playback/player domain (`VideoPlayerStore`).
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class NetworkOfflineStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val MANUAL_OFFLINE_ENABLED = booleanPreferencesKey("manual_offline_enabled")
        val AUTO_OFFLINE_ENABLED = booleanPreferencesKey("auto_offline_enabled")
        val MANUAL_BANDWIDTH_CAP = longPreferencesKey("manual_bandwidth_cap")
        val METERED_NETWORK_BEHAVIOR = stringPreferencesKey("metered_network_behavior")
        val ADAPTIVE_BITRATE_ENABLED = booleanPreferencesKey("adaptive_bitrate_enabled")
        val DATA_SAVER_ENABLED = booleanPreferencesKey("data_saver_enabled")
        val VERBOSE_NETWORK_LOGGING = booleanPreferencesKey("verbose_network_logging")
        val NETWORK_TIMEOUT_PRESET = stringPreferencesKey("network_timeout_preset")
        val MAX_CACHE_SIZE_MB = intPreferencesKey("max_cache_size_mb")
        val AUTO_DELETE_CACHE = booleanPreferencesKey("auto_delete_cache")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val networkOffline: StateFlow<NetworkOfflineSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, NetworkOfflineSlice())

    internal fun read(prefs: Preferences): NetworkOfflineSlice = NetworkOfflineSlice(
        manualOfflineEnabled = PreferenceCodec.readBool(prefs, Keys.MANUAL_OFFLINE_ENABLED, "manual_offline_enabled", false),
        autoOfflineEnabled = PreferenceCodec.readBool(prefs, Keys.AUTO_OFFLINE_ENABLED, "auto_offline_enabled", true),
        manualBandwidthCap = prefs[Keys.MANUAL_BANDWIDTH_CAP] ?: 0L,
        meteredNetworkBehavior = readMeteredNetworkBehavior(prefs),
        adaptiveBitrateEnabled = PreferenceCodec.readBool(prefs, Keys.ADAPTIVE_BITRATE_ENABLED, "adaptive_bitrate_enabled", true),
        dataSaverEnabled = PreferenceCodec.readBool(prefs, Keys.DATA_SAVER_ENABLED, "data_saver_enabled", false),
        verboseNetworkLogging = PreferenceCodec.readBool(prefs, Keys.VERBOSE_NETWORK_LOGGING, "verbose_network_logging", false),
        networkTimeoutPreset = readNetworkTimeoutPreset(prefs),
        maxCacheSizeMb = PreferenceCodec.readInt(prefs, Keys.MAX_CACHE_SIZE_MB, "max_cache_size_mb", 0),
        autoDeleteCache = PreferenceCodec.readBool(prefs, Keys.AUTO_DELETE_CACHE, "auto_delete_cache", true),
    )

    private fun readMeteredNetworkBehavior(prefs: Preferences): MeteredNetworkBehavior = try {
        MeteredNetworkBehavior.valueOf(prefs[Keys.METERED_NETWORK_BEHAVIOR] ?: MeteredNetworkBehavior.WARN.name)
    } catch (_: Exception) { MeteredNetworkBehavior.WARN }

    private fun readNetworkTimeoutPreset(prefs: Preferences): NetworkTimeoutPreset = try {
        NetworkTimeoutPreset.valueOf(prefs[Keys.NETWORK_TIMEOUT_PRESET] ?: NetworkTimeoutPreset.DEFAULT.name)
    } catch (_: Exception) { NetworkTimeoutPreset.DEFAULT }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setManualOffline(enabled: Boolean) {
        dataStore.edit { it[Keys.MANUAL_OFFLINE_ENABLED] = enabled }
    }

    suspend fun setAutoOfflineEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_OFFLINE_ENABLED] = enabled }
    }

    suspend fun setManualBandwidthCap(cap: Long) {
        dataStore.edit { it[Keys.MANUAL_BANDWIDTH_CAP] = cap }
    }

    suspend fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) {
        dataStore.edit { it[Keys.METERED_NETWORK_BEHAVIOR] = behavior.name }
    }

    suspend fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ADAPTIVE_BITRATE_ENABLED] = enabled }
    }

    suspend fun setDataSaverEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DATA_SAVER_ENABLED] = enabled }
    }

    suspend fun setVerboseNetworkLogging(enabled: Boolean) {
        dataStore.edit { it[Keys.VERBOSE_NETWORK_LOGGING] = enabled }
    }

    suspend fun setNetworkTimeoutPreset(preset: NetworkTimeoutPreset) {
        dataStore.edit { it[Keys.NETWORK_TIMEOUT_PRESET] = preset.name }
    }

    suspend fun setMaxCacheSize(sizeMb: Int) {
        dataStore.edit { it[Keys.MAX_CACHE_SIZE_MB] = sizeMb }
    }

    suspend fun setAutoDeleteCache(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_DELETE_CACHE] = enabled }
    }

    /**
     * Keys owned by this store, for factory-reset participation. This is the
     * network/offline subset of the legacy `DOWNLOADS_NETWORK` reset category —
     * the download keys now belong to
     * [com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore].
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.MAX_CACHE_SIZE_MB, Keys.AUTO_DELETE_CACHE,
        Keys.MANUAL_OFFLINE_ENABLED, Keys.AUTO_OFFLINE_ENABLED,
        Keys.MANUAL_BANDWIDTH_CAP, Keys.METERED_NETWORK_BEHAVIOR,
        Keys.ADAPTIVE_BITRATE_ENABLED, Keys.DATA_SAVER_ENABLED,
        Keys.VERBOSE_NETWORK_LOGGING, Keys.NETWORK_TIMEOUT_PRESET,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. The network/offline keys (plus the image-cache size + auto
     * delete toggles) all sit in the legacy `DOWNLOADS_NETWORK` reset category.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.DOWNLOADS_NETWORK -> listOf(
            Keys.MAX_CACHE_SIZE_MB,
            Keys.AUTO_DELETE_CACHE,
            Keys.MANUAL_OFFLINE_ENABLED,
            Keys.AUTO_OFFLINE_ENABLED,
            Keys.MANUAL_BANDWIDTH_CAP,
            Keys.METERED_NETWORK_BEHAVIOR,
            Keys.ADAPTIVE_BITRATE_ENABLED,
            Keys.DATA_SAVER_ENABLED,
            Keys.VERBOSE_NETWORK_LOGGING,
            Keys.NETWORK_TIMEOUT_PRESET,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the network/offline keys owned by
     * this store from a decoded [UserPreferences]. The facade calls this (and
     * every other store's hook) instead of writing these keys itself.
     *
     * Mirrors the legacy facade behaviour exactly.
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.MAX_CACHE_SIZE_MB] = userPreferences.maxCacheSizeMb
            it[Keys.AUTO_DELETE_CACHE] = userPreferences.autoDeleteCache
            it[Keys.MANUAL_OFFLINE_ENABLED] = userPreferences.manualOfflineEnabled
            it[Keys.AUTO_OFFLINE_ENABLED] = userPreferences.autoOfflineEnabled
            it[Keys.MANUAL_BANDWIDTH_CAP] = userPreferences.manualBandwidthCap
            it[Keys.METERED_NETWORK_BEHAVIOR] = userPreferences.meteredNetworkBehavior.name
            it[Keys.ADAPTIVE_BITRATE_ENABLED] = userPreferences.adaptiveBitrateEnabled
            it[Keys.DATA_SAVER_ENABLED] = userPreferences.dataSaverEnabled
            it[Keys.VERBOSE_NETWORK_LOGGING] = userPreferences.verboseNetworkLogging
            it[Keys.NETWORK_TIMEOUT_PRESET] = userPreferences.networkTimeoutPreset.name
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences].
     */
    suspend fun restore(slice: NetworkOfflineSlice) {
        dataStore.edit { it ->
            it[Keys.MAX_CACHE_SIZE_MB] = slice.maxCacheSizeMb
            it[Keys.AUTO_DELETE_CACHE] = slice.autoDeleteCache
            it[Keys.MANUAL_OFFLINE_ENABLED] = slice.manualOfflineEnabled
            it[Keys.AUTO_OFFLINE_ENABLED] = slice.autoOfflineEnabled
            it[Keys.MANUAL_BANDWIDTH_CAP] = slice.manualBandwidthCap
            it[Keys.METERED_NETWORK_BEHAVIOR] = slice.meteredNetworkBehavior.name
            it[Keys.ADAPTIVE_BITRATE_ENABLED] = slice.adaptiveBitrateEnabled
            it[Keys.DATA_SAVER_ENABLED] = slice.dataSaverEnabled
            it[Keys.VERBOSE_NETWORK_LOGGING] = slice.verboseNetworkLogging
            it[Keys.NETWORK_TIMEOUT_PRESET] = slice.networkTimeoutPreset.name
        }
    }
}

/**
 * The network / offline-mode preference slice. Plain data class. Defaults mirror
 * the projection defaults in [NetworkOfflineStore.read].
 */
@Immutable
@Serializable
data class NetworkOfflineSlice(
    val manualOfflineEnabled: Boolean = false,
    val autoOfflineEnabled: Boolean = true,
    val manualBandwidthCap: Long = 0L,
    val meteredNetworkBehavior: MeteredNetworkBehavior = MeteredNetworkBehavior.WARN,
    val adaptiveBitrateEnabled: Boolean = true,
    val dataSaverEnabled: Boolean = false,
    val verboseNetworkLogging: Boolean = false,
    val networkTimeoutPreset: NetworkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
    val maxCacheSizeMb: Int = 0,
    val autoDeleteCache: Boolean = true,
)
