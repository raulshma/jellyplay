package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SystemTimeSource
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        client = LibraryApiClientImpl(engine, mockk(relaxed = true), SystemTimeSource())
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

        /** The raw query-parameter maps each request carried (for the isMissing param mapping). */
        val queries = mutableListOf<Map<String, Any?>>()
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
            queries += queryParameters
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

    // ── missing-episodes param mapping ───────────────────────────────────

    /** Minimal items envelope so the episode paths decode without error. */
    private val emptyItemsBody = """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""

    @Test
    fun `getEpisodes hide sends isMissing false showing omits it`() = runTest {
        // jellyfin-web parity: hiding (the default) passes isMissing=false so
        // the server drops its virtual (missing/unaired) placeholders; showing
        // omits the filter entirely (null → the SDK drops the param).
        val api = RecordingApiClient(responseBody = emptyItemsBody)
        engine.updateApi(api)

        client.getEpisodes(EPISODE_SERIES_ID, EPISODE_SEASON_ID, isMissing = false).getOrThrow()
        assertEquals(false, api.queries.last()["isMissing"])

        client.getEpisodes(EPISODE_SERIES_ID, EPISODE_SEASON_ID, isMissing = null).getOrThrow()
        assertNull(api.queries.last()["isMissing"])
    }

    @Test
    fun `getAllEpisodes hide sends isMissing false showing omits it`() = runTest {
        val api = RecordingApiClient(responseBody = emptyItemsBody)
        engine.updateApi(api)

        client.getAllEpisodes(EPISODE_SERIES_ID, isMissing = false).getOrThrow()
        assertEquals(false, api.queries.last()["isMissing"])

        client.getAllEpisodes(EPISODE_SERIES_ID, isMissing = null).getOrThrow()
        assertNull(api.queries.last()["isMissing"])
    }

    // ── classic-rows kind narrowing (#168) ──────────────────────────────────

    /** Normalizes an SDK collection query param to a comma string for assertions. */
    private fun queryParam(map: Map<String, Any?>, name: String): String = when (val v = map[name]) {
        is Collection<*> -> v.joinToString(",") { it.toString() }
        null -> ""
        else -> v.toString()
    }

    @Test
    fun `the classic-rows narrowing rides the resume and latest wire queries`() = runTest {
        val api = RecordingApiClient(responseBody = emptyItemsBody)
        engine.updateApi(api)
        client.getContinueWatching(limit = 20, includeKinds = listOf("Episode", "Movie")).getOrThrow()

        // /Users/{userId}/Items/Latest answers a bare JSON array, not an envelope.
        val latestApi = RecordingApiClient(responseBody = "[]")
        engine.updateApi(latestApi)
        client.getLatestMedia(parentId = LATEST_FOLDER_ID, limit = 16, includeKinds = listOf("Series")).getOrThrow()

        assertTrue(
            queryParam(api.queries[0], "includeItemTypes").contains("Episode", ignoreCase = true) &&
                queryParam(api.queries[0], "includeItemTypes").contains("Movie", ignoreCase = true),
            "the classic resume query must carry IncludeItemTypes",
        )
        assertEquals(
            "Series",
            queryParam(latestApi.queries[0], "includeItemTypes"),
            "the classic latest query must pin the TV folder to Series",
        )
        // The Series pin bypasses the grouped latest route: GroupBy(SeriesName)
        // over Series-only rows self-empties on 12.x and truncates on 10.x
        // (both live-verified). Plain GetItemList honors IncludeItemTypes.
        assertEquals(
            "false",
            queryParam(latestApi.queries[0], "groupItems"),
            "the classic TV pin must drop groupItems",
        )
    }

    @Test
    fun `modern resume and latest queries omit IncludeItemTypes entirely`() = runTest {
        val api = RecordingApiClient(responseBody = emptyItemsBody)
        engine.updateApi(api)
        client.getContinueWatching(limit = 20).getOrThrow()

        val latestApi = RecordingApiClient(responseBody = "[]")
        engine.updateApi(latestApi)
        client.getLatestMedia(parentId = LATEST_FOLDER_ID, limit = 16).getOrThrow()

        assertEquals("", queryParam(api.queries[0], "includeItemTypes"))
        assertEquals("", queryParam(latestApi.queries[0], "includeItemTypes"))
        // Modern rows keep the SDK default (grouped route): the server's own
        // Series/Season/Episode container selection is the feature.
        assertEquals("true", queryParam(latestApi.queries[0], "groupItems"))
    }

    private companion object {
        /** Real UUID: the favorite paths pass it through String.toUUID(). */
        const val FAVORITE_ITEM_ID = "2a2a2a2a-1111-4222-8222-333333333333"

        /** Real UUIDs: the resume mapper reads Id through the same path. */
        const val POISONED_ITEM_ID = "3b3b3b3b-1111-4333-8333-444444444444"
        const val RESUMABLE_ITEM_ID = "4c4c4c4c-1111-4444-8444-555555555555"

        /** Real UUIDs: the episodes paths pass both through String.toUUID(). */
        const val EPISODE_SERIES_ID = "5d5d5d5d-1111-4555-8555-666666666666"
        const val EPISODE_SEASON_ID = "6e6e6e6e-1111-4666-8666-777777777777"

        /** Real UUID: the latest-media path passes it through String.toUUID(). */
        const val LATEST_FOLDER_ID = "7f7f7f7f-1111-4777-8777-888888888888"
    }
}
