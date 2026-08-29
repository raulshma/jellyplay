package com.raulshma.jellyplay.feature.settings.platform

/**
 * The desktop image-cache handle behind [StorageAreas]' image-cache bucket
 * and clear action (wave 21B). The desktop image pipeline is Coil, and its
 * persistent cache is NOT memory-only: coil3's default desktop loader (the
 * app shell's Main.kt configures no custom disk cache) writes decoded files
 * to the coil3 process-wide singleton disk cache under the SYSTEM TEMP
 * directory — outside this module's ownership, so the shell injects the two
 * operations when it loads desktopSettingsPlatformModule instead of the
 * settings module learning the image pipeline.
 *
 * Public because the shell (apps/desktop) implements it; the seam's other
 * consumers are [DesktopStorageAreas] and its jvmTest hand-fakes.
 */
interface DesktopImageCacheOps {

    /** Current size of the persistent image cache, in bytes. */
    suspend fun sizeEstimateBytes(): Long

    /** Clears the persistent image cache (the desktop twin of Android's `image_cache` wipe). */
    suspend fun clear()
}
