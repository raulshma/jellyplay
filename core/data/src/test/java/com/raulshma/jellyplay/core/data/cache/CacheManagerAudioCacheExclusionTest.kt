package com.raulshma.jellyplay.core.data.cache

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheManagerAudioCacheExclusionTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val networkOfflineStore: NetworkOfflineStore = mockk(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setup() {
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(NetworkOfflineSlice(autoDeleteCache = true))
    }

    @Test
    fun `clearCache preserves audio_cache directory contents`() = runTest {
        val cacheDir = tmpFolder.newFolder("cache")
        val audioCacheDir = File(cacheDir, "audio_cache").apply { mkdirs() }
        File(audioCacheDir, "track.bin").writeBytes(ByteArray(1024))
        File(cacheDir, "http_cache.bin").writeBytes(ByteArray(512))

        val manager = CacheManager(
            context = ApplicationProvider.getApplicationContext(),
            networkOfflineStore = networkOfflineStore,
            appScope = scope,
        ).apply { cacheDirOverride = cacheDir }
        manager.clearCache()

        assertTrue("audio_cache must survive the sweep", audioCacheDir.exists())
        assertTrue("audio_cache contents must survive", File(audioCacheDir, "track.bin").exists())
        assertFalse("http_cache should be deleted", File(cacheDir, "http_cache.bin").exists())
    }

    @Test
    fun `cacheSizeBytes excludes audio_cache directory`() = runTest {
        val cacheDir = tmpFolder.newFolder("cache")
        val audioCacheDir = File(cacheDir, "audio_cache").apply { mkdirs() }
        File(audioCacheDir, "track.bin").writeBytes(ByteArray(2048))
        File(cacheDir, "http_cache.bin").writeBytes(ByteArray(512))

        val manager = CacheManager(
            context = ApplicationProvider.getApplicationContext(),
            networkOfflineStore = networkOfflineStore,
            appScope = scope,
        ).apply { cacheDirOverride = cacheDir }
        val size = manager.cacheSizeBytes()
        // Only http_cache.bin (512) counted; audio_cache (2048) excluded
        assertEquals(512L, size)
    }
}
