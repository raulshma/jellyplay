package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.NetworkStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class AudioCachePolicyGuardTest {

    private val audioCacheStore: AudioCacheStore = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private val networkStatus = MutableStateFlow(NetworkStatus.Online)
    private val isMetered = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun cacheSlice(
        policy: AudioCacheNetworkPolicy = AudioCacheNetworkPolicy.WIFI_ONLY,
        cellularMonthlyCapMb: Int = 500,
    ): AudioCacheSlice = AudioCacheSlice(
        audioCacheNetworkPolicy = policy,
        audioCacheCellularMonthlyCapMb = cellularMonthlyCapMb,
    )

    @BeforeTest
    fun setup() {
        every { networkMonitor.networkStatus } returns networkStatus
        every { networkMonitor.isMetered } returns isMetered
        every { audioCacheStore.audioCache } returns MutableStateFlow(cacheSlice())
    }

    @Test
    fun `OFF policy disables prefetch`() = runTest {
        every { audioCacheStore.audioCache } returns MutableStateFlow(
            cacheSlice(policy = AudioCacheNetworkPolicy.OFF),
        )
        val guard = AudioCachePolicyGuard(audioCacheStore, networkMonitor, scope)
        assertFalse(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `WIFI_ONLY on unmetered allows prefetch`() = runTest {
        isMetered.value = false
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(audioCacheStore, networkMonitor, scope)
        assertTrue(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `WIFI_ONLY on metered blocks prefetch`() = runTest {
        isMetered.value = true
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(audioCacheStore, networkMonitor, scope)
        assertFalse(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `ANY_NETWORK on metered allows prefetch under cap`() = runTest {
        every { audioCacheStore.audioCache } returns MutableStateFlow(
            cacheSlice(
                policy = AudioCacheNetworkPolicy.ANY_NETWORK,
                cellularMonthlyCapMb = 500,
            ),
        )
        isMetered.value = true
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(audioCacheStore, networkMonitor, scope)
        assertTrue(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `ANY_NETWORK on metered blocks when cap exceeded`() = runTest {
        every { audioCacheStore.audioCache } returns MutableStateFlow(
            cacheSlice(
                policy = AudioCacheNetworkPolicy.ANY_NETWORK,
                cellularMonthlyCapMb = 500,
            ),
        )
        isMetered.value = true
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(audioCacheStore, networkMonitor, scope)
        guard.recordCellularPrefetch(500L * 1024 * 1024)
        assertFalse(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `offline blocks prefetch`() = runTest {
        networkStatus.value = NetworkStatus.Offline
        val guard = AudioCachePolicyGuard(audioCacheStore, networkMonitor, scope)
        assertFalse(guard.isPrefetchAllowed.first())
    }
}
