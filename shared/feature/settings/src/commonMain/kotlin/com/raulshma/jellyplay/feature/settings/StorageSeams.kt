package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam behind the storage screen's filesystem-derived numbers and
 * destructive cache actions (AudioCacheClearer pattern): the legacy ViewModel
 * bodies walked `Context.cacheDir` / `externalCacheDir` / `filesDir` /
 * `getExternalFilesDir` directly, which is Android-only surface. The Android
 * actual (platform/AndroidStorageAreas) keeps those bodies verbatim; the
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
     * resolves the downloads dir the same way the legacy VM did
     * (`"EXTERNAL"` → app-private external root, else `filesDir`).
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
 * Platform seam for the auto-download work-manager sync the legacy VM reached
 * through `AutoDownloadScheduler.sync()` (Android WorkManager enqueue/cancel;
 * non-suspend, fire-and-forget). Desktop has no scheduler yet — Wave 2
 * registers a no-op at the Koin edge.
 */
fun interface AutoDownloadSync {
    fun sync()
}

/**
 * Platform seam enumerating the download destinations the user can pick.
 * The Android actual (platform/AndroidStorageMountsProvider) inlines the pure
 * framework body of the legacy `DownloadStorageLayout.availableMounts()`
 * (filesDir + `getExternalFilesDirs` + StatFs — no legacy core:data types), so
 * no app-side Koin override is needed; desktop reports its single volume.
 */
fun interface StorageMountsProvider {
    suspend fun availableMounts(): List<StorageMount>
}

/**
 * One selectable download destination surfaced by
 * [StorageMountsProvider.availableMounts] (module-local copy of the legacy
 * core:data `StorageMount` — that type is not visible from this module).
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
