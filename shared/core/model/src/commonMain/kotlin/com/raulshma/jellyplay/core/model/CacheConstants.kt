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

/**
 * The `max_cache_size_mb` sentinel that disables caching entirely (the picker's
 * "Disabled" entry). Distinct from `0`, which the picker labels "Unlimited".
 * Consumers: the Coil builder policy (`JellyPlayImageLoader`), the OkHttp
 * response cache sizing (`baseOkHttpClient`), and the storage-settings picker.
 */
const val CACHE_SIZE_DISABLED = -1
