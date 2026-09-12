package com.raulshma.jellyplay.core.data.playback

import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioStreamCacheTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val preferencesStore: AudioCacheStore = mockk(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private lateinit var cache: AudioStreamCache
    private lateinit var testDir: java.io.File

    @Before
    fun setup() {
        every { preferencesStore.audioCache } returns MutableStateFlow(
            AudioCacheSlice(audioCacheSizeMb = 64)
        )
        testDir = tmpFolder.newFolder("audio_cache")
        cache = object : AudioStreamCache(
            context = context,
            streamingOkHttpClient = okhttp3.OkHttpClient(),
            audioCacheStore = preferencesStore,
            scope = scope,
        ) {
            override fun resolveCacheDir() = testDir
        }
    }

    @Test
    fun `cache opens successfully and is available`() {
        assertTrue(cache.isAvailable())
    }

    @Test
    fun `getCacheDataSourceFactory wraps upstream in CacheDataSource`() {
        val upstream = DefaultDataSource.Factory(context)
        val factory = cache.getCacheDataSourceFactory(upstream)
        val ds = factory.createDataSource()
        assertTrue("Expected CacheDataSource, got ${ds::class.java}", ds is CacheDataSource)
    }

    @Test
    fun `cache key factory strips api_key query param`() {
        val urlWithToken = "https://server.local/Audio/abc/universal?deviceId=x&api_key=SECRET123"
        val urlWithDifferentToken = "https://server.local/Audio/abc/universal?deviceId=x&api_key=DIFFERENT456"
        val key1 = cache.cacheKeyForUrl(urlWithToken)
        val key2 = cache.cacheKeyForUrl(urlWithDifferentToken)
        assertEquals("api_key must be stripped so token rotation doesn't break cache hits", key1, key2)
    }

    @Test
    fun `clear empties the cache directory`() = runTest {
        cache.clear()
        assertEquals(0L, cache.cacheSpaceBytes())
    }

    @Test
    fun `getCachedBytes returns zero for uncached url`() {
        assertEquals(0L, cache.getCachedBytes("https://server.local/Audio/xyz/universal?api_key=k"))
    }

    @Test
    fun `passthrough factory returned when cache unavailable`() {
        // Point the cache dir at a path whose parent is a *file*, not a
        // directory — mkdirs() cannot create a directory under a file, so
        // SimpleCache initialization will fail.
        val blockingFile = tmpFolder.newFile("blocker")
        val brokenCache = object : AudioStreamCache(
            context = context,
            streamingOkHttpClient = okhttp3.OkHttpClient(),
            audioCacheStore = preferencesStore,
            scope = scope,
        ) {
            override fun resolveCacheDir() = java.io.File(blockingFile, "audio_cache")
        }
        assertFalse(brokenCache.isAvailable())
        val upstream = DefaultDataSource.Factory(context)
        val factory = brokenCache.getCacheDataSourceFactory(upstream)
        assertEquals("Passthrough should return upstream factory unchanged", upstream, factory)
    }
}
