package com.raulshma.jellyplay.desktop

import coil3.SingletonImageLoader
import com.raulshma.jellyplay.core.data.image.jellyPlayImageLoader
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import okhttp3.OkHttpClient
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.KoinApplication

/**
 * The desktop image engine: the shared JVM Coil
 * builder policy ([jellyPlayImageLoader] in core:data's jvmShared) over this
 * shell's two divergences — the Koin-owned base STREAMING client and the
 * `<configDir>` cache root.
 *
 * The streaming client derives from the base client via newBuilder(), so it
 * shares the base sslSocketFactory/hostnameVerifier and the SAME dynamic
 * self-signed trust layer (grants read at handshake time). Letting
 * coil-network-okhttp self-register via ServiceLoader would use its OWN
 * default OkHttpClient and fail the TLS handshake against a self-signed
 * server the user had granted — every other surface would connect while
 * artwork stayed broken — so the shared factory's
 * serviceLoaderEnabled(false) makes the exclusivity STRUCTURAL (the
 * ServiceLoader factory can never install its default-client fetcher
 * behind our back); the lambda defers the Koin resolution to the first image
 * load (well after startKoin).
 *
 * Explicit disk cache (same pref slice + ImageCache.DIR name as Android,
 * sized through the shared [com.raulshma.jellyplay.core.data.image.imageDiskCacheBytes]
 * fold): rooted at the app's cache root here
 * <configDir>, next to the OkHttp http-cache (DesktopStorageAreas walks ONLY
 * the http-cache subtree, so the image cache is never double-counted; its
 * storage bucket measures through DesktopCoilImageCache, which resolves THIS
 * loader's diskcache — by construction the same directory it clears).
 * Without this cache, coil3's default applies — the process-wide singleton
 * DiskCache under the SYSTEM TEMP directory, sized 2% of that volume clamped
 * to 10–250 MB — and the user's "max cache size" setting does nothing on
 * this platform.
 */
internal fun installDesktopImageLoader(koinApp: KoinApplication, configDir: Path) {
    SingletonImageLoader.setSafe {
        jellyPlayImageLoader(
            platformContext = it,
            imageClient = {
                koinApp.koin.get<OkHttpClient>(NetworkQualifiers.streamingHttpClient)
            },
            diskCacheDirectory = {
                configDir.toFile().resolve(ImageCache.DIR).absolutePath.toPath()
            },
            maxCacheSizeMb = {
                koinApp.koin.get<NetworkOfflineStore>().networkOffline.value.maxCacheSizeMb
            },
        )
    }
}
