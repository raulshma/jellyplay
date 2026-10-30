package com.raulshma.jellyplay.core.data.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import com.raulshma.jellyplay.core.model.CACHE_SIZE_DISABLED
import okhttp3.OkHttpClient
import okio.Path

/**
 * The shared JVM Coil builder policy (the fold of the two shells' hand-copied
 * `ImageLoader.Builder` blocks in `JellyPlayApplication` and the desktop
 * `Main.kt`): OkHttp fetcher over the shell's image client,
 * `serviceLoaderEnabled(false)`, crossfade, and the lazily-sized
 * `ImageCache.DIR` disk cache.
 *
 * What is deliberately NOT here (per-shell divergences, supplied through the
 * parameters): the image CLIENT itself — Android derives it from the base
 * OkHttp client with `.cache(null)` so image bytes are not written to disk
 * twice, desktop uses the Koin-owned STREAMING client whose self-signed-trust
 * layer must be the one artwork handshakes through — and the memory-cache
 * policy, which only Android tunes (RAM-tiered `maxSizePercent`); desktop
 * keeps Coil's default via the no-op [configureMemoryCache].
 *
 * ServiceLoader is disabled STRUCTURALLY, not by registration-order luck: the
 * explicit OkHttp fetcher below always wins the component match, so Coil's
 * ServiceLoader discovery of its default fetcher is dead work — and skipping
 * it means the ServiceLoader factory can never resurrect a default-client
 * fetcher behind our back (a review round found it dormant-first-match-loser;
 * keep it that way by construction).
 *
 * [diskCacheDirectory] and [maxCacheSizeMb] are LAMBDAS read inside the
 * `diskCache {}` builder block because Coil builds the DiskCache lazily on
 * its FIRST access (the first networked image write), not at ImageLoader
 * construction — by then the shells' `NetworkOfflineStore` prewarm has long
 * since published the persisted slice, so the sizing read resolves the
 * user-configured value instead of a cold default (the documented
 * lazily-sized-DiskCache race; both shells launch the prewarm ahead of the
 * first image load).
 *
 * [CACHE_SIZE_DISABLED] cannot ride that lazy path: Coil's
 * `diskCachePolicy` is builder-level, so the disabled check reads the slice
 * eagerly at loader construction — the first image request in both shells,
 * well after `onCreate` fires the prewarm launch, and a lost race (wedged
 * DataStore) degrades benignly to an enabled unlimited cache for one session,
 * self-correcting on the next launch. When disabled, the loader skips the
 * DiskCache entirely (`CachePolicy.DISABLED` — Coil rejects a 0-byte cache,
 * and attaching one and never writing is not expressible), and deletes the
 * previous run's `ImageCache.DIR` contents: without the eviction the disabled
 * state would leave every stale entry on disk forever, since the policy never
 * touches existing files again. The delete is uncontended — the loader is
 * brand new, no request has run through it yet.
 */
fun jellyPlayImageLoader(
    platformContext: PlatformContext,
    imageClient: () -> OkHttpClient,
    diskCacheDirectory: () -> Path,
    maxCacheSizeMb: () -> Int,
    configureMemoryCache: ImageLoader.Builder.() -> Unit = {},
): ImageLoader {
    val diskCacheDisabled = maxCacheSizeMb() == CACHE_SIZE_DISABLED
    if (diskCacheDisabled) {
        runCatching { java.io.File(diskCacheDirectory().toString()).deleteRecursively() }
    }
    val builder = ImageLoader.Builder(platformContext)
        .serviceLoaderEnabled(false)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = imageClient))
        }
        .apply(configureMemoryCache)
    if (diskCacheDisabled) {
        builder.diskCachePolicy(CachePolicy.DISABLED)
    } else {
        builder.diskCache {
            DiskCache.Builder()
                .directory(diskCacheDirectory())
                .maxSizeBytes(imageDiskCacheBytes(maxCacheSizeMb()))
                .build()
        }
    }
    return builder
        .crossfade(true)
        .build()
}

/**
 * The one disk-cache sizing fold both JVM shells share over
 * `NetworkOfflineStore.networkOffline.value.maxCacheSizeMb`: a persisted size
 * `> 0` wins (MB → bytes); `0` is the picker's "Unlimited" and sizes the cache
 * unbounded; any other negative — only reachable as a corrupt value, since the
 * [CACHE_SIZE_DISABLED] sentinel is turned into `CachePolicy.DISABLED` by the
 * builder above before sizing runs — keeps the legacy 256 MB default. Pure;
 * pinned by `JellyPlayImageLoaderTest`.
 */
fun imageDiskCacheBytes(maxCacheSizeMb: Int): Long = when {
    maxCacheSizeMb > 0 -> maxCacheSizeMb * 1024L * 1024L
    maxCacheSizeMb == 0 -> Long.MAX_VALUE
    else -> 256L * 1024 * 1024
}
