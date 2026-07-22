package com.raulshma.jellyplay.feature.settings

import android.content.Context
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Storage / download / cache / offline-network preferences plus the filesystem-derived cache size
 * state (`cacheSizeMb`, `storageBreakdown`, `cacheError`).
 *
 * The cache size is computed lazily on screen entry via [refreshCacheSize] — the screen invokes it
 * from a `LaunchedEffect(Unit)`. It is NOT computed in `init` to avoid recursive FS walks at
 * construction time (issue #11): a freshly built VM with no user ever viewing the storage screen
 * would otherwise trigger four directory walks on every process start.
 */
@HiltViewModel
class StorageSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
    private val autoDownloadScheduler: AutoDownloadScheduler,
) : JellyPlayViewModel() {

    val preferences: StateFlow<StoragePreferences> = store.storagePreferences

    val showAdvancedSettings: StateFlow<Boolean> = store.preferences
        .map { it.showAdvancedSettings }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    var cacheSizeMb by composeState(0L)
        private set

    var storageBreakdown by composeState(StorageBreakdown())
        private set

    var cacheError by composeState<String?>(null)
        private set

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { setShowAdvancedSettings(enabled) }

    /**
     * Recomputes the cache / downloads / image-cache sizes from disk. Four recursive FS walks are
     * run concurrently under a single `Dispatchers.IO` context switch. Must be invoked explicitly
     * (typically by the screen on entry) — never called from `init`.
     */
    fun refreshCacheSize() {
        launch {
            // Five independent recursive FS walks — collapse into a single IO
            // context-switch and run the walks concurrently rather than one
            // after another. Each walk can take seconds on large directories.
            val (cacheSize, externalCacheSize, downloadsSize, imagesSize) = withContext(Dispatchers.IO) {
                val cacheAsync = async { getDirSize(context.cacheDir) }
                val extAsync = async { context.externalCacheDir?.let { getDirSize(it) } ?: 0L }
                val dlAsync = async {
                    val prefs = store.preferences.value
                    val location = prefs.downloadStorageLocation
                    val downloadsDir = if (location == "EXTERNAL" && context.getExternalFilesDir(null) != null) {
                        context.getExternalFilesDir(null)!!
                    } else {
                        context.filesDir
                    }
                    getDirSize(downloadsDir)
                }
                val imgAsync = async {
                    val imageDir = File(context.cacheDir, ImageCache.DIR)
                    if (imageDir.exists()) getDirSize(imageDir) else 0L
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

    /**
     * Sums file lengths under [dir] using an explicit stack (no recursion, so a
     * deep tree cannot overflow the call stack). Symlinks inside the tree are
     * skipped to avoid following circular links, and traversal is capped at
     * [maxDepth] levels as a guard against pathological trees. The root [dir]
     * itself is never skipped, even if it is a symlink, so a cache/downloads
     * location that the OS symlinks onto external storage still reports its size.
     */
    private fun getDirSize(dir: File): Long {
        var size = 0L
        // (file, depth, isRoot) — isRoot lets us skip the symlink guard for the
        // seed entry so a legitimately-symlinked root is still measured.
        val stack = ArrayDeque<Triple<File, Int, Boolean>>()
        stack.addLast(Triple(dir, 0, true))
        val maxDepth = 10
        while (stack.isNotEmpty()) {
            val (current, depth, isRoot) = stack.removeLast()
            if (!isRoot && java.nio.file.Files.isSymbolicLink(current.toPath())) continue
            if (current.isDirectory) {
                if (depth >= maxDepth) continue
                current.listFiles()?.forEach { file ->
                    stack.addLast(Triple(file, depth + 1, false))
                }
            } else if (current.isFile) {
                size += current.length()
            }
        }
        return size
    }

    fun setWifiOnlyDownloads(enabled: Boolean) =
        editor.edit { setWifiOnlyDownloads(enabled) }

    fun setDownloadConnections(count: Int) =
        editor.edit { setDownloadConnections(count) }

    fun setMaxConcurrentDownloads(count: Int) =
        editor.edit { setMaxConcurrentDownloads(count) }

    fun setMaxCacheSize(sizeMb: Int) =
        editor.edit { setMaxCacheSize(sizeMb) }

    fun setAutoDeleteCache(enabled: Boolean) =
        editor.edit { setAutoDeleteCache(enabled) }

    fun setDownloadStorageLocation(location: String) =
        editor.edit { setDownloadStorageLocation(location) }

    fun setMaxDownloadStorageGb(gb: Int) =
        editor.edit { setMaxDownloadStorageGb(gb) }

    fun setCellularDownloadSizeWarningMb(sizeMb: Int) =
        editor.edit { setCellularDownloadSizeWarningMb(sizeMb) }

    fun setDownloadQuality(quality: DownloadQuality) =
        editor.edit { setDownloadQuality(quality) }

    fun setSmartDownloadsEnabled(enabled: Boolean) =
        editor.edit { setSmartDownloadsEnabled(enabled) }

    fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        editor.edit {
            setAutoDownloadNewEpisodes(enabled)
            autoDownloadScheduler.sync()
        }
    }

    fun setDownloadScheduleEnabled(enabled: Boolean) =
        editor.edit { setDownloadScheduleEnabled(enabled) }

    fun setDownloadScheduleWindow(window: DownloadScheduleWindow) =
        editor.edit { setDownloadScheduleWindow(window) }

    fun setMeteredNetworkBehavior(behavior: MeteredNetworkBehavior) =
        editor.edit { setMeteredNetworkBehavior(behavior) }

    fun setAdaptiveBitrateEnabled(enabled: Boolean) =
        editor.edit { setAdaptiveBitrateEnabled(enabled) }

    fun setManualBandwidthCap(cap: Long) =
        editor.edit { setManualBandwidthCap(cap) }

    fun setManualOffline(enabled: Boolean) =
        editor.edit { setManualOffline(enabled) }

    fun setAutoOfflineEnabled(enabled: Boolean) =
        editor.edit { setAutoOfflineEnabled(enabled) }

    fun setDataSaverEnabled(enabled: Boolean) =
        editor.edit { setDataSaverEnabled(enabled) }

    fun setVerboseNetworkLogging(enabled: Boolean) =
        editor.edit { setVerboseNetworkLogging(enabled) }

    fun setNetworkTimeoutPreset(preset: NetworkTimeoutPreset) =
        editor.edit { setNetworkTimeoutPreset(preset) }

    fun setUserDataSyncEnabled(enabled: Boolean) =
        editor.edit { setUserDataSyncEnabled(enabled) }

    fun setCellularStreamingQuality(quality: StreamingQuality) =
        editor.edit { setCellularStreamingQuality(quality) }
}
