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
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    override fun onCreate() {
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
