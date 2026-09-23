package com.raulshma.jellyplay

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.raulshma.jellyplay.core.data.di.CoreDataWorkerFactory
import com.raulshma.jellyplay.core.data.di.androidCoreDataModule
import com.raulshma.jellyplay.core.data.di.androidDataModule
import com.raulshma.jellyplay.core.data.di.dataJvmModule
import com.raulshma.jellyplay.core.data.image.jellyPlayImageLoader
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.di.androidDatastoreModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.database.di.androidDatabaseModule
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.network.di.androidNetworkModule
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.core.notification.di.NotificationWorkerFactory
import com.raulshma.jellyplay.core.notification.di.androidNotificationModule
import com.raulshma.jellyplay.core.ui.di.androidCoreUiModule
import com.raulshma.jellyplay.di.androidAdminSeamsModule
import com.raulshma.jellyplay.di.androidAppInteropAdaptersModule
import com.raulshma.jellyplay.di.androidAppModule
import com.raulshma.jellyplay.di.androidAppViewModelsModule
import com.raulshma.jellyplay.di.androidDownloadSeamsModule
import com.raulshma.jellyplay.di.androidSettingsSeamsModule
import com.raulshma.jellyplay.feature.library.di.androidPhotoExportModule
import com.raulshma.jellyplay.feature.settings.di.androidSettingsPlatformModule
import com.raulshma.jellyplay.feature.admin.di.androidAdminModule
import com.raulshma.jellyplay.feature.subtitle.tester.di.androidSubtitleTesterModule
import com.raulshma.jellyplay.feature.player.live.di.androidPlayerLiveModule
import com.raulshma.jellyplay.feature.player.video.di.androidPlayerVideoModule
import com.raulshma.jellyplay.feature.shell.sharedFeatureModules
import com.raulshma.jellyplay.startup.AppStartupPrewarms


import com.raulshma.jellyplay.core.ui.di.coreUiMessageModule
import com.raulshma.jellyplay.feature.auth.di.androidAuthModule

import com.raulshma.jellyplay.feature.details.androidDetailsModule
import com.raulshma.jellyplay.feature.book.di.androidBookPlayerModule


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
        startKoin {
            modules(
                datastoreCommonModule,
                androidDatastoreModule(this@JellyPlayApplication),
                databaseDaosModule,
                androidDatabaseModule(this@JellyPlayApplication),
                networkJvmModule,
                androidNetworkModule(this@JellyPlayApplication),
                dataJvmModule,
                androidDataModule(this@JellyPlayApplication),
                // Legacy core:data remainder (Hilt-extinct — media3
                // audio stack, cast, schedulers, remote control, workers) +
                // core:notification and core:ui's UserMessageBus.
                androidCoreDataModule(this@JellyPlayApplication),
                androidNotificationModule(this@JellyPlayApplication),
                androidCoreUiModule,
                // V3 downloads conveyor: Android actuals of the portable
                // download engine's seams (WorkManager enqueue/coordinator,
                // Context/StatFs storage layout, notification summary, Coil
                // preload). Koin owns these legacy-side impls so the
                // DownloadRepository single in dataJvmModule resolves.
                androidDownloadSeamsModule(this@JellyPlayApplication),
                // Dev v0.10.7 quick-action download-outcome bridge lives in
                // androidAppInteropAdaptersModule below (DownloadOutcomeMessenger
                // -> core:ui UserMessageBus).
                // Admin flip: Android actual of the admin-statistics
                // label seam — legacy core:data R.string over the Koin-owned
                // AdminStatisticsRepositoryImpl (dataJvmModule), byte-identical
                // to the pre-move context.getString calls.
                androidAdminSeamsModule(this@JellyPlayApplication),
                // App Koin graph: the former Hilt-owned :app classes
                // (shell coordinators, startup initializers, widget schedulers/
                // updaters, DeepLinkHandler, FloatingPlayerState), the three
                // former WidgetModule @Binds pairs, and the shared-feature seam
                // adapters the deleted HiltInteropModule used to bridge
                // (MusicMessageBus / DetailThemeMusic /
                // AudioPlayerCast — direct Koin resolution
                // now, no EntryPoint; AudioPlayerEngine moved into core/data
                // and androidCoreDataModule aliases it onto the manager).
                androidAppModule(this@JellyPlayApplication),
                androidAppInteropAdaptersModule(this@JellyPlayApplication),
                // App-shell ViewModels (Main/PlayOn/WidgetConfig): resolved
                // through the AndroidX ViewModelStore via KoinViewModelFactory,
                // so activity-scoped instance-sharing semantics are unchanged.
                androidAppViewModelsModule,
                // The shared commonMain feature Koin modules both JVM shells
                // register, declared ONCE in shared/feature/shell
                // (sharedFeatureModules);
                // the per-module conveyor history rides that declaration.
                // Registration order is inert in Koin (definitions are keyed);
                // only Android's platform actuals below are order-sensitive,
                // and they stay inline in THIS list.
                *sharedFeatureModules.toTypedArray(),
                // core:ui's UserMessageBus module — core, not feature, so it
                // stays inline here rather than in sharedFeatureModules (the
                // home/settings ViewModels post their feedback through it).
                coreUiMessageModule,
                // V3 settings conveyor (Android platform pick): the Android
                // actuals of the shared settings ViewModels' seams (SAF backup
                // IO, LocaleManager, storage walkers, About/Licenses sources).
                // The four seams (auto-download sync, notification reschedule,
                // TV watch-next, audio cache clear) wrap the legacy schedulers,
                // resolved straight from the core Koin graph.
                androidSettingsPlatformModule(this@JellyPlayApplication),
                androidSettingsSeamsModule(),
                // MediaStore/FileProvider photo-export actual for the library
                // feature's PhotoExport seam (androidDataModule pattern).
                androidPhotoExportModule(this@JellyPlayApplication),
                // V3 admin conveyor (Android half): the Android-only
                // plugin-config WebView ViewModel (Context ctor param);
                // AdminRepository and AdminStatisticsRepository resolve from
                // dataJvmModule (Koin-owned since the admin flip).
                androidAdminModule(this@JellyPlayApplication),

                // V3 subtitle-tester conveyor (final feature): the whole
                // feature is Android-only (androidMain-heavy module — the
                // preview engines, surface host, SAF font picker and raw-asset
                // factory have no desktop halves), so this is the only
                // registration. PlayerEngineFactory and FontProvider are
                // Koin-owned by androidPlayerVideoModule below; the
                // PlaybackRequestFactory single is constructed with the
                // application context here.
                androidSubtitleTesterModule(this@JellyPlayApplication),

                // Player-video conveyor: the migrated video player
                // (:feature:player:video + the absorbed :feature:player:core
                // remains). Koin owns the engine stack, the font/cache/
                // preview singletons and the VideoPlayerViewModel; the six
                // legacy playback deps resolve from the core Koin graph
                // (the legacy :core:data remainder). Sole entry
                // point stays PlayerActivity — no desktop registration
                // (latent feature, subtitle-tester precedent).
                androidPlayerVideoModule(this@JellyPlayApplication),

                //  auth cutover (Android platform half): the
                // LocalNetworkStatus gate is Android-only here — it bridges
                // the legacy :core:ui LocalNetworkAccess object with the
                // application context (androidAdminModule pattern); desktop
                // registers its own non-blaming pick from the shared module's
                // jvmMain.
                androidAuthModule(this@JellyPlayApplication),
                // Details conveyor (Android platform half): the two media3
                // playback seams (per-item audio play, ambient theme music)
                // resolve through the androidAppInteropAdaptersModule adapters
                // above; the storage probe is the StatFs androidMain actual
                // below.
                androidDetailsModule(this@JellyPlayApplication),
                // Book reader conveyor (Android platform half): the reader
                // engine seams are the module's android platform module.
                androidBookPlayerModule(this@JellyPlayApplication),

                // Player-live conveyor (Android platform half): the three
                // platform seams replacing the legacy :feature:player:live
                // module. The engine factory resolves the shared
                // NetworkQualifiers.streamingHttpClient; the audio seam wraps
                // the legacy PlayerAudioLifecycle; the transcode-reasons
                // renderer delegates to the legacy core:ui formatter.
                androidPlayerLiveModule(this@JellyPlayApplication),

            )
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
