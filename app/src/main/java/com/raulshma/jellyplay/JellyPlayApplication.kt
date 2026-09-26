package com.raulshma.jellyplay

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.raulshma.jellyplay.core.data.di.CoreDataWorkerFactory
import com.raulshma.jellyplay.core.data.image.jellyPlayImageLoader
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.notification.di.NotificationWorkerFactory
import com.raulshma.jellyplay.di.androidKoinModules
import com.raulshma.jellyplay.startup.AppStartupPrewarms
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.raulshma.jellyplay.widget.AppWidgetWorkerFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

class JellyPlayApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                DelegatingWorkerFactory().apply {
                    // App widget recommendation workers (plain CoroutineWorker
                    // ctors — see AppWidgetWorkerFactory).
                    addFactory(AppWidgetWorkerFactory())
                    // Core-data legacy workers (plain CoroutineWorker ctors —
                    // see CoreDataWorkerFactory/NotificationWorkerFactory).
                    addFactory(CoreDataWorkerFactory())
                    addFactory(NotificationWorkerFactory())
                },
            )
            .build()

    // Deferred single access (Hilt removal): the former
    // javax.inject.Provider fields deferred Hilt construction off the
    // cold-start path; kotlin `by lazy` over the Koin container preserves that
    // exactly (definitions are lazy, and each resolved single is the same
    // memoized instance the rest of the graph sees).
    private val okHttpClient: OkHttpClient by lazyFromKoin()
    // Read by the image loader's lazily-sized DiskCache below
    // (maxCacheSizeMb); the cold-start prewarm of the same slice lives in
    // AppStartupPrewarms, which owns the t=0 ordering.
    private val networkOfflineStore: NetworkOfflineStore by lazyFromKoin()
    // The cold-start prewarm choreography — critical DataStore/cache prewarms,
    // the 2 s-deferred audio/widget and background-scheduler groups, download
    // recovery + the self-update APK sweep — is composed in
    // startup/AppStartupPrewarms (the CacheMaintenanceInitializer/
    // DownloadRecoveryInitializer idiom), over the same memoizing lazy
    // collaborator access the former inline fields provided.
    private val appStartupPrewarms: AppStartupPrewarms by lazyFromKoin()

    private val applicationScope: CoroutineScope
        by lazy { KoinPlatform.getKoin()!!.get(DatastoreQualifiers.applicationScope) }

    /**
     * Memoizing deferred access into the Koin container — the kotlin-lazy
     * twin of the former javax.inject.Provider fields (a @Singleton-backed
     * Hilt Provider returned the same instance per get(), which a memoizing
     * lazy matches).
     */
    private inline fun <reified T : Any> lazyFromKoin() =
        lazy { KoinPlatform.getKoin()!!.get<T>() }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Earliest hook in app startup — before ContentProviders and onCreate,
        // so debug StrictMode sees the whole init path. The debug source set
        // backs installDebugStrictMode with real policies; the release source
        // set ships a no-op, so release builds compile none of it.
        installDebugStrictMode()
        // LeakCanary self-installs via its own startup ContentProvider — this
        // call is the debug-only seam for future config tweaks (twin-file
        // idiom, no-op in release).
        installLeakCanary()
    }

    override fun onCreate() {
        // Koin owns construction for every module —
        // Hilt is fully gone from :app. MUST run before anything resolves a
        // dependency: the lazy fields and every Activity/Service/widget entry
        // point reach into this container, and definitions are lazy, so this
        // adds no cold-start construction cost.
        // The module list itself is AndroidKoinModules.kt (the fold): the
        // shared core graph, this shell's platform actuals, and the ONE
        // spread of sharedFeatureModules both JVM shells consume — every
        // per-registration comment moved there with its registration.
        startKoin {
            modules(androidKoinModules(this@JellyPlayApplication))
        }
        super.onCreate()
        // The cold-start choreography — (a) the critical-path prewarms
        // (network-offline DataStore slice before the Coil DiskCache sizing
        // reads it, identity slice, security slice, font/stream cache
        // prewarms), (b) the 2 s-deferred audio + widget updaters, (c) the
        // 2 s-deferred background schedulers (all KEEP-idempotent enqueues),
        // (d) best-effort download recovery + the self-update APK sweep —
        // is owned by [AppStartupPrewarms] beside the other startup
        // initializers. start() fires the same applicationScope launches, in
        // the same order, at the same point of onCreate as the former inline
        // blocks; the per-group rationale lives there now.
        appStartupPrewarms.start()
    }

    private val imageClient by lazy {
        okHttpClient.newBuilder()
            // Drop the inherited OkHttp http_cache so image bytes aren't written
            // to disk twice. The base client's cache (`cacheDir/http_cache`,
            // sized for API JSON) is shared via newBuilder(); without this Coil
            // would also write every fetched image to its own `image_cache`
            // DiskCache (256 MB default), doubling disk writes for every poster
            // and bloating http_cache with binary data competing with the small
            // JSON responses it was sized for. `.cache(null)` leaves Coil's
            // DiskCache as the sole owner of image bytes — functionally
            // identical to the previous behavior for image rendering.
            .cache(null)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val imageLoader by lazy {
        // Tier the memory-cache budget on device RAM class, mirroring the
        // EngineDeviceProfile.isLowRamDevice gate already used for trickplay.
        // On a 1 GB TV stick the default 20% would over-reserve a small heap
        // competing with MPV/ExoPlayer native buffers.
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRamDevice = am?.let { it.isLowRamDevice || it.memoryClass <= 256 } ?: false
        val memoryCachePercent = if (isLowRamDevice) 0.12 else 0.20

        // Shared JVM builder policy (OkHttp fetcher with ServiceLoader
        // structurally off, crossfade, and the lazily-sized ImageCache.DIR
        // disk cache over NetworkOfflineStore.maxCacheSizeMb — the sizing
        // subtlety documented in JellyPlayImageLoader's KDoc) lives in
        // core:data's jvmShared; this shell supplies only its divergences:
        // the RAM-tiered memory cache above and the image client below.
        jellyPlayImageLoader(
            platformContext = this,
            imageClient = { imageClient },
            diskCacheDirectory = {
                cacheDir.resolve(ImageCache.DIR).absolutePath.toPath()
            },
            maxCacheSizeMb = { networkOfflineStore.networkOffline.value.maxCacheSizeMb },
            configureMemoryCache = {
                memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(this@JellyPlayApplication, memoryCachePercent)
                        .build()
                }
            },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader = imageLoader
}
