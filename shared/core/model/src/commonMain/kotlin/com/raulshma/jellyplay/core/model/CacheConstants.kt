package com.raulshma.jellyplay.core.model

/**
 * Filesystem cache constants shared across modules.
 *
 * [ImageCache] is the Coil disk-cache directory used by the app's `ImageLoader`
 * (see `JellyPlayApplication`). It is referenced by the storage-settings VMs both
 * for size measurement and to preserve it during a generic cache clear (wiping it
 * mid-session causes every visible image to re-decode and flash to its blurHash).
 */
object ImageCache {
    /** Subdirectory of the app's `cacheDir` that Coil writes decoded bitmaps to. */
    const val DIR = "image_cache"
}
