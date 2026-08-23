package com.raulshma.jellyplay.feature.settings.platform

import com.raulshma.jellyplay.feature.settings.StorageAreas
import com.raulshma.jellyplay.feature.settings.StorageSizeEstimate

/**
 * Desktop actual of the [StorageAreas] seam — declared degrade:
 *  - sizing: every bucket reports 0 bytes. The desktop app does not keep the
 *    Android cacheDir/externalCacheDir layout, and no desktop data seam owns
 *    the `~/.jellyplay` cache/downloads roots yet (the downloads conveyor
 *    owns those paths); the screen renders "0 MB" until one lands.
 *  - clears: best-effort no-op for the same reason — there is no desktop-owned
 *    cache directory to wipe here.
 */
internal class DesktopStorageAreas : StorageAreas {

    override suspend fun sizeEstimateBytes(downloadStorageLocation: String): StorageSizeEstimate =
        StorageSizeEstimate(
            cacheBytes = 0L,
            externalCacheBytes = 0L,
            downloadsBytes = 0L,
            imageCacheBytes = 0L,
        )

    override suspend fun clearCache() {}

    override suspend fun clearImageCache() {}
}
