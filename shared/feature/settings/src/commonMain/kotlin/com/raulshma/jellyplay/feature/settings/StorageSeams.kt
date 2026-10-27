package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam behind the storage screen's filesystem-derived numbers and
 * destructive cache actions: the Android actual
 * (platform/AndroidStorageAreas) walks `Context.cacheDir` /
 * `externalCacheDir` / `filesDir` / `getExternalFilesDir` directly; the
 * desktop actual walks the roots the desktop data seams own
 * (platform/DesktopStorageAreas: `<dataDir>/downloads` +
 * `<configDir>/http-cache`, image cache through a shell-injected handle).
 * Suspend so each actual owns its own dispatcher (commonMain has no
 * Dispatchers.IO). Also backs the Privacy & Data hub's clear-cache /
 * clear-image-cache actions (same bodies, one seam).
 */
interface StorageAreas {

    /**
     * Size estimates for the storage-breakdown bar. [downloadStorageLocation]
     * is the current `downloadStorageLocation` preference — the Android actual
     * resolves the downloads dir (`"EXTERNAL"` → app-private external root,
     * else `filesDir`).
     */
    suspend fun sizeEstimateBytes(downloadStorageLocation: String): StorageSizeEstimate

    /**
     * Deletes the contents of the cache dirs *except* the Coil image cache
     * (`image_cache`) — wiping it mid-session causes every visible image to
     * re-decode and flash to its blurHash.
     */
    suspend fun clearCache()

    /** Clears only the Coil image cache directory. */
    suspend fun clearImageCache()
}

/** Per-bucket byte estimates surfaced by [StorageAreas.sizeEstimateBytes]. */
data class StorageSizeEstimate(
    val cacheBytes: Long,
    val externalCacheBytes: Long,
    val downloadsBytes: Long,
    val imageCacheBytes: Long,
)

/**
 * Platform seam for the auto-download work-manager sync
 * (`AutoDownloadScheduler.sync()`: Android WorkManager enqueue/cancel;
 * non-suspend, fire-and-forget). Desktop has no scheduler — the desktop module
 * registers a no-op at the Koin edge.
 */
fun interface AutoDownloadSync {
    fun sync()

    /**
     * The storage screen's "Check for new episodes now" action: an immediate
     * one-shot discovery pass. Defaults to [sync] (both platform schedules
     * run a check as part of it) so the SAM-conversion bindings keep working;
     * Android overrides it with the distinct `enqueueNow()` one-shot.
     */
    fun checkNow() = sync()
}

/** What one keep-days retention pass reclaimed — the "Clean up now" summary. */
data class AutoDownloadCleanupSummary(
    val deletedCount: Int,
    val bytesReclaimed: Long,
)

/**
 * Seam behind the storage screen's "Clean up now" action: one on-demand pass of
 * the auto-download keep-days retention sweep
 * (`DownloadRepository.sweepExpiredAutoDownloads` — watched completed downloads
 * older than the keep-days window; unwatched ones are never deleted). Bound at
 * the Koin edge like [AutoDownloadSync]: the Android app module wraps the
 * repository single; the desktop platform module resolves it directly
 * (feature/settings cannot see core:data's repository types from its common
 * source set — the same visibility split [StorageAreas] documents).
 */
fun interface AutoDownloadCleanup {
    suspend fun cleanupNow(): AutoDownloadCleanupSummary
}

/**
 * Platform seam enumerating the download destinations the user can pick.
 * The Android actual (platform/AndroidStorageMountsProvider) enumerates mounts
 * with pure framework calls (filesDir + `getExternalFilesDirs` + StatFs — no
 * core:data types), so no app-side Koin override is needed; desktop reports
 * its single volume.
 */
fun interface StorageMountsProvider {
    suspend fun availableMounts(): List<StorageMount>
}

/**
 * One selectable download destination surfaced by
 * [StorageMountsProvider.availableMounts] (module-local counterpart of
 * core:data's `StorageMount`, which is not visible from this module).
 *
 * @property prefValue the value to persist as `downloadStorageLocation`
 *   ("INTERNAL" / "EXTERNAL" / "EXTERNAL_N").
 * @property kind coarse label class the UI maps to a localized string.
 * @property availableBytes free bytes on the mount, or 0 if unavailable.
 * @property rootPath absolute path of the app-private root (for display).
 */
data class StorageMount(
    val prefValue: String,
    val kind: StorageMountKind,
    val availableBytes: Long,
    val rootPath: String,
)

/**
 * Coarse classification of a [StorageMount] for UI labeling. The settings
 * screen maps each to a localized string (`settings_storage_internal`, etc.).
 */
enum class StorageMountKind {
    /** App-private `filesDir` (built-in flash, never media-scanned). */
    INTERNAL,

    /** Primary emulated external storage (built-in flash, app-private). */
    PRIMARY_EXTERNAL,

    /** A real removable mount (SD card / USB) reported by the OS. */
    REMOVABLE,

    /** Secondary non-removable external mount (e.g. adopted storage). */
    EXTERNAL,
}
