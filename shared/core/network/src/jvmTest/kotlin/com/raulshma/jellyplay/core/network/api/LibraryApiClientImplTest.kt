package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Covers the [LibraryApiClientImpl] behavior that needs a real engine: the
 * favorite-flag cache hand-off between [LibraryApiClientImpl.setFavorite] and
 * [LibraryApiClientImpl.toggleFavorite]. (The home-sections fetch
 * choreography and its TTL sub-call caches moved to the commonMain
 * `HomeSectionsFetcher` — pinned by `HomeSectionsFetcherTest` in commonTest.)
 *
 * Setup mirrors [AuthApiClientImplTest]: a real [JellyfinApiEngine] (mocked
 * Jellyfin SDK instance, real OkHttp) so the identity-keyed TtlCache runs for
 * real under a signed-in (server, user) pair.
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

    @BeforeTest
    fun setup() {
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { jellyfin },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        // A signed-in (server, user) pair so the favorite cache keys off a real
        // CacheIdentity instead of the pre-login UNKNOWN fallback.
        engine.updateServer(testServer)
        engine.updateUser(testUser)

        client = LibraryApiClientImpl(engine, mockk(relaxed = true))
    }

    private val favoriteItemId = FAVORITE_ITEM_ID

    /**
     * Minimal recording [ApiClient]: the favorite paths run the REAL
     * `UserLibraryApi` over it (mockk can't proxy the final operations
     * classes), with every request answered by a 200 whose body decodes to
     * the DTO under test. Defaults to the all-defaults UserItemDataDto the
     * favorite paths need; tests targeting other endpoints pass their own
     * [responseBody].
     */
    private class RecordingApiClient(
        private val responseBody: String = """
            {"PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,
            "Played":false,"Key":"k","ItemId":"$FAVORITE_ITEM_ID"}
        """.trimIndent(),
    ) : org.jellyfin.sdk.api.client.ApiClient() {
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
            return org.jellyfin.sdk.api.client.RawResponse(responseBody.toByteArray(), 200, emptyMap())
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

    @Test
    fun `getContinueWatching drops played rows the server still reports resumable (#157)`() = runTest {
        // /Items/Resume filters on PlaybackPositionTicks > 0 only — it does not
        // exclude played items. A row with Played=true + a stale position is
        // watched yet permanently resumable server-side; the client must drop
        // it so a watched episode never occupies Continue Watching.
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[
                    {"Id":"$POISONED_ITEM_ID","Name":"Poisoned","Type":"Movie",
                     "UserData":{"PlaybackPositionTicks":5000000,"PlayCount":1,
                                 "IsFavorite":false,"Played":true,"Key":"k1",
                                 "ItemId":"$POISONED_ITEM_ID"}},
                    {"Id":"$RESUMABLE_ITEM_ID","Name":"Resumable","Type":"Movie",
                     "UserData":{"PlaybackPositionTicks":3000000,"PlayCount":0,
                                 "IsFavorite":false,"Played":false,"Key":"k2",
                                 "ItemId":"$RESUMABLE_ITEM_ID"}}
                ],"TotalRecordCount":2,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val items = client.getContinueWatching(limit = 20).getOrThrow()

        assertEquals(listOf("Resumable"), items.map { it.name })
    }

    @Test
    fun `getContinueWatching with every row played yields an empty row, not a failure (#157)`() = runTest {
        // All-played edge of the same filter: the row must collapse to empty
        // (rendering "nothing to continue") rather than error or pass rows
        // through because "everything was dropped".
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[
                    {"Id":"$POISONED_ITEM_ID","Name":"Poisoned","Type":"Movie",
                     "UserData":{"PlaybackPositionTicks":5000000,"PlayCount":1,
                                 "IsFavorite":false,"Played":true,"Key":"k1",
                                 "ItemId":"$POISONED_ITEM_ID"}},
                    {"Id":"$RESUMABLE_ITEM_ID","Name":"Also Played","Type":"Episode",
                     "UserData":{"PlaybackPositionTicks":3000000,"PlayCount":2,
                                 "IsFavorite":false,"Played":true,"Key":"k2",
                                 "ItemId":"$RESUMABLE_ITEM_ID"}}
                ],"TotalRecordCount":2,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val items = client.getContinueWatching(limit = 20).getOrThrow()

        assertEquals(emptyList(), items)
    }

    private companion object {
        /** Real UUID: the favorite paths pass it through String.toUUID(). */
        const val FAVORITE_ITEM_ID = "2a2a2a2a-1111-4222-8222-333333333333"

        /** Real UUIDs: the resume mapper reads Id through the same path. */
        const val POISONED_ITEM_ID = "3b3b3b3b-1111-4333-8333-444444444444"
        const val RESUMABLE_ITEM_ID = "4c4c4c4c-1111-4444-8444-555555555555"
    }
}
