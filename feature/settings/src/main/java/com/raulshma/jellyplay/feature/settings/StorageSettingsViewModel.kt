package com.raulshma.jellyplay.feature.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayout
import com.raulshma.jellyplay.core.data.repository.StorageMount
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.model.StoragePreferences
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

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
 */
@HiltViewModel
class StorageSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val autoDownloadScheduler: AutoDownloadScheduler,
    private val storageLayout: DownloadStorageLayout,
) : JellyPlayViewModel() {

    val preferences: StateFlow<StoragePreferences> = projections.storagePreferences

    /**
     * Storage mounts the user can pick for downloads.
     * Computed once on construction from `DownloadStorageLayout.availableMounts`
     * (which reads `Context.getExternalFilesDirs`); recomputed cheaply only when
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
            val mounts = withContext(Dispatchers.IO) {
                runCatching { storageLayout.availableMounts() }.getOrDefault(emptyList())
            }
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
     * Recomputes the cache / downloads / image-cache sizes from disk. Four recursive FS walks are
     * run concurrently under a single `Dispatchers.IO` context switch. Must be invoked explicitly
     * (typically by the screen on entry) — never called from `init`.
     */
    fun refreshCacheSize() {
        launch {
            // Four independent recursive FS walks — collapse into a single IO
            // context-switch and run the walks concurrently rather than one
            // after another. Each walk can take seconds on large directories.
            val (cacheSize, externalCacheSize, downloadsSize, imagesSize) = withContext(Dispatchers.IO) {
                val cacheAsync = async { directorySizeBytes(context.cacheDir) }
                val extAsync = async { context.externalCacheDir?.let { directorySizeBytes(it) } ?: 0L }
                val dlAsync = async {
                    val location = projections.downloadPreferences.value.downloadStorageLocation
                    val downloadsDir = if (location == "EXTERNAL" && context.getExternalFilesDir(null) != null) {
                        context.getExternalFilesDir(null)!!
                    } else {
                        context.filesDir
                    }
                    directorySizeBytes(downloadsDir)
                }
                val imgAsync = async {
                    val imageDir = File(context.cacheDir, ImageCache.DIR)
                    if (imageDir.exists()) directorySizeBytes(imageDir) else 0L
                }
                QuadLongs(cacheAsync.await(), extAsync.await(), dlAsync.await(), imgAsync.await())
            }

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
                withContext(Dispatchers.IO) {
                    // Delete the contents of cacheDir *except* the Coil image cache.
                    // Wiping image_cache mid-session causes every visible image to
                    // re-decode and flash to its blurHash for several seconds.
                    context.cacheDir.listFiles()?.forEach { child ->
                        if (child.name != ImageCache.DIR) {
                            child.deleteRecursively()
                        }
                    }
                    val externalCache = context.externalCacheDir
                    if (externalCache != null && externalCache.exists()) {
                        externalCache.listFiles()?.forEach { child ->
                            if (child.name != ImageCache.DIR) {
                                child.deleteRecursively()
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                cacheError = error.message ?: error::class.simpleName
            } finally {
                refreshCacheSize()
            }
        }
    }

    /**
     * Clears only the Coil image cache ([ImageCache.DIR]). Use when the user
     * explicitly wants to reclaim the image-cache bytes (the generic [clearCache]
     * deliberately preserves it to avoid mid-session image flashing).
     */
    fun clearImageCache() {
        launch {
            cacheError = null
            try {
                withContext(Dispatchers.IO) {
                    val imageDir = File(context.cacheDir, ImageCache.DIR)
                    if (imageDir.exists()) {
                        imageDir.deleteRecursively()
                    }
                }
            } catch (error: Exception) {
                cacheError = error.message ?: error::class.simpleName
            } finally {
                refreshCacheSize()
            }
        }
    }

    /** 4-tuple of `Long` for destructuring the four parallel FS-walk results. */
    private class QuadLongs(
        val first: Long,
        val second: Long,
        val third: Long,
        val fourth: Long,
    ) {
        operator fun component1(): Long = first
        operator fun component2(): Long = second
        operator fun component3(): Long = third
        operator fun component4(): Long = fourth
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
            autoDownloadScheduler.sync()
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
