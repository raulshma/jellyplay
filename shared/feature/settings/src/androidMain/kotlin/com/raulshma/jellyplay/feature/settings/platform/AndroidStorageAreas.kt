package com.raulshma.jellyplay.feature.settings.platform

import android.content.Context
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.feature.settings.StorageAreas
import com.raulshma.jellyplay.feature.settings.StorageSizeEstimate
import com.raulshma.jellyplay.feature.settings.directorySizeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android actual of the [StorageAreas] seam: the cache/downloads/image-cache
 * size walks and the two cache-clear bodies, reading `Context` directly. The
 * walks keep the concurrent single-IO-context-switch shape.
 */
internal class AndroidStorageAreas(
    private val context: Context,
) : StorageAreas {

    override suspend fun sizeEstimateBytes(downloadStorageLocation: String): StorageSizeEstimate =
        withContext(Dispatchers.IO) {
            // Four independent recursive FS walks — collapse into a single IO
            // context-switch and run the walks concurrently rather than one
            // after another. Each walk can take seconds on large directories.
            coroutineScope {
                val cacheAsync = async { directorySizeBytes(context.cacheDir) }
                val extAsync = async { context.externalCacheDir?.let { directorySizeBytes(it) } ?: 0L }
                val dlAsync = async {
                    val downloadsDir = if (downloadStorageLocation == "EXTERNAL" && context.getExternalFilesDir(null) != null) {
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
                StorageSizeEstimate(cacheAsync.await(), extAsync.await(), dlAsync.await(), imgAsync.await())
            }
        }

    override suspend fun clearCache() {
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
    }

    override suspend fun clearImageCache() {
        withContext(Dispatchers.IO) {
            // Coil's own eviction first — the disk-cache journal stays
            // consistent with the wiped files (a bare deleteRecursively
            // leaves stale index entries behind). Parity with the desktop
            // actual (DesktopCoilImageCache), including the deliberate choice
            // to leave the MEMORY cache warm: visible artwork must not flash
            // to its blurHash mid-session.
            coil3.SingletonImageLoader.get(context).diskCache?.clear()
            // The directory walk stays as the floor: it also covers the
            // cache-disabled mode, where the loader has no disk cache to
            // clear but legacy entries from earlier runs may still exist.
            val imageDir = File(context.cacheDir, ImageCache.DIR)
            if (imageDir.exists()) {
                imageDir.deleteRecursively()
            }
        }
    }
}
