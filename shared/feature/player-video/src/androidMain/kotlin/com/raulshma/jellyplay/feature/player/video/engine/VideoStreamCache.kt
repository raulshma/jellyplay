package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.raulshma.jellyplay.core.data.cache.VIDEO_CACHE_DIR_NAME
import com.raulshma.jellyplay.core.data.playback.STRIP_SAFE_QUERY_PARAMS
import com.raulshma.jellyplay.core.data.playback.isSessionKeyedUrl
import com.raulshma.jellyplay.core.data.playback.stripVolatileQueryParams
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Owns the video byte cache ([SimpleCache]) and builds [CacheDataSource.Factory]
 * instances that wrap an upstream [DataSource.Factory] so ExoPlayer reads
 * side-cache bytes into the cache on every fetch — backward seeks and segment
 * re-reads are then served from disk instead of re-fetching from the network.
 *
 * This mirrors [com.raulshma.jellyplay.core.data.playback.AudioStreamCache]
 * (the audio side's reference implementation), deliberately as a sibling in the
 * video player module rather than a shared abstraction: the two caches differ
 * in scoping (video is gated to direct-play/direct-stream URLs by
 * [ExoPlayerEngine], audio is not) and in sizing source (audio reads the
 * user-configured AudioCacheStore; video uses the fixed
 * [DEFAULT_CACHE_SIZE_MB] until a settings surface lands).
 *
 * OkHttp's disk Cache cannot do this job: ExoPlayer's progressive source
 * issues `206 Partial Content` range requests, which OkHttp never stores (it
 * only caches complete 200 responses). [CacheDataSource] is the only layer
 * that can serve and fill a byte-range cache.
 *
 * The cache directory lives at `cacheDir/video_cache`
 * ([VIDEO_CACHE_DIR_NAME], a sibling of audio's `audio_cache`) and is **not**
 * swept by [com.raulshma.jellyplay.core.data.cache.CacheManager] (see the
 * exclusion added there). Cache keys strip token rotation via
 * [STRIP_SAFE_QUERY_PARAMS] (`api_key`) so re-auth does not invalidate
 * cached content. Session-keyed URLs are never key-normalized — distinct
 * sessions are distinct content ([isSessionKeyedUrl]); eligibility gating in
 * [ExoPlayerEngine] rejects them, and the key factory is the backstop.
 *
 * [SimpleCache] requires a single instance per directory, hence this
 * process-wide singleton (Koin-owned since the wave 7C KMP move; was a
 * Hilt `@Singleton`) holder with construction serialized by
 * [initLock] ([ensureCacheLocked]). If the cache directory cannot be opened
 * (disk full, permissions), [getCacheDataSourceFactory] degrades to
 * passthrough — returning the upstream factory unchanged — so playback never
 * breaks.
 */
class VideoStreamCache(
    private val context: Context,
) {
    private val cacheDir = File(context.cacheDir, VIDEO_CACHE_DIR_NAME)

    private val cacheKeyFactory = CacheKeyFactory { dataSpec ->
        val url = dataSpec.key ?: dataSpec.uri.toString()
        // Session-keyed URLs must never be key-normalized: distinct sessions
        // are distinct content, and stripping would collide them onto one
        // key (StreamCacheKeys contract). Eligibility gating in
        // ExoPlayerEngine already rejects these; this is the backstop.
        if (isSessionKeyedUrl(url)) url
        else stripVolatileQueryParams(url, STRIP_SAFE_QUERY_PARAMS)
    }

    @Volatile private var cache: SimpleCache? = null

    /**
     * Set once opening the cache has failed: a failed open (missing dir,
     * unusable index DB) is persistent for the process lifetime, and
     * [getCacheDataSourceFactory] runs on the player thread per `load()` —
     * retrying mkdirs + a SQLite open there on every load would put disk
     * work on the latency path. Passthrough until restart.
     */
    private var cacheUnavailable = false

    /** Serializes cache construction between [prewarm] and the player thread. */
    private val initLock = ReentrantLock()

    private fun ensureCacheLocked(): SimpleCache? {
        cache?.let { return it }
        if (cacheUnavailable) return null
        val sizeBytes = DEFAULT_CACHE_SIZE_MB.coerceAtLeast(1).toLong() * 1024L * 1024L
        val evictor = LeastRecentlyUsedCacheEvictor(sizeBytes)
        val dir = cacheDir.apply { mkdirs() }
        if (!dir.exists() || !dir.isDirectory) {
            cacheUnavailable = true
            return null
        }
        val dbProvider = try {
            StandaloneDatabaseProvider(context)
        } catch (_: Exception) {
            cacheUnavailable = true
            return null
        }
        return try {
            SimpleCache(dir, evictor, dbProvider).also { cache = it }
        } catch (_: Exception) {
            // SimpleCache owns the provider only once construction succeeds
            runCatching { dbProvider.close() }
            cacheUnavailable = true
            null
        }
    }

    /**
     * Opens the cache (index DB + span scan) ahead of the first playback so
     * [getCacheDataSourceFactory] never pays that disk work on the player
     * thread. Call from a background dispatcher at startup; a
     * [getCacheDataSourceFactory] call racing this takes the passthrough
     * path instead of waiting on the lock.
     */
    fun prewarm() {
        initLock.withLock { ensureCacheLocked() }
    }

    /**
     * Wraps [upstream] in a [CacheDataSource.Factory]. If the cache is
     * unavailable, returns [upstream] unchanged (passthrough).
     *
     * Unlike AudioStreamCache there is no memoized factory pair: the video
     * engine builds a fresh upstream per `load()` (it embeds the item's auth
     * headers via ResolvingDataSource), so an identity fast path could never
     * hit. Each call still wraps the SAME process-wide [SimpleCache] — the
     * expensive part — and [CacheDataSource.Factory] construction is cheap.
     */
    fun getCacheDataSourceFactory(upstream: DataSource.Factory): DataSource.Factory {
        var sc = cache
        if (sc == null) {
            if (!initLock.tryLock()) {
                // Startup prewarm (IO dispatcher) holds the lock mid disk
                // open — waiting here would put that work on the player
                // thread. This load plays passthrough; the next load()
                // finds the cache ready.
                return upstream
            }
            try {
                sc = ensureCacheLocked()
            } finally {
                initLock.unlock()
            }
        }
        if (sc == null) return upstream
        return CacheDataSource.Factory()
            .setCache(sc)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private companion object {
        /**
         * Cache bound in MB. Matches the audio cache's default bound
         * (AudioCacheStore's `audioCacheSizeMb` default of 1024 MB) so the two
         * transient media caches behave consistently. Fixed for now — a
         * user-facing size setting is a deliberate follow-up.
         */
        const val DEFAULT_CACHE_SIZE_MB = 1024
    }
}
