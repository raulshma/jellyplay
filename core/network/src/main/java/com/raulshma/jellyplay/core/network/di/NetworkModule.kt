package com.raulshma.jellyplay.core.network.di

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.runBlocking
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.JellyfinApiClientImpl
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.AdminApiClientImpl
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.AuthApiClientImpl
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClientImpl
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.LiveTvApiClientImpl
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClientImpl
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClientImpl
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClientImpl
import com.raulshma.jellyplay.core.network.api.PluginApiClient
import com.raulshma.jellyplay.core.network.api.PluginApiClientImpl
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClientImpl
import com.raulshma.jellyplay.core.network.api.UserApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClientImpl
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.RadarrApiClientImpl
import com.raulshma.jellyplay.core.network.arr.ResilientRadarrApiClient
import com.raulshma.jellyplay.core.network.arr.ResilientSonarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClientImpl
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApiImpl
import com.raulshma.jellyplay.core.network.seerr.ResilientSeerrApiClient
import com.raulshma.jellyplay.core.network.api.ResilientTmdbApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClientImpl
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import com.raulshma.jellyplay.core.network.failover.ServerFailoverInterceptor
import com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Named
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindJellyfinApiClient(
        impl: JellyfinApiClientImpl,
    ): JellyfinApiClient

    @Binds
    @Singleton
    abstract fun bindAuthApiClient(impl: AuthApiClientImpl): AuthApiClient

    @Binds
    @Singleton
    abstract fun bindLibraryApiClient(impl: LibraryApiClientImpl): LibraryApiClient

    @Binds
    @Singleton
    abstract fun bindPlaybackApiClient(impl: PlaybackApiClientImpl): PlaybackApiClient

    @Binds
    @Singleton
    abstract fun bindSyncPlayApiClient(impl: SyncPlayApiClientImpl): SyncPlayApiClient

    @Binds
    @Singleton
    abstract fun bindLiveTvApiClient(impl: LiveTvApiClientImpl): LiveTvApiClient

    @Binds
    @Singleton
    abstract fun bindAdminApiClient(impl: AdminApiClientImpl): AdminApiClient

    @Binds
    @Singleton
    abstract fun bindUserApiClient(impl: UserApiClientImpl): UserApiClient

    @Binds
    @Singleton
    abstract fun bindMetadataApiClient(impl: MetadataApiClientImpl): MetadataApiClient

    @Binds
    @Singleton
    abstract fun bindMediaInfoApiClient(impl: MediaInfoApiClientImpl): MediaInfoApiClient

    @Binds
    @Singleton
    abstract fun bindPluginApiClient(impl: PluginApiClientImpl): PluginApiClient

    @Binds
    @Singleton
    abstract fun bindSeerrApiClient(
        impl: ResilientSeerrApiClient,
    ): SeerrApiClient

    @Binds
    @Singleton
    abstract fun bindTmdbApiClient(
        impl: ResilientTmdbApiClient,
    ): TmdbApiClient

    @Binds
    @Singleton
    abstract fun bindRadarrApiClient(
        impl: ResilientRadarrApiClient,
    ): RadarrApiClient

    @Binds
    @Singleton
    abstract fun bindSonarrApiClient(
        impl: ResilientSonarrApiClient,
    ): SonarrApiClient

    companion object {
        // Hoisted so the pattern compiles once at class load rather than on each
        // OkHttp client construction (low impact since the provider is @Singleton,
        // but removes an unnecessary per-cold-start Regex allocation).
        val TOKEN_PARAM_PATTERN = Regex(
            "(?i)(\\?|&)(api_key|apikey|token|x-emby-token|accesstoken)=[^&\\s]+",
        )

        // Derived-client timeout constants. Hoisted as named constants so a
        // tune of the base preset (or these derived values) only has to touch
        // one place — the streaming/download clients previously re-declared
        // 30/60/30 second literals independently of the base preset.
        private const val STREAMING_MIN_READ_TIMEOUT_SEC = 30L
        private const val DOWNLOAD_CONNECT_TIMEOUT_SEC = 30L
        private const val DOWNLOAD_READ_TIMEOUT_SEC = 60L
        private const val DOWNLOAD_WRITE_TIMEOUT_SEC = 30L

        /**
         * Binds the GitHub Releases client as a [GitHubReleasesApi]. Built here
         * rather than via `@Binds` + `@Inject constructor` because the impl
         * takes the latest-release URL as a constructor param (overridable by
         * unit tests via MockWebServer). Dagger does not honor Kotlin default
         * argument values, so leaving it to `@Binds` would require a bare
         * `String` binding in the graph — instead we supply the production
         * constant directly here.
         */
        @Provides
        @Singleton
        fun provideGitHubReleasesApi(
            okHttpClient: OkHttpClient,
        ): GitHubReleasesApi =
            GitHubReleasesApiImpl(
                okHttpClient,
                GitHubReleasesApiImpl.LATEST_RELEASE_URL,
            )

        @Provides
        @Singleton
        fun provideConnectivityManager(
            @ApplicationContext context: Context,
        ): ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        /**
         * Shared lenient `Json` for ad-hoc (de)serialization across the data and
         * network layers (repository JSON columns, REST DTO decoding). Centralized
         * here so every site shares one configured instance instead of re-declaring
         * `Json { ignoreUnknownKeys = true }`. `Json` is thread-safe for
         * parse/encode; a single process-wide instance is fine.
         *
         * Note: `JellyfinApiEngine.sharedJson` is the same configuration kept as a
         * companion `val` for code paths that cannot use DI (e.g. object
         * companions). Prefer this injected instance where a Hilt graph is available.
         */
        @Provides
        @Singleton
        fun provideJson(): kotlinx.serialization.json.Json =
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        @Provides
        @Singleton
        fun provideJellyfin(
            @ApplicationContext context: Context,
            okHttpClient: OkHttpClient,
            serverIdentityStore: com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore,
        ): Jellyfin {
            // Use the app's persistent DataStore UUID as the SDK device id so the
            // REST/session API, the WebSocket connection (which passes the same
            // id in MainViewModel), and the server all agree on one identity.
            // Without this the SDK defaults to Settings.Secure.ANDROID_ID, which
            // never equals ensureDeviceId() and made the app's own session show up
            // in the Play On / Cast device list.
            //
            // ServerIdentityStore.identity is a StateFlow shared with
            // SharingStarted.Eagerly, so after the very first process launch its
            // current value is the persisted UUID held in memory. Reading .value
            // is non-blocking and avoids a DataStore disk read on the DI critical
            // path (every screen transitively pulls this @Provides on first
            // frame). Only on the rare first-launch case where the Eagerly flow
            // hasn't populated yet do we fall back to the blocking
            // ensureDeviceId() — which generates + persists the id. The resolved
            // id is identical either way; the fast path simply skips the disk IO.
            val androidDefault = androidDevice(context)
            val deviceId = serverIdentityStore.identity.value.deviceId
                ?: runBlocking { serverIdentityStore.ensureDeviceId() }
            return createJellyfin {
                this.context = context
                clientInfo = ClientInfo(
                    name = "JellyPlay",
                    version = context.packageManager
                        .getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                )
                deviceInfo = DeviceInfo(
                    id = deviceId,
                    name = androidDefault.name,
                )
                apiClientFactory = OkHttpFactory(okHttpClient)
            }
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(
            @ApplicationContext context: Context,
            okHttpConfigProvider: OkHttpConfigProvider,
            bandwidthInterceptor: BandwidthInterceptor,
            serverAddressRouter: ServerAddressRouter,
        ): OkHttpClient {
            // Read config synchronously via StateFlow.value — no runBlocking.
            // On a cold start the Eagerly-shared StateFlow may still hold the
            // initialValue sentinel (maxCacheSizeMb = 0), in which case the cache
            // falls back to 50 MB below. The real preference lands in .value
            // within tens of ms (DataStore disk read) and is picked up on the
            // next process start; timeouts are re-applied per request by the
            // interceptor below, so only the (rarely-changed) cache size is
            // affected, and only on the very first launch. The previous
            // runBlocking { .first() } blocked the DI critical path — every
            // screen that transitively pulls OkHttpClient (repositories, SDK
            // client, download workers) waited on a DataStore disk read before
            // first frame.
            val initialConfig = okHttpConfigProvider.config.value
            val cacheDir = File(context.cacheDir, "http_cache")
            cacheDir.mkdirs()
            val cacheMb = initialConfig.maxCacheSizeMb
            val cacheSize = if (cacheMb > 0) cacheMb * 1024L * 1024L else 50L * 1024 * 1024
            val initialTimeout = initialConfig.networkTimeoutPreset
            
            // Custom logger that strips Jellyfin access tokens from log lines.
            // OkHttp's HttpLoggingInterceptor has redactHeader(...) but no
            // equivalent for query params in 5.x, and the SDK embeds the access
            // token as ?api_key=... on stream/image/subtitle/WebSocket URLs.
            // The logger replaces the query string of any URL line containing a
            // token-bearing param with "[redacted]" so verbose network logging
            // can never leak credentials to logcat.
            val tokenParamPattern = TOKEN_PARAM_PATTERN
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                val safe = if (tokenParamPattern.containsMatchIn(message)) {
                    tokenParamPattern.replace(message) { mr ->
                        val sep = mr.value.first()
                        val key = mr.value.drop(1).substringBefore("=")
                        "$sep$key=[redacted]"
                    }
                } else {
                    message
                }
                HttpLoggingInterceptor.Logger.DEFAULT.log(safe)
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
                // Never expose credentials in logcat, even when verbose network
                // logging is enabled.
                redactHeader("X-Emby-Token")
                redactHeader("X-Api-Key")
                redactHeader("Authorization")
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
            }
            
            val builder = OkHttpClient.Builder()
                .connectTimeout(initialTimeout.connectSec, TimeUnit.SECONDS)
                .readTimeout(initialTimeout.readSec, TimeUnit.SECONDS)
                .writeTimeout(initialTimeout.writeSec, TimeUnit.SECONDS)
                .cache(Cache(cacheDir, cacheSize))
                .connectionPool(ConnectionPool(16, 15, TimeUnit.MINUTES))
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .retryOnConnectionFailure(true)
                // Outermost interceptor: every derived client (SDK, Coil,
                // streaming, downloads, WebSocket) inherits it, so requests
                // targeting an unreachable primary address are transparently
                // rerouted to the active alternate. Must sit outside the
                // timeout interceptor so logging sees the final URL.
                .addInterceptor(ServerFailoverInterceptor(serverAddressRouter))
                .addInterceptor { chain ->
                    val currentConfig = okHttpConfigProvider.config.value
                    val timeoutPreset = currentConfig.networkTimeoutPreset
                    
                    val newChain = chain
                        .withConnectTimeout(timeoutPreset.connectSec.toInt(), TimeUnit.SECONDS)
                        .withReadTimeout(timeoutPreset.readSec.toInt(), TimeUnit.SECONDS)
                        .withWriteTimeout(timeoutPreset.writeSec.toInt(), TimeUnit.SECONDS)
                    
                    if (currentConfig.verboseNetworkLogging) {
                        loggingInterceptor.intercept(newChain)
                    } else {
                        newChain.proceed(newChain.request())
                    }
                }
            return builder
                .addNetworkInterceptor(bandwidthInterceptor)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val path = chain.request().url.encodedPath
                    val query = chain.request().url.queryParameterNames
                    val cacheMaxAge = when {
                        path.startsWith("/Items/") && path.contains("/Images/") -> 604800
                        path.startsWith("/Genres/") -> 300
                        path == "/System/Info/Public" -> 600
                        path.startsWith("/Library/MediaFolders") -> 300
                        path == "/System/Info" -> 120
                        path.startsWith("/Sessions") -> 10
                        path.startsWith("/ScheduledTasks") -> 30
                        path == "/Shows/NextUp" || query.contains("resume") && path == "/Items" -> 120
                        path.contains("/Similar") -> 300
                        // /Items?Ids=… (detail-by-id) carries per-item UserData; the
                        // similar /Shows/.../Episodes and /Shows/.../Seasons reads (now
                        // uncached — they fall through to `else`) carry it too. Don't
                        // blind-cache them: mark-watched / favorite writes hit different
                        // URIs (PlayedItems / FavoriteItems), so OkHttp won't invalidate
                        // these entries (RFC 7234 §4.4), and the repo's detailCache /
                        // EpisodeCatalogue are the cache layers that DO get invalidated on
                        // every write. Library browse (/Items?ParentId=…) keeps the cache.
                        path == "/Items" &&
                            !query.contains("Resume") &&
                            !query.any { it.equals("Ids", ignoreCase = true) } -> 60
                        else -> null
                    }
                    if (response.isSuccessful && cacheMaxAge != null) {
                        response.newBuilder()
                            .header("Cache-Control", "max-age=$cacheMaxAge")
                            .build()
                    } else {
                        response
                    }
                }
                .build()
        }

        @Provides
        @Singleton
        @Named("streaming")
        fun provideStreamingOkHttpClient(
            okHttpClient: OkHttpClient,
        ): OkHttpClient {
            val baseReadSec = okHttpClient.readTimeoutMillis.toLong() / 1000L
            val streamingReadSec = maxOf(baseReadSec, STREAMING_MIN_READ_TIMEOUT_SEC)
            return okHttpClient.newBuilder()
                .readTimeout(streamingReadSec, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Derived client for download paths. Mirrors the per-run `newBuilder()`
         * previously invoked inside `DownloadWorker.doWork()` — same connect /
         * read / write timeouts, but hoisted to a single shared singleton so
         * concurrent `DownloadWorker` invocations (the limiter allows up to
         * `maxConcurrentDownloads`) no longer each pay a `build()` cost and
         * clone the interceptor list. The base `OkHttpClient` shares its
         * `ConnectionPool` and `Dispatcher` via `newBuilder()`, so behavior is
         * identical to the prior per-worker client.
         */
        @Provides
        @Singleton
        @Named("download")
        fun provideDownloadOkHttpClient(
            okHttpClient: OkHttpClient,
        ): OkHttpClient = okHttpClient.newBuilder()
            .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(DOWNLOAD_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(DOWNLOAD_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }
}
