package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the transient audio byte cache ([SimpleCache]) and builds
 * [CacheDataSource.Factory] instances that wrap an upstream [DataSource.Factory]
 * so ExoPlayer reads side-cache bytes into the cache on every fetch.
 *
 * The cache directory lives at `cacheDir/audio_cache` and is **not** swept by
 * [com.raulshma.jellyplay.core.data.cache.CacheManager] (see the exclusion added
 * there). Cache keys strip the `api_key` query parameter so token rotation
 * does not invalidate cached content.
 *
 * If the cache directory cannot be opened (disk full, permissions),
 * [getCacheDataSourceFactory] degrades to passthrough — returning the upstream
 * factory unchanged — so playback never breaks.
 */
@Singleton
open class AudioStreamCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("streaming") private val streamingOkHttpClient: OkHttpClient,
    private val audioCacheStore: com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Override in tests to point at a temp dir. */
    protected open fun resolveCacheDir(): File = File(context.cacheDir, "audio_cache")

    private val cacheKeyFactory = CacheKeyFactory { dataSpec ->
        stripApiKey(dataSpec.key ?: dataSpec.uri.toString())
    }

    private var cache: SimpleCache? = null
    @Volatile private var available = false

    @Synchronized
    private fun ensureCache(): SimpleCache? {
        cache?.let { return it }
        return try {
            val sizeMb = audioCacheStore.audioCache.value.audioCacheSizeMb.coerceAtLeast(1)
            val sizeBytes = sizeMb.toLong() * 1024L * 1024L
            val evictor = LeastRecentlyUsedCacheEvictor(sizeBytes)
            val dir = resolveCacheDir().apply { mkdirs() }
            if (!dir.exists() || !dir.isDirectory) {
                available = false
                return null
            }
            val dbProvider = StandaloneDatabaseProvider(context)
            val sc = SimpleCache(dir, evictor, dbProvider)
            cache = sc
            available = true
            sc
        } catch (e: Exception) {
            available = false
            null
        }
    }

    fun isAvailable(): Boolean {
        if (!available) ensureCache()
        return available
    }

    /**
     * Wraps [upstream] in a [CacheDataSource.Factory]. If the cache is
     * unavailable, returns [upstream] unchanged (passthrough).
     *
     * Production callers always pass our own [buildUpstreamFactory] result, so
     * both the upstream chain and the derived cache factory are process-stable
     * — memoized in [cachedUpstreamFactory]/[cachedCacheFactory] instead of
     * being rebuilt per player creation and per [warmTrack] prefetch.
     * Invalidated by [clear]; custom upstreams (tests) bypass the memo.
     *
     * Synchronized with [clear] so the memo can never hand out a factory
     * wrapping a [SimpleCache] that [clear] just released: without the lock a
     * reader between [ensureCache] and the memo write would keep using a dead
     * cache (silently — FLAG_IGNORE_CACHE_ON_ERROR masks it).
     */
    @Synchronized
    fun getCacheDataSourceFactory(upstream: DataSource.Factory): DataSource.Factory {
        if (upstream === cachedUpstreamFactory) {
            cachedCacheFactory?.let { return it }
        }
        val sc = ensureCache() ?: return upstream
        val factory = CacheDataSource.Factory()
            .setCache(sc)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        if (upstream === cachedUpstreamFactory) {
            cachedCacheFactory = factory
        }
        return factory
    }

    @Volatile private var cachedUpstreamFactory: DataSource.Factory? = null
    @Volatile private var cachedCacheFactory: DataSource.Factory? = null

    /**
     * Builds the OkHttp-backed upstream factory used by both player and warmer.
     * Synchronized like the other memo accessors so concurrent callers can't
     * build racing instances (a loser's write would make callers hold a
     * factory that no longer `===`s the memo, silently bypassing the
     * [cachedCacheFactory] fast path) and so [clear]'s invalidation can't
     * interleave with a memo write.
     */
    @Synchronized
    fun buildUpstreamFactory(): DataSource.Factory {
        cachedUpstreamFactory?.let { return it }
        val httpFactory = OkHttpDataSource.Factory(streamingOkHttpClient)
            .setUserAgent("JellyPlay")
        return DefaultDataSource.Factory(context, httpFactory).also { cachedUpstreamFactory = it }
    }

    /** Bytes currently held in the cache. */
    fun cacheSpaceBytes(): Long = cache?.cacheSpace ?: 0L

    /** Cached bytes for a given URL (api_key stripped from the key). */
    fun getCachedBytes(url: String): Long {
        val sc = cache ?: return 0L
        val key = stripApiKey(url)
        return sc.getCachedBytes(key, 0L, Long.MAX_VALUE).toLong()
    }

    /** Exposed for testing + the prefetch engine's skip-if-cached check. */
    fun cacheKeyForUrl(url: String): String = stripApiKey(url)

    /**
     * Warms the cache for [url] by streaming its bytes via [CacheWriter].
     * Discovers Content-Length via a HEAD request; falls back to read-to-EOF
     * if the server omits it. Returns bytes written on success, or a failure
     * result (never throws — caller decides whether to retry).
     */
    suspend fun warmTrack(url: String): Result<Long> = withContext(Dispatchers.IO) {
        val sc = ensureCache() ?: return@withContext Result.failure(IllegalStateException("cache unavailable"))
        val key = stripApiKey(url)
        try {
            val length = discoverContentLength(url)
            val dataSpec = DataSpec.Builder()
                .setUri(url)
                .setKey(key)
                .setPosition(0)
                .setLength(length)
                .build()
            val cacheDs = getCacheDataSourceFactory(buildUpstreamFactory()).createDataSource() as CacheDataSource
            val writer = CacheWriter(cacheDs, dataSpec, null, null)
            suspendCancellableCoroutine { cont ->
                scope.launch(Dispatchers.IO) {
                    try {
                        writer.cache()
                        cont.resume(sc.getCachedBytes(key, 0L, Long.MAX_VALUE).toLong())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            }
            Result.success(sc.getCachedBytes(key, 0L, Long.MAX_VALUE).toLong())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Releases the cache, deletes its directory, and reopens empty. */
    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        // Lock the cache instance — the same monitor the @Synchronized accessors
        // use. A bare `this` here would capture the withContext CoroutineScope
        // and race the readers instead of excluding them.
        synchronized(this@AudioStreamCache) {
            cache?.release()
            cache = null
            available = false
            // The memoized factories reference the released SimpleCache —
            // drop them so the next request rebuilds against the fresh cache.
            cachedCacheFactory = null
            cachedUpstreamFactory = null
        }
        resolveCacheDir().deleteRecursively()
        resolveCacheDir().mkdirs()
        ensureCache()
    }

    private fun discoverContentLength(url: String): Long {
        return try {
            streamingOkHttpClient.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
                resp.header("Content-Length")?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
            }
        } catch (e: Exception) {
            C.LENGTH_UNSET.toLong()
        }
    }

    /** Strips `api_key=...` from the query string so cache keys are token-invariant. */
    internal fun stripApiKey(url: String): String {
        return try {
            val uri = URI(url)
            val query = uri.rawQuery ?: return url
            val filtered = query.split("&").filterNot { it.startsWith("api_key=") }.joinToString("&")
            val newQuery = if (filtered.isEmpty()) null else filtered
            URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, newQuery, uri.fragment).toString()
        } catch (e: Exception) {
            url.replace(Regex("[?&]api_key=[^&]*"), "").replace(Regex("\\?&"), "?").replace(Regex("&&"), "&")
        }
    }
}
