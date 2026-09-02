package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.JellyfinApiClientImpl
import com.raulshma.jellyplay.core.network.LrcLibApi
import com.raulshma.jellyplay.core.network.LyricsApi
import com.raulshma.jellyplay.core.network.ServerDiscoveryService
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.AdminApiClientImpl
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.AuthApiClientImpl
import com.raulshma.jellyplay.core.network.api.DeviceProfileProvider
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
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
import com.raulshma.jellyplay.core.network.api.ResilientTmdbApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClientImpl
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClientImpl
import com.raulshma.jellyplay.core.network.api.UserApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClientImpl
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.RadarrApiClientImpl
import com.raulshma.jellyplay.core.network.arr.ResilientRadarrApiClient
import com.raulshma.jellyplay.core.network.arr.ResilientSonarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClientImpl
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import com.raulshma.jellyplay.core.network.config.applySelfSignedTrust
import com.raulshma.jellyplay.core.network.config.selfSignedTrustHostsReader
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import com.raulshma.jellyplay.core.network.failover.ServerFailoverInterceptor
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApi
import com.raulshma.jellyplay.core.network.github.GitHubReleasesApiImpl
import com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor
import com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.network.seerr.ResilientSeerrApiClient
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import com.raulshma.jellyplay.core.network.subtitle.OpenSubtitlesSubtitleProvider
import com.raulshma.jellyplay.core.network.subtitle.ResilientSubtitleProvider
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import com.raulshma.jellyplay.core.network.subtitle.WyzieSubtitleProvider
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the platform-independent network stack
 * (Phase C4). The base `OkHttpClient`, the Jellyfin SDK instance, and the
 * `DeviceCodecCapabilities` / `DiscoveryMulticastGuard` platform picks live in
 * [androidNetworkModule] / [desktopNetworkModule]; everything here resolves
 * them via cross-module `get()`.
 *
 * The impl classes keep their `@Inject` constructors on the classpath (the
 * legacy Hilt shim reads them from binaries while its bridges route to these
 * Koin definitions), but Koin is now the single construction owner.
 */
val networkJvmModule: Module = module {
    single { Json { ignoreUnknownKeys = true } }
    // Resolves OkHttpConfigProvider cross-module (dataJvmModule provides the
    // impl): the router's probe client installs the SAME self-signed trust
    // layer as the app client so granted servers probe as reachable.
    single { ServerAddressRouter(get()) }
    single { BandwidthInterceptor() }
    single { DeviceProfileProvider(get()) }
    single {
        JellyfinApiEngine(
            jellyfinLazy = daggerLazy { get<Jellyfin>() },
            okHttpClientLazy = daggerLazy { get<OkHttpClient>() },
            deviceProfileProvider = get(),
            addressRouter = get(),
        )
    }
    single {
        JellyfinApiClientImpl(
            authClient = get(),
            libraryClient = get(),
            playbackClient = get(),
            syncPlayClient = get(),
            liveTvClient = get(),
            adminClient = get(),
            metadataClient = get(),
            mediaInfoClient = get(),
            pluginClient = get(),
            userClient = get(),
        )
    }
    single<JellyfinApiClient> { get<JellyfinApiClientImpl>() }

    single { AuthApiClientImpl(get(), get()) }
    single<AuthApiClient> { get<AuthApiClientImpl>() }
    single { LibraryApiClientImpl(get(), get()) }
    single<LibraryApiClient> { get<LibraryApiClientImpl>() }
    single { PlaybackApiClientImpl(get(), get(), get()) }
    single<PlaybackApiClient> { get<PlaybackApiClientImpl>() }
    single { SyncPlayApiClientImpl(get()) }
    single<SyncPlayApiClient> { get<SyncPlayApiClientImpl>() }
    single { LiveTvApiClientImpl(get()) }
    single<LiveTvApiClient> { get<LiveTvApiClientImpl>() }
    single { AdminApiClientImpl(get()) }
    single<AdminApiClient> { get<AdminApiClientImpl>() }
    single { UserApiClientImpl(get()) }
    single<UserApiClient> { get<UserApiClientImpl>() }
    single { MetadataApiClientImpl(get()) }
    single<MetadataApiClient> { get<MetadataApiClientImpl>() }
    single { MediaInfoApiClientImpl(get()) }
    single<MediaInfoApiClient> { get<MediaInfoApiClientImpl>() }
    single { PluginApiClientImpl(get()) }
    single<PluginApiClient> { get<PluginApiClientImpl>() }

    single { SeerrApiClientImpl(get()) }
    single { ResilientSeerrApiClient(get()) }
    single<SeerrApiClient> { get<ResilientSeerrApiClient>() }
    single { TmdbApiClientImpl(get()) }
    single { ResilientTmdbApiClient(get()) }
    single<TmdbApiClient> { get<ResilientTmdbApiClient>() }
    single { RadarrApiClientImpl(get()) }
    single { ResilientRadarrApiClient(get()) }
    single<RadarrApiClient> { get<ResilientRadarrApiClient>() }
    single { SonarrApiClientImpl(get()) }
    single { ResilientSonarrApiClient(get()) }
    single<SonarrApiClient> { get<ResilientSonarrApiClient>() }

    single {
        GitHubReleasesApiImpl(
            okHttpClient = get(),
            latestReleaseUrl = GitHubReleasesApiImpl.LATEST_RELEASE_URL,
        )
    }
    single<GitHubReleasesApi> { get<GitHubReleasesApiImpl>() }

    single { LrcLibApi(get()) }
    single { LyricsApi(get()) }
    single { JellyfinWebSocketClient(get()) }
    single { ServerDiscoveryService(get(), get()) }

    // ── Subtitle provider fan-out (C4 part 2: the @IntoMap Hilt
    // multibinding flipped to Koin). Each raw impl is wrapped in a
    // ResilientSubtitleProvider so RetryPolicy applies to every call —
    // byte-for-byte the map the legacy SubtitleProviderModule built. Adding
    // a new provider = a new enum value, one impl, and one entry here.
    single { WyzieSubtitleProvider(get()) }
    single { OpenSubtitlesSubtitleProvider(get(), get()) }
    single<Map<SubtitleProviderKind, SubtitleProvider>> {
        mapOf(
            SubtitleProviderKind.WYZIE to
                ResilientSubtitleProvider(get<WyzieSubtitleProvider>()),
            SubtitleProviderKind.OPENSUBTITLES to
                ResilientSubtitleProvider(get<OpenSubtitlesSubtitleProvider>()),
        )
    }

    single { ActivityLogRealtimeChannel(get(), get(), get()) }
    single {
        ScheduledTasksRealtimeChannel(
            webSocketClient = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single {
        UserDataRealtimeChannel(
            webSocketClient = get(),
            engine = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
}

/**
 * Adapts a Koin resolution to [dagger.Lazy] for [JellyfinApiEngine]'s ctor
 * params, preserving the memoizing single-evaluation semantics Hilt's
 * Provider-based `Lazy` had: `lazy(...)` defaults to SYNCHRONIZED
 * (double-checked locking), so the value is computed at most once no matter
 * how many threads race the first `.get()`.
 */
internal fun <T> daggerLazy(provider: () -> T): dagger.Lazy<T> {
    val memoized = lazy(provider)
    return object : dagger.Lazy<T> {
        override fun get(): T = memoized.value
    }
}

// Hoisted so the pattern compiles once at class load rather than on each
// OkHttp client construction (low impact since the provider is a singleton,
// but removes an unnecessary per-cold-start Regex allocation).
private val TOKEN_PARAM_PATTERN = Regex(
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
 * Shared construction of the base `OkHttpClient`, replicated byte-for-byte
 * from the legacy Hilt `NetworkModule.provideOkHttpClient` (C4 zero-behavior-
 * change contract): timeouts, interceptor order (failover outermost, timeout
 * re-application + token-redacting logging, bandwidth, per-path Cache-Control
 * rewrite), connection pool, and protocols. Only the cache directory source
 * differs per platform (caller-supplied).
 */
internal fun baseOkHttpClient(
    cacheDir: File,
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
    // affected, and only on the very first launch.
    val initialConfig = okHttpConfigProvider.config.value
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
        // Self-signed trust layer: SSLContext + hostname verifier that read
        // the granted set from the config StateFlow AT HANDSHAKE TIME (same
        // dynamic-config contract as the timeout interceptor below), so the
        // base client — and every client derived from it via newBuilder(),
        // which shares this sslSocketFactory/hostnameVerifier — honors grants
        // and revokes without any rebuild.
        .applySelfSignedTrust(selfSignedTrustHostsReader(okHttpConfigProvider))
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
                // The library-folder read migrated to GET /Users/{userId}/Views
                // (the MediaFolders branch above now only serves the admin folder
                // editor). Cache keys embed the userId, so no cross-user
                // contamination; folder lists carry no per-item UserData.
                path.startsWith("/Users/") && path.endsWith("/Views") -> 300
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

/** Derived streaming client: read timeout floored at 30s (legacy `@Named("streaming")`). */
internal fun OkHttpClient.toStreamingOkHttpClient(): OkHttpClient {
    val baseReadSec = readTimeoutMillis.toLong() / 1000L
    val streamingReadSec = maxOf(baseReadSec, STREAMING_MIN_READ_TIMEOUT_SEC)
    return newBuilder()
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
internal fun OkHttpClient.toDownloadOkHttpClient(): OkHttpClient = newBuilder()
    .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
    .readTimeout(DOWNLOAD_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
    .writeTimeout(DOWNLOAD_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
    .build()
