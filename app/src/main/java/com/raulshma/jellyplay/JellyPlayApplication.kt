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
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    @Inject lateinit var userPreferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore
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
    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var autoDownloadScheduler: com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
    @Inject lateinit var userDataSyncScheduler: com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
    @Inject lateinit var widgetWorkScheduler: com.raulshma.jellyplay.widget.WidgetWorkScheduler
    @Inject lateinit var downloadRecoveryInitializer: com.raulshma.jellyplay.startup.DownloadRecoveryInitializer

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Critical path: Sentry must initialise before anything else so all
        // subsequent work is crash-instrumented. Audio + widget updaters follow
        // on the same coroutine (they have no dependency on the groups below).
        applicationScope.launch(Dispatchers.IO) {
            initSentry()
            audioPlaybackManagerProvider.get().start()
            nowPlayingWidgetUpdaterProvider.get().start()
        }
        // Background schedulers — independent enqueue calls, all KEEP-safe.
        // Run concurrently with the critical path so cold start isn't gated on
        // Sentry/audio init.
        applicationScope.launch(Dispatchers.IO) {
            widgetWorkScheduler.enqueuePeriodic()
            userDataSyncScheduler.enqueuePeriodic()
            autoDownloadScheduler.sync()
            notificationScheduler.scheduleOrUpdate()
        }
        // Best-effort download recovery — independent of the above groups.
        applicationScope.launch(Dispatchers.IO) {
            downloadRecoveryInitializer.recover()
        }
    }

    private fun initSentry() {
        SentryAndroid.init(this) { options ->
            // Strip query strings from HTTP breadcrumb URLs that may carry the
            // Jellyfin access token (e.g. ".../stream?api_key=…"). The previous
            // implementation was case-sensitive and only inspected the "url"
            // data key, missing "ApiKey" variants and breadcrumbs whose message
            // contained a logged request line.
            options.setBeforeBreadcrumb { breadcrumb, _ ->
                val data = breadcrumb.data
                val url = data["url"] as? String
                if (url != null && url.contains("?")) {
                    val query = url.substringAfter("?")
                    // Match token-bearing query params case-insensitively.
                    val carriesToken = query.split("&").any { kv ->
                        val key = kv.substringBefore("=").lowercase()
                        key in TOKEN_PARAM_NAMES
                    }
                    if (carriesToken) {
                        data["url"] = url.substringBefore("?")
                    }
                }
                // Drop breadcrumbs whose message contains a literal token
                // pattern (e.g. an OkHttp log line of
                // "GET .../stream?api_key=abc123"). Returning null drops the
                // breadcrumb entirely rather than redacting in place, which is
                // safer because we can't know where in the message the token is.
                val message = breadcrumb.message
                if (message != null) {
                    val lower = message.lowercase()
                    if (TOKEN_PATTERNS.any { lower.contains(it) }) {
                        return@setBeforeBreadcrumb null
                    }
                }
                breadcrumb
            }
            options.dsn?.let { dsn ->
                if (dsn.isNotBlank()) {
                    configureSentryUserContext()
                }
            }
        }
    }

    private fun configureSentryUserContext() {
        val user = io.sentry.protocol.User().apply {
            username = "jellyplay-user"
        }
        Sentry.setUser(user)
        Sentry.setTag("player.engine", userPreferencesStore.preferences.value.preferredPlayer.name)
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
        val cacheMb = userPreferencesStore.preferences.value.maxCacheSizeMb
        val cacheSize = if (cacheMb > 0) cacheMb * 1024L * 1024L else 256L * 1024 * 1024

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
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(cacheSize)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader = imageLoader

    companion object {
        // Query-param names (lowercased) whose presence marks a URL as carrying
        // a credential. Matched case-insensitively against the param key only.
        private val TOKEN_PARAM_NAMES = setOf(
            "api_key", "apikey", "token", "x-emby-token", "accesstoken",
        )
        // Lowercased substrings whose presence in a breadcrumb message marks it
        // as a logged request line that may carry a raw token.
        private val TOKEN_PATTERNS = listOf(
            "api_key=", "apikey=", "x-emby-token:", "x-emby-token=", "accesstoken=",
        )
    }
}
