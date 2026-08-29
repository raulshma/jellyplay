package com.raulshma.jellyplay.feature.settings.platform

import com.raulshma.jellyplay.feature.settings.StorageAreas
import com.raulshma.jellyplay.feature.settings.StorageSizeEstimate
import com.raulshma.jellyplay.feature.settings.directorySizeBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop actual of the [StorageAreas] seam (wave 21B — replaces the declared
 * zeros/no-ops degrade whose "no desktop data seam owns the roots yet" note
 * outlived the downloads conveyor). The desktop app owns exactly two
 * persistent cache/downloads roots today, and this actual walks/clears
 * precisely those:
 *  - downloads bucket: [downloadsRoot] — `<dataDir>/downloads` (audio under
 *    the nested `downloads/music`), the same root
 *    [com.raulshma.jellyplay.core.data.repository.DesktopDownloadStorageLayout]
 *    resolves every download into. [downloadStorageLocation] is ignored —
 *    desktop has a single volume, the same documented choice as the storage
 *    layout and [DesktopStorageMountsProvider].
 *  - cache bucket: [httpCacheRoot] — `<configDir>/http-cache`, the OkHttp
 *    response cache DesktopNetworkModule builds the base client against (the
 *    desktop twin of Android's cacheDir walk). No image-cache exclusion is
 *    needed, unlike Android: the desktop image cache is NOT nested under
 *    this root.
 *  - image-cache bucket + [clearImageCache]: the shell-injected
 *    [DesktopImageCacheOps] handle (Coil lives in the app shell, not this
 *    module).
 *  - external-cache bucket: always 0 — desktop has a single volume.
 */
internal class DesktopStorageAreas(
    private val downloadsRoot: File,
    private val httpCacheRoot: File,
    private val imageCache: DesktopImageCacheOps,
) : StorageAreas {

    override suspend fun sizeEstimateBytes(downloadStorageLocation: String): StorageSizeEstimate =
        withContext(Dispatchers.IO) {
            StorageSizeEstimate(
                cacheBytes = directorySizeBytes(httpCacheRoot),
                externalCacheBytes = 0L,
                downloadsBytes = directorySizeBytes(downloadsRoot),
                imageCacheBytes = imageCache.sizeEstimateBytes(),
            )
        }

    override suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            clearDirectoryContents(httpCacheRoot)
        }
    }

    override suspend fun clearImageCache() {
        imageCache.clear()
    }
}

/**
 * Deletes the CONTENTS of [dir] but keeps [dir] itself. The http-cache root
 * belongs to a live OkHttp Cache instance, which recreates its journal
 * inside the existing directory on the next request — deleting the children
 * (journal + response files) is the wipe; keeping the root means OkHttp's
 * cache directory never vanishes under it. A missing root is a no-op (the
 * cache is created lazily on first use).
 */
internal fun clearDirectoryContents(dir: File) {
    if (!dir.exists()) return
    dir.listFiles()?.forEach { child -> child.deleteRecursively() }
}
