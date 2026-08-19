package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the home hot-path sub-call caches inside [LibraryApiClientImpl]
 * (`homeLatestMediaCache` / `homeSimilarCache`): back-to-back `getHomeSections`
 * calls with an identical query must not re-fan-out the underlying
 * `/Items/Latest` request, while `force = true` (pull-to-refresh) must bypass
 * the sub-cache and hit the server again.
 *
 * Setup mirrors [AuthApiClientImplTest]: a real [JellyfinApiEngine] (mocked
 * Jellyfin SDK instance, real OkHttp) so the identity-keyed TtlCaches run for
 * real under a signed-in (server, user) pair. The client is a [spyk] whose
 * network leaf overrides are stubbed — the sub-cache wrapper logic itself
 * (`getLatestMediaForHome`) is the real code under test, and the underlying
 * [LibraryApiClientImpl.getLatestMedia] call count is the observable.
 */
class LibraryApiClientImplTest {

    private lateinit var client: LibraryApiClientImpl

    private val testServer = ServerInfo(
        id = "server-1",
        name = "Test Server",
        address = "https://test.example.com",
    )

    private val testUser = UserInfo(
        id = "user-1",
        name = "testuser",
        serverAddress = "https://test.example.com",
        accessToken = "token-123",
        serverId = "server-1",
    )

    /** Latest Media only: the smallest query that still drives the sub-cache path. */
    private val latestOnlyQuery = HomeSectionQuery(
        enabledSections = setOf(HomeSectionType.LATEST_MEDIA),
    )

    @Before
    fun setup() {
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        val engine = JellyfinApiEngine(
            context = mockk(relaxed = true),
            jellyfinLazy = dagger.Lazy { jellyfin },
            okHttpClientLazy = dagger.Lazy { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        // A signed-in (server, user) pair so the sub-caches key off a real
        // CacheIdentity instead of the pre-login UNKNOWN fallback.
        engine.updateServer(testServer)
        engine.updateUser(testUser)

        client = spyk(LibraryApiClientImpl(engine, mockk(relaxed = true)))
        coEvery { client.getLibraryFolders() } returns Result.success(
            listOf(LibraryFolder(id = "movies", name = "Movies", collectionType = "movies")),
        )
        coEvery { client.getLatestMedia(any(), any()) } returns Result.success(
            listOf(latestItem("movie-1")),
        )
    }

    private fun latestItem(id: String) = MediaItem(
        id = id,
        name = "Latest $id",
        mediaType = MediaType.MOVIE,
    )

    @Test
    fun `identical back-to-back queries hit the latest-media sub-cache`() = runTest {
        val first = client.getHomeSections(latestOnlyQuery)
        val second = client.getHomeSections(latestOnlyQuery)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        // The second call served the folder's latest-media row from the
        // sub-cache instead of re-hitting /Items/Latest.
        coVerify(exactly = 1) { client.getLatestMedia(any(), any()) }
    }

    @Test
    fun `forced query bypasses the latest-media sub-cache`() = runTest {
        val first = client.getHomeSections(latestOnlyQuery)
        val forced = client.getHomeSections(latestOnlyQuery, force = true)

        assertTrue(first.isSuccess)
        assertTrue(forced.isSuccess)
        // Pull-to-refresh re-issued the underlying request instead of serving
        // the sub-cached row from the first call.
        coVerify(exactly = 2) { client.getLatestMedia(any(), any()) }
    }

    @Test
    fun `forced query leaves the sub-cache usable for subsequent normal reads`() = runTest {
        client.getHomeSections(latestOnlyQuery)                    // populates: 1 fetch
        client.getHomeSections(latestOnlyQuery, force = true)      // bypasses:  2 fetches
        client.getHomeSections(latestOnlyQuery)                    // cache hit: still 2

        coVerify(exactly = 2) { client.getLatestMedia(any(), any()) }
    }
}
