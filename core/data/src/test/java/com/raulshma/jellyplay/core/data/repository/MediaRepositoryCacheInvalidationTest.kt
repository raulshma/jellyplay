package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LrcLibApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Regression tests: [MediaRepositoryImpl] must invalidate its TTL caches when the
 * active server or user changes so that the next user doesn't see the previous user's data
 * (privacy + correctness).
 *
 * The cache-invalidation observer runs on `Dispatchers.Default` (a real dispatcher, not the
 * test dispatcher), so tests use [runBlocking] + a short [delay] to let the collector process
 * each flow emission before asserting.
 */
class MediaRepositoryCacheInvalidationTest {

    private val serverFlow = MutableStateFlow<ServerInfo?>(null)
    private val userFlow = MutableStateFlow<UserInfo?>(null)
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)

    private fun buildRepository(): MediaRepositoryImpl {
        every { apiClient.currentServer } returns serverFlow
        every { apiClient.currentUser } returns userFlow
        coEvery { apiClient.getMediaDetail(any()) } returns Result.success(mockk<MediaDetail>(relaxed = true))
        every { networkMonitor.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        val lrcLibApi: LrcLibApi = mockk(relaxed = true)
        val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
        val homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true)
        val playedStateSync: PlayedStateSync = mockk(relaxed = true)
        val offlineRepository: OfflineRepository = mockk(relaxed = true)
        val episodeCatalogue = com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl(
            apiClient,
            offlineRepository,
        )
        return MediaRepositoryImpl(
            apiClient,
            lrcLibApi,
            lyricsCacheDao,
            homeSectionCacheDao,
            networkMonitor,
            playedStateSync,
            episodeCatalogue,
        )
    }

    /** Waits long enough for the `Dispatchers.Default` collector to observe the latest emission. */
    private suspend fun waitForCacheObserver() {
        delay(150)
    }

    @Test
    fun `getMediaDetail caches result on repeat calls`() = runBlocking {
        val repository = buildRepository()
        repository.getMediaDetail("item-1")
        repository.getMediaDetail("item-1")
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache survives an identity emission that does not change`() = runBlocking {
        val repository = buildRepository()
        userFlow.value = userInfo("user-A")
        serverFlow.value = serverInfo("server-1")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        repository.getMediaDetail("item-1")

        // Re-emit the same identity — no invalidation expected.
        serverFlow.value = serverInfo("server-1")
        userFlow.value = userInfo("user-A")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is invalidated when user changes`() = runBlocking {
        val repository = buildRepository()
        userFlow.value = userInfo("user-A")
        serverFlow.value = serverInfo("server-1")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        userFlow.value = userInfo("user-B")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is invalidated when server changes`() = runBlocking {
        val repository = buildRepository()
        userFlow.value = userInfo("user-A")
        serverFlow.value = serverInfo("server-1")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        serverFlow.value = serverInfo("server-2")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is NOT invalidated on first emission`() = runBlocking {
        val repository = buildRepository()

        // Session restore: identity is established before the first fetch (the
        // realistic ordering — DataStore restores server/user, then the UI
        // loads). The observer must treat this as a restore, not a switch.
        userFlow.value = userInfo("user-A")
        serverFlow.value = serverInfo("server-1")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        // Re-emit the same identity — still a restore, no invalidation expected.
        serverFlow.value = serverInfo("server-1")
        userFlow.value = userInfo("user-A")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is invalidated on logout (user becomes null)`() = runBlocking {
        val repository = buildRepository()
        userFlow.value = userInfo("user-A")
        serverFlow.value = serverInfo("server-1")
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        userFlow.value = null
        waitForCacheObserver()

        repository.getMediaDetail("item-1")
        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
    }

    private fun userInfo(id: String) = UserInfo(
        id = id,
        name = id,
        serverAddress = "https://example.com",
        accessToken = "token",
        serverId = "server-1",
        isAdmin = false,
        maxParentalAgeRating = null,
        primaryImageTag = null,
        enabledFolderIds = emptyList(),
    )

    private fun serverInfo(id: String) = ServerInfo(
        id = id,
        name = "server-$id",
        address = "https://example.com",
        userId = null,
        accessToken = null,
    )
}
