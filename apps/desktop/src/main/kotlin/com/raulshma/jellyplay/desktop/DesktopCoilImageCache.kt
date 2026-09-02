package com.raulshma.jellyplay.desktop

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.raulshma.jellyplay.feature.settings.platform.DesktopImageCacheOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The shell's [DesktopImageCacheOps] over the desktop image pipeline (wave
 * 21B). Desktop Coil is NOT memory-only: Main.kt builds its loader with no
 * custom disk cache, so coil3's default applies — the process-wide singleton
 * DiskCache under the SYSTEM TEMP directory
 * (`<java.io.tmpdir>/coil3_disk_cache`, sized 2% of that volume clamped to
 * 10–250 MB), which every request with the default ENABLED disk-cache
 * policy writes decoded files to.
 *
 * Both operations go through the shell's SingletonImageLoader instance
 * (Main.kt's setSafe factory) and its `diskCache` — so whatever disk cache
 * the loader's builder resolves is exactly what the settings screen measures
 * and clears. The type is nullable (coil3 allows a disk-cache-less loader);
 * this shell's builder always resolves one, and a hypothetical null
 * degrades to 0 bytes / no-op rather than crashing the settings screen.
 * Only the disk cache is cleared: the memory cache is left warm, the same
 * mid-session behavior as Android's image_cache wipe (visible artwork must
 * not flash to its blurHash).
 */
internal fun desktopCoilImageCacheOps(): DesktopImageCacheOps =
    object : DesktopImageCacheOps {
        private fun diskCache(): DiskCache? =
            SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache

        override suspend fun sizeEstimateBytes(): Long =
            withContext(Dispatchers.IO) { diskCache()?.size ?: 0L }

        override suspend fun clear(): Unit =
            withContext(Dispatchers.IO) { diskCache()?.clear() }
    }
