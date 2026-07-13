package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioCachePolicyGuardTest {

    private val preferencesStore: UserPreferencesStore = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private val networkStatus = MutableStateFlow(NetworkStatus.Online)
    private val isMetered = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setup() {
        every { networkMonitor.networkStatus } returns networkStatus
        every { networkMonitor.isMetered } returns isMetered
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(audioCacheNetworkPolicy = AudioCacheNetworkPolicy.WIFI_ONLY)
        )
    }

    @Test
    fun `OFF policy disables prefetch`() = runTest {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(audioCacheNetworkPolicy = AudioCacheNetworkPolicy.OFF)
        )
        val guard = AudioCachePolicyGuard(preferencesStore, networkMonitor, scope)
        assertFalse(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `WIFI_ONLY on unmetered allows prefetch`() = runTest {
        isMetered.value = false
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(preferencesStore, networkMonitor, scope)
        assertTrue(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `WIFI_ONLY on metered blocks prefetch`() = runTest {
        isMetered.value = true
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(preferencesStore, networkMonitor, scope)
        assertFalse(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `ANY_NETWORK on metered allows prefetch under cap`() = runTest {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(
                audioCacheNetworkPolicy = AudioCacheNetworkPolicy.ANY_NETWORK,
                audioCacheCellularMonthlyCapMb = 500,
            )
        )
        isMetered.value = true
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(preferencesStore, networkMonitor, scope)
        assertTrue(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `ANY_NETWORK on metered blocks when cap exceeded`() = runTest {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(
                audioCacheNetworkPolicy = AudioCacheNetworkPolicy.ANY_NETWORK,
                audioCacheCellularMonthlyCapMb = 500,
            )
        )
        isMetered.value = true
        networkStatus.value = NetworkStatus.Online
        val guard = AudioCachePolicyGuard(preferencesStore, networkMonitor, scope)
        guard.recordCellularPrefetch(500L * 1024 * 1024)
        assertFalse(guard.isPrefetchAllowed.first())
    }

    @Test
    fun `offline blocks prefetch`() = runTest {
        networkStatus.value = NetworkStatus.Offline
        val guard = AudioCachePolicyGuard(preferencesStore, networkMonitor, scope)
        assertFalse(guard.isPrefetchAllowed.first())
    }
}
