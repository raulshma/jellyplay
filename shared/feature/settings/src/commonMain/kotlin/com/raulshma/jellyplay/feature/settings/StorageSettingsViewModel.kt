package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.model.StoragePreferences
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Filesystem-derived storage breakdown surfaced by [StorageSettingsViewModel].
 * Previously lived on the shared [SettingsViewModel]; moved here when the
 * storage accounting concern was given its own home.
 */
@Immutable
data class StorageBreakdown(
    val cacheMb: Long = 0,
    val downloadsMb: Long = 0,
    val imagesMb: Long = 0,
    val totalMb: Long = 0,
)

/**
 * Storage / download / cache / offline-network preferences plus the filesystem-derived cache size
 * state (`cacheSizeMb`, `storageBreakdown`, `cacheError`).
 *
 * The cache size is computed lazily on screen entry via [refreshCacheSize] — the screen invokes it
 * from a `LaunchedEffect(Unit)`. It is NOT computed in `init` to avoid recursive FS walks at
 * construction time: a freshly built VM with no user ever viewing the storage screen
 * would otherwise trigger four directory walks on every process start.
 *
 * The FS walks and cache clears delegate to the [StorageAreas] platform seam
 * (Android keeps the verbatim Context bodies; desktop degrades to zeros /
 * no-ops), the download-mount enumeration to [StorageMountsProvider], and the
 * auto-download scheduler poke to [AutoDownloadSync] — Wave 2 binds the
 * actuals at the Koin edge.
 */
class StorageSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val autoDownloadSync: AutoDownloadSync,
    private val storageAreas: StorageAreas,
    private val storageMountsProvider: StorageMountsProvider,
) : JellyPlayViewModel() {

    val preferences: StateFlow<StoragePreferences> = projections.storagePreferences

    /**
     * Storage mounts the user can pick for downloads.
     * Computed once on construction from [StorageMountsProvider.availableMounts]
     * (which on Android reads `Context.getExternalFilesDirs`); recomputed cheaply only when
     * the user re-enters the screen, since the mount set only changes when a
     * card/USB is inserted or removed between screen entries.
     */
    var storageMounts by composeState<List<StorageMount>>(emptyList())
        private set

    init {
        refreshStorageMounts()
    }

    private fun refreshStorageMounts() {
        launch {
            val mounts = runCatching { storageMountsProvider.availableMounts() }.getOrDefault(emptyList())
            storageMounts = mounts
        }
    }

    val showAdvancedSettings: StateFlow<Boolean> = appearanceStore.showAdvancedSettings

    var cacheSizeMb by composeState(0L)
        private set

    var storageBreakdown by composeState(StorageBreakdown())
        private set

    var cacheError by composeState<String?>(null)
        private set

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { appearance.setShowAdvancedSettings(enabled) }

    /**
     * Recomputes the cache / downloads / image-cache sizes from disk via the
     * [StorageAreas] seam (four recursive FS walks on Android, run concurrently
     * inside the actual's single IO context-switch). Must be invoked explicitly
     * (typically by the screen on entry) — never called from `init`.
     */
    fun refreshCacheSize() {
        launch {
            val location = projections.downloadPreferences.value.downloadStorageLocation
            val (cacheSize, externalCacheSize, downloadsSize, imagesSize) =
                storageAreas.sizeEstimateBytes(location)

            cacheSizeMb = (cacheSize + externalCacheSize) / (1024 * 1024)
            val downloadsMb = downloadsSize / (1024 * 1024)
            val imagesMb = imagesSize / (1024 * 1024)
            val total = cacheSizeMb + downloadsMb + imagesMb
            storageBreakdown = StorageBreakdown(
                cacheMb = cacheSizeMb,
                downloadsMb = downloadsMb,
                imagesMb = imagesMb,
                totalMb = total,
            )
        }
    }

    fun clearCache() {
        launch {
            cacheError = null
            try {
                storageAreas.clearCache()
            } catch (error: Exception) {
                cacheError = error.message ?: error::class.simpleName
            } finally {
                refreshCacheSize()
            }
        }
    }

    /**
     * Clears only the Coil image cache. Use when the user
     * explicitly wants to reclaim the image-cache bytes (the generic [clearCache]
     * deliberately preserves it to avoid mid-session image flashing).
     */
    fun clearImageCache() {
        launch {
            cacheError = null
            try {
                storageAreas.clearImageCache()
            } catch (error: Exception) {
                cacheError = error.message ?: error::class.simpleName
            } finally {
                refreshCacheSize()
            }
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) =
        editor.edit { downloads.setWifiOnlyDownloads(enabled) }

    fun setDownloadConnections(count: Int) =
        editor.edit { downloads.setDownloadConnections(count) }

    fun setMaxConcurrentDownloads(count: Int) =
        editor.edit { downloads.setMaxConcurrentDownloads(count) }

    fun setMaxCacheSize(sizeMb: Int) =
        editor.edit { networkOffline.setMaxCacheSize(sizeMb) }

    fun setAutoDeleteCache(enabled: Boolean) =
        editor.edit { networkOffline.setAutoDeleteCache(enabled) }

    fun setDownloadStorageLocation(location: String) =
        editor.edit { downloads.setDownloadStorageLocation(location) }

    fun setAutoDeleteAfterWatch(enabled: Boolean) =
        editor.edit { downloads.setAutoDeleteAfterWatch(enabled) }

    fun setMaxDownloadStorageGb(gb: Int) =
        editor.edit { downloads.setMaxDownloadStorageGb(gb) }

    fun setCellularDownloadSizeWarningMb(sizeMb: Int) =
        editor.edit { downloads.setCellularDownloadSizeWarningMb(sizeMb) }

    fun setDownloadQuality(quality: DownloadQuality) =
        editor.edit { downloads.setDownloadQuality(quality) }

    fun setSmartDownloadsEnabled(enabled: Boolean) =
        editor.edit { downloads.setSmartDownloadsEnabled(enabled) }

    fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        editor.edit {
            downloads.setAutoDownloadNewEpisodes(enabled)
            autoDownloadSync.sync()
        }
    }

    fun setDownloadScheduleEnabled(enabled: Boolean) =
        editor.edit { downloads.setDownloadScheduleEnabled(enabled) }

    fun setDownloadScheduleWindow(window: DownloadScheduleWindow) =
        editor.edit { downloads.setDownloadScheduleWindow(window) }

    fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) =
        editor.edit { networkOffline.setMeteredNetworkBehavior(behavior) }

    fun setAdaptiveBitrateEnabled(enabled: Boolean) =
        editor.edit { networkOffline.setAdaptiveBitrateEnabled(enabled) }

    fun setManualBandwidthCap(cap: Long) =
        editor.edit { networkOffline.setManualBandwidthCap(cap) }

    fun setManualOffline(enabled: Boolean) =
        editor.edit { networkOffline.setManualOffline(enabled) }

    fun setAutoOfflineEnabled(enabled: Boolean) =
        editor.edit { networkOffline.setAutoOfflineEnabled(enabled) }

    fun setDataSaverEnabled(enabled: Boolean) =
        editor.edit { networkOffline.setDataSaverEnabled(enabled) }

    fun setVerboseNetworkLogging(enabled: Boolean) =
        editor.edit { networkOffline.setVerboseNetworkLogging(enabled) }

    fun setNetworkTimeoutPreset(preset: NetworkTimeoutPreset) =
        editor.edit { networkOffline.setNetworkTimeoutPreset(preset) }

    fun setUserDataSyncEnabled(enabled: Boolean) =
        editor.edit { playback.setUserDataSyncEnabled(enabled) }

    fun setCellularStreamingQuality(quality: StreamingQuality) =
        editor.edit { playback.setCellularStreamingQuality(quality) }
}
