package com.raulshma.jellyplay

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.size.Size
import com.raulshma.jellyplay.core.data.di.androidDataModule
import com.raulshma.jellyplay.core.data.di.dataJvmModule
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.androidDatastoreModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.database.di.androidDatabaseModule
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.network.di.androidNetworkModule
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.di.hiltInteropModule
import com.raulshma.jellyplay.di.androidDownloadSeamsModule
import com.raulshma.jellyplay.di.androidSettingsSeamsModule
import com.raulshma.jellyplay.feature.search.di.searchModule
import com.raulshma.jellyplay.feature.library.di.androidPhotoExportModule
import com.raulshma.jellyplay.feature.library.di.libraryModule
import com.raulshma.jellyplay.feature.music.di.musicModule
import com.raulshma.jellyplay.feature.livetv.di.liveTvModule
import com.raulshma.jellyplay.feature.downloads.di.downloadsModule
import com.raulshma.jellyplay.feature.syncplay.di.syncPlayModule
import com.raulshma.jellyplay.feature.settings.di.settingsModule
import com.raulshma.jellyplay.feature.settings.di.androidSettingsPlatformModule
import com.raulshma.jellyplay.feature.admin.di.adminModule
import com.raulshma.jellyplay.feature.admin.di.androidAdminModule


import com.raulshma.jellyplay.feature.editor.di.editorModule

import com.raulshma.jellyplay.feature.calendar.di.calendarModule


import com.raulshma.jellyplay.feature.requests.di.requestsModule

import com.raulshma.jellyplay.feature.newsletter.di.newsletterModule

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import javax.inject.Inject

@HiltAndroidApp
class JellyPlayApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // javax.inject.Provider defers Hilt construction of OkHttpClient
    // (whose provideOkHttpClient does a blocking DataStore read + disk IO)
    // off the cold-start path until Coil's first image request, which only
    // happens once setContent renders an image — well after onCreate returns.
    @Inject lateinit var okHttpClientProvider: javax.inject.Provider<OkHttpClient>
    @Inject lateinit var networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
    // Defers the subtitle-font asset copy + byte-cache pre-warm (multi-MB .ttf
    // reads) to the IO block below, so the player's Main-thread font handoff to
    // libass hits a warm cache instead of reading disk.
    @Inject lateinit var fontProviderProvider: javax.inject.Provider<com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider>
    // Defers the video byte cache's SQLite index open + span directory scan to
    // the IO block below, so the first cacheable playback doesn't pay that
    // disk work on the player thread.
    @Inject lateinit var videoStreamCacheProvider: javax.inject.Provider<com.raulshma.jellyplay.feature.player.video.engine.VideoStreamCache>
    // javax.inject.Provider defers Hilt construction of AudioPlaybackManager
    // (and its transitive 14-dep graph: AudioLibraryBrowser,
    // AudioProgressReporter, AudioCrossfader, QueueUndoStack, LruCache(25), …)
    // off the main thread until the IO launch block below actually calls
    // get(). The start() body already offloads its real work to Dispatchers.IO,
    // so behavior is unchanged; only the construction cost moves off the
    // cold-start critical path.
    @Inject lateinit var audioPlaybackManagerProvider: javax.inject.Provider<com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager>
    // javax.inject.Provider defers Hilt construction of NowPlayingWidgetUpdater,
    // whose constructor pulls in AudioPlaybackManager — the same 14-dep graph
    // the Provider above defers. A direct field inject re-pulls that whole
    // graph before onCreate returns; moving construction into the IO launch
    // block below keeps it off the cold-start critical path. start() only
    // launches observers on its own scope, so behavior is unchanged.
    @Inject lateinit var nowPlayingWidgetUpdaterProvider: javax.inject.Provider<com.raulshma.jellyplay.widget.NowPlayingWidgetUpdater>
    // The remaining schedulers/listeners are likewise deferred via
    // javax.inject.Provider. A direct @Inject lateinit var forces Hilt to
    // construct each one (and its transitive graph — several pull Room, OkHttp,
    // repository singletons) during super.onCreate() before the first frame.
    // Each .start()/.enqueue…()/sync() body already runs on its own scope or
    // dispatches to IO, so construction is the only cost on the critical path.
    @Inject lateinit var notificationSchedulerProvider: javax.inject.Provider<NotificationScheduler>
    @Inject lateinit var autoDownloadSchedulerProvider: javax.inject.Provider<com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler>
    @Inject lateinit var userDataSyncSchedulerProvider: javax.inject.Provider<com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler>
    @Inject lateinit var playbackSyncSchedulerProvider: javax.inject.Provider<com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler>
    @Inject lateinit var playbackSyncReconnectListenerProvider: javax.inject.Provider<com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener>
    @Inject lateinit var downloadReconnectListenerProvider: javax.inject.Provider<com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener>
    @Inject lateinit var notificationReconnectListenerProvider: javax.inject.Provider<com.raulshma.jellyplay.core.notification.scheduler.NotificationReconnectListener>
    @Inject lateinit var widgetWorkSchedulerProvider: javax.inject.Provider<com.raulshma.jellyplay.widget.WidgetWorkScheduler>
    @Inject lateinit var downloadRecoveryInitializerProvider: javax.inject.Provider<com.raulshma.jellyplay.startup.DownloadRecoveryInitializer>
    // javax.inject.Provider defers construction of AppUpdateRepository (which
    // pulls GitHub + OkHttp) off the cold-start path. cleanupDownloadedApk is a
    // cheap file delete, but construction is the cost worth deferring.
    @Inject lateinit var appUpdateRepositoryProvider: javax.inject.Provider<com.raulshma.jellyplay.core.data.update.AppUpdateRepository>

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Earliest hook in app startup — before ContentProviders and onCreate,
        // so debug StrictMode sees the whole init path. The debug source set
        // backs installDebugStrictMode with real policies; the release source
        // set ships a no-op, so release builds compile none of it.
        installDebugStrictMode()
    }

    override fun onCreate() {
        // Koin owns construction for the shared modules (plan §Phase C4); the
        // legacy Hilt shims bridge every binding to these definitions. MUST
        // run before super.onCreate() — Hilt field-injects bridged types
        // (e.g. networkOfflineStore) during super.onCreate(), and definitions
        // are lazy, so this adds no cold-start construction cost.
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
                // V3 downloads conveyor: Android actuals of the portable
                // download engine's seams (WorkManager enqueue/coordinator,
                // Context/StatFs storage layout, notification summary, Coil
                // preload). Koin owns these legacy-side impls so the
                // DownloadRepository single in dataJvmModule resolves; the
                // legacy DataModule bridges its remaining Hilt injectors to
                // them via koin().get().
                androidDownloadSeamsModule(this@JellyPlayApplication),
                // V3 feature conveyor: shared feature ViewModels. The interop
                // bridge exposes the still-Hilt-owned data-layer types to the
                // Koin graph until the Phase X MediaRepository flip.
                hiltInteropModule(this@JellyPlayApplication),
                searchModule,
                libraryModule,
                musicModule,
                liveTvModule,
                downloadsModule,
                syncPlayModule,
                // V3 settings conveyor: the shared settings ViewModels plus the
                // Android platform pick of their seams (SAF backup IO,
                // LocaleManager, storage walkers, About/Licenses sources). The
                // four Hilt-backed seams (auto-download sync, notification
                // reschedule, TV watch-next, audio cache clear) wrap the legacy
                // schedulers through the SettingsSeamsEntryPoint below.
                settingsModule,
                androidSettingsPlatformModule(this@JellyPlayApplication),
                androidSettingsSeamsModule(this@JellyPlayApplication),
                // MediaStore/FileProvider photo-export actual for the library
                // feature's PhotoExport seam (androidDataModule pattern).
                androidPhotoExportModule(this@JellyPlayApplication),
                // V3 admin conveyor (eighth feature): the shared admin
                // ViewModels plus the Android-only plugin-config WebView
                // ViewModel (Context ctor param). AdminRepository and
                // AdminStatisticsRepository resolve through the
                // hiltInteropModule singles above.
                adminModule,
                androidAdminModule(this@JellyPlayApplication),


                // V3 editor conveyor (ninth feature): the shared metadata
                // editor ViewModel. MetadataEditorRepository / AuthRepository /
                // SubtitleProviderRepository are Koin-native; the
                // StreamingSubtitleStore dep resolves through the
                // hiltInteropModule single above (impl stays Hilt-bound).
                editorModule,

                // V3 calendar conveyor: all three ctor deps (ArrRepository,
                // SeerrRepository, ExperimentalStore) are already Koin-native
                // in the shared graph — the first conveyor module with zero
                // Hilt-interop edges.
                calendarModule,


                // V3 requests conveyor (eleventh feature): all three ctor deps
                // (SeerrRepository, ArrRepository, ExperimentalStore) were
                // already Koin-owned, so — like calendar, and unlike the nine
                // features above — this registration involves no Hilt interop
                // at all.
                requestsModule,

                // V3 newsletter conveyor: imageUrlProvider/notificationStore/
                // authRepository were already Koin-native; mediaRepository
                // resolves through the hiltInteropModule single above — no
                // new interop definitions were needed for this feature.
                newsletterModule,

            )
        }
        super.onCreate()
        // Critical path: audio + widget updaters (no dependency on the groups
        // below). Also pre-warms (a) the network-offline DataStore slice — the
        // real persisted read happens in the store's eager stateIn upstream,
        // this keeps the flow warm for the imageLoader's lazily-sized DiskCache —
        // and (b) the subtitle font byte cache so the first ASS playback doesn't
        // read fonts on Main.
        applicationScope.launch(Dispatchers.IO) {
            runCatching { networkOfflineStore.networkOffline.first() }
            runCatching { fontProviderProvider.get().prewarm() }
            runCatching { videoStreamCacheProvider.get().prewarm() }
            audioPlaybackManagerProvider.get().start()
            nowPlayingWidgetUpdaterProvider.get().start()
        }
        // Background schedulers — independent enqueue calls, all KEEP-safe.
        // Run concurrently with the critical path so cold start isn't gated on
        // audio init. Provider.get() constructs each scheduler here (off the
        // main thread) instead of during super.onCreate().
        applicationScope.launch(Dispatchers.IO) {
            widgetWorkSchedulerProvider.get().enqueuePeriodic()
            userDataSyncSchedulerProvider.get().enqueuePeriodic()
            playbackSyncSchedulerProvider.get().enqueuePeriodic()
            playbackSyncReconnectListenerProvider.get().start()
            downloadReconnectListenerProvider.get().start()
            notificationReconnectListenerProvider.get().start()
            autoDownloadSchedulerProvider.get().sync()
            notificationSchedulerProvider.get().scheduleOrUpdate()
        }
        // Best-effort download recovery — independent of the above groups.
        applicationScope.launch(Dispatchers.IO) {
            downloadRecoveryInitializerProvider.get().recover()
        }
        // Sweep any APK left by a prior self-update. A successful install
        // restarts the process (so this runs in the new version) and leaves the
        // old APK orphaned; a cancelled/failed install also leaves it behind.
        // Safe at startup: onCreate precedes any new download.
        applicationScope.launch(Dispatchers.IO) {
            runCatching { appUpdateRepositoryProvider.get().cleanupDownloadedApk() }
        }
    }

    private val imageClient by lazy {
        okHttpClientProvider.get().newBuilder()
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

        ImageLoader.Builder(this)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this@JellyPlayApplication, memoryCachePercent)
                    .build()
            }
            .diskCache {
                // Sized inside the builder lambda: Coil builds the DiskCache
                // lazily on its FIRST access (the first networked image write),
                // not at ImageLoader construction — by then the `networkOffline`
                // pre-warm in onCreate has long since published the persisted
                // slice, so `.value` reads the user-configured size instead of
                // the default. A persisted size > 0 wins; anything else falls
                // back to 256 MB.
                val cacheMb = networkOfflineStore.networkOffline.value.maxCacheSizeMb
                val cacheSize = if (cacheMb > 0) cacheMb * 1024L * 1024L else 256L * 1024 * 1024
                DiskCache.Builder()
                    .directory(cacheDir.resolve(ImageCache.DIR).absolutePath.toPath())
                    .maxSizeBytes(cacheSize)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader = imageLoader
}
