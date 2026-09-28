package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.StoragePreferences
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
 * (Android keeps the verbatim Context bodies; desktop walks its own
 * downloads/http-cache roots), the download-mount enumeration to
 * [StorageMountsProvider], the auto-download scheduler poke to
 * [AutoDownloadSync], and the keep-days "Clean up now" action to
 * [AutoDownloadCleanup] — the Koin edge binds the actuals.
 *
 * The configured-server list ([servers]) feeds the auto-download allow-list
 * multi-select — the same [AuthRepository.servers] flow the server-management
 * screen renders.
 */
class StorageSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    private val autoDownloadSync: AutoDownloadSync,
    private val autoDownloadCleanup: AutoDownloadCleanup,
    private val storageAreas: StorageAreas,
    private val storageMountsProvider: StorageMountsProvider,
    authRepository: AuthRepository,
) : SettingsSectionViewModel(advancedSettings, editor) {

    val preferences: StateFlow<StoragePreferences> = projections.storagePreferences

    /** Every configured server — the allow-list picker's option source. */
    var servers by composeState<List<ServerInfo>>(emptyList())
        private set

    init {
        launch {
            authRepository.servers.collect { serverList ->
                servers = serverList
            }
        }
        refreshStorageMounts()
    }

    private fun refreshStorageMounts() {
        launch {
            val mounts = runCatching { storageMountsProvider.availableMounts() }.getOrDefault(emptyList())
            storageMounts = mounts
        }
    }

    /**
     * Storage mounts the user can pick for downloads.
     * Computed once on construction from [StorageMountsProvider.availableMounts]
     * (which on Android reads `Context.getExternalFilesDirs`); recomputed cheaply only when
     * the user re-enters the screen, since the mount set only changes when a
     * card/USB is inserted or removed between screen entries.
     */
    var storageMounts by composeState<List<StorageMount>>(emptyList())
        private set

    var cacheSizeMb by composeState(0L)
        private set

    var storageBreakdown by composeState(StorageBreakdown())
        private set

    var cacheError by composeState<String?>(null)
        private set

    /** Summary of the last "Clean up now" pass, rendered beside the row. */
    var lastCleanupSummary by composeState<AutoDownloadCleanupSummary?>(null)
        private set

    /** True while the on-demand retention sweep is running (the row shows progress). */
    var isCleaningUp by composeState(false)
        private set

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

    /** Store write + auto-download scheduler poke against the updated config. */
    fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        editor.edit {
            downloads.setAutoDownloadNewEpisodes(enabled)
            autoDownloadSync.sync()
        }
    }

    /**
     * One on-demand keep-days retention pass (the downloads block's
     * "Clean up now" action). Runs on the VM scope; the summary is surfaced
     * beside the row.
     */
    fun cleanupDownloadsNow() {
        if (isCleaningUp) return
        launch {
            isCleaningUp = true
            try {
                lastCleanupSummary = autoDownloadCleanup.cleanupNow()
            } finally {
                isCleaningUp = false
            }
        }
    }
}
