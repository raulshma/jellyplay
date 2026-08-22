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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    private lateinit var engine: JellyfinApiEngine

    private val testServer = ServerInfo(
        id = "server-1",
        name = "Test Server",
        address = "https://test.example.com",
    )

    private val testUser = UserInfo(
        id = "11111111-1111-4111-8111-111111111111",
        name = "testuser",
        serverAddress = "https://test.example.com",
        accessToken = "token-123",
        serverId = "server-1",
    )

    /** Latest Media only: the smallest query that still drives the sub-cache path. */
    private val latestOnlyQuery = HomeSectionQuery(
        enabledSections = setOf(HomeSectionType.LATEST_MEDIA),
    )

    @BeforeTest
    fun setup() {
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        engine = JellyfinApiEngine(
            jellyfinLazy = dagger.Lazy { jellyfin },
            okHttpClientLazy = dagger.Lazy { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
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
        client.getHomeSections(latestOnlyQuery, force = true)      // bypasses: 2 fetches
        client.getHomeSections(latestOnlyQuery)                    // cache hit: still 2

        coVerify(exactly = 2) { client.getLatestMedia(any(), any()) }
    }

    @Test
    fun `forced query memoises the pulled rows for subsequent normal reads`() = runTest {
        // The forced fetch must refresh the sub-cache, not just bypass its
        // read: otherwise the next (non-forced) periodic refresh would serve
        // the PRE-pull rows for up to the TTL and freshly-swiped content
        // would visibly revert.
        coEvery { client.getLatestMedia(any(), any()) } returnsMany listOf(
            Result.success(listOf(latestItem("stale-row"))),
            Result.success(listOf(latestItem("pulled-row"))),
        )

        client.getHomeSections(latestOnlyQuery)                // fetch 1: stale-row
        client.getHomeSections(latestOnlyQuery, force = true)  // fetch 2: pulled-row

        // No third fetch — and the served rows are the PULLED ones.
        coVerify(exactly = 2) { client.getLatestMedia(any(), any()) }
        val sections = client.getHomeSections(latestOnlyQuery).getOrThrow().sections
        assertTrue(sections.any { it.items.any { item -> item.id == "pulled-row" } })
    }

    private val favoriteItemId = FAVORITE_ITEM_ID

    /**
     * Minimal recording [ApiClient]: the favorite paths run the REAL
     * `UserLibraryApi` over it (mockk can't proxy the final operations
     * classes), with every request answered by an empty-object 200 whose
     * `{}` body decodes to an all-defaults DTO.
     */
    private class RecordingApiClient : org.jellyfin.sdk.api.client.ApiClient() {
        val requests = mutableListOf<String>()
        override val baseUrl = "https://test.example.com"
        override val accessToken = "token-123"
        override val clientInfo = org.jellyfin.sdk.model.ClientInfo(name = "test", version = "1.0.0")
        override val deviceInfo = org.jellyfin.sdk.model.DeviceInfo(id = "test", name = "test")
        override val httpClientOptions = org.jellyfin.sdk.api.client.HttpClientOptions()
        override val webSocket: org.jellyfin.sdk.api.sockets.SocketApi = mockk(relaxed = true)
        override fun update(
            baseUrl: String?,
            accessToken: String?,
            clientInfo: org.jellyfin.sdk.model.ClientInfo,
            deviceInfo: org.jellyfin.sdk.model.DeviceInfo,
        ) = Unit
        override suspend fun request(
            method: org.jellyfin.sdk.api.client.HttpMethod,
            pathTemplate: String,
            pathParameters: Map<String, Any?>,
            queryParameters: Map<String, Any?>,
            requestBody: Any?,
        ): org.jellyfin.sdk.api.client.RawResponse {
            requests += "${method.name} $pathTemplate"
            // UserItemDataDto has six REQUIRED fields, so the body must be
            // complete (the favorite paths ignore the content anyway).
            val body = """
                {"PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,
                "Played":false,"Key":"k","ItemId":"$FAVORITE_ITEM_ID"}
            """.trimIndent()
            return org.jellyfin.sdk.api.client.RawResponse(body.toByteArray(), 200, emptyMap())
        }
    }

    @Test
    fun `setFavorite seeds the favorite cache toggleFavorite reads`() = runTest {
        // Both favorite paths must share one cache key: seeding via
        // setFavorite(true) then toggling with currentIsFavorite = null has
        // to read the seeded flag (unmark → false) instead of re-fetching —
        // a cache miss would fetch userData.isFavorite = false and mark → true.
        val api = RecordingApiClient()
        engine.updateApi(api)

        client.setFavorite(favoriteItemId, true).getOrThrow()
        val toggled = client.toggleFavorite(favoriteItemId, null).getOrThrow()

        assertFalse(toggled)
        // Seed-POST then the toggle's unmark-DELETE — and crucially no GET:
        // a cache miss would first re-fetch the item (GET) and then mark
        // (POST) with toggled = true.
        assertEquals(
            listOf(
                "POST /UserFavoriteItems/{itemId}",
                "DELETE /UserFavoriteItems/{itemId}",
            ),
            api.requests,
        )
    }

    private companion object {
        /** Real UUID: the favorite paths pass it through String.toUUID(). */
        const val FAVORITE_ITEM_ID = "2a2a2a2a-1111-4222-8222-333333333333"
    }
}
