package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Regression tests: [MediaRepositoryImpl] must invalidate its TTL caches when the
 * active server or user changes so that the next user doesn't see the previous user's data
 * (privacy + correctness).
 *
 * Port of the orphaned legacy `:core:data` suite of the same name onto the
 * shared module's 8-arg MediaRepositoryImpl constructor.
 *
 * The cache-invalidation observer runs on `Dispatchers.Default` (a real dispatcher, not the
 * test dispatcher — [mediaRepoStackFor]'s default), so tests use [runBlocking] + a short [delay]
 * to let the collector process each flow emission before asserting.
 */
class MediaRepositoryCacheInvalidationTest {

    // Atomic (server, user) session flow consumed by the shared HomeSession
    // detector — one value per identity step (see JellyfinApiEngine.session).
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val apiClient: LibraryApiClient = mockk(relaxed = true)

    private fun buildRepository(): MediaRepositoryImpl {
        coEvery { apiClient.getMediaDetail(any()) } returns Result.success(mockk<MediaDetail>(relaxed = true))
        return mediaRepoStackFor(
            libraryApiClient = apiClient,
            sessionFlow = sessionFlow,
        ).repository
    }

    /** Waits long enough for the `Dispatchers.Default` collector to observe the latest emission. */
    private suspend fun waitForCacheObserver() {
        delay(150)
    }

    @Test
    fun `getMediaDetail caches result on repeat calls`() = runBlocking {
        val repository = buildRepository()
        repository.getMediaDetail("item-1", force = false)
        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache survives an identity emission that does not change`() = runBlocking {
        val repository = buildRepository()
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        repository.getMediaDetail("item-1", force = false)

        // Re-emit the same identity (new pair object, same ids) — no
        // invalidation expected.
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is invalidated when user changes`() = runBlocking {
        val repository = buildRepository()
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-B"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is invalidated when server changes`() = runBlocking {
        val repository = buildRepository()
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        sessionFlow.value = ActiveSession(serverInfo("server-2"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is NOT invalidated on first emission`() = runBlocking {
        val repository = buildRepository()

        // Session restore: identity is established before the first fetch (the
        // realistic ordering — DataStore restores server/user, then the UI
        // loads). The observer must treat this as a restore, not a switch.
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        // Re-emit the same identity (new pair object, same ids) — still a
        // restore, no invalidation expected.
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `cache is invalidated on logout (user becomes null)`() = runBlocking {
        val repository = buildRepository()
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo("user-A"))
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }

        sessionFlow.value = null
        waitForCacheObserver()

        repository.getMediaDetail("item-1", force = false)
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
