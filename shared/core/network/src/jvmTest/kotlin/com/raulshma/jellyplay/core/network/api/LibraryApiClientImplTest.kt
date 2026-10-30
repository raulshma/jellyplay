package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SystemTimeSource
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePortAdapter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.api.MediaType as SdkMediaType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the [LibraryApiClientImpl] behavior that needs a real engine: the
 * favorite-flag cache hand-off — owned by [UserDataApiClientImpl] since the
 * write funnel's carve, driven directly here over the SAME engine — plus the
 * wide client's wire shapes. (The home-sections fetch
 * choreography and its TTL sub-call caches moved to the commonMain
 * `HomeSectionsFetcher` — pinned by `HomeSectionsFetcherTest` in commonTest.)
 *
 * Setup mirrors [AuthApiClientImplTest]: a real [JellyfinApiEngine] (mocked
 * Jellyfin SDK instance, real OkHttp) so the identity-keyed TtlCache runs for
 * real under a signed-in (server, user) pair.
 *
 * The playlist / collection families moved off the wide client to
 * [PlaylistApiClientImpl] / [CollectionApiClientImpl]; their tests here
 * construct the adapters over the SAME test engine and pin the moved wire
 * shapes, and the impl-owned [HomeSectionsCachePort] adapter is exercised
 * through the internal re-exposure the Koin binding resolves.
 */
class LibraryApiClientImplTest {

    private lateinit var client: LibraryApiClientImpl
    private lateinit var userDataClient: UserDataApiClientImpl
    private lateinit var homeLayoutClient: HomeLayoutApiClientImpl
    private lateinit var playlistClient: PlaylistApiClientImpl
    private lateinit var collectionClient: CollectionApiClientImpl
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
        // The router is a relaxed mock with a permanently-null activeAddress:
        // a REAL router + updateServer emits the address and the engine's
        // rebuild collector then races the test's updateApi — losing the race
        // lands in the SDK's createApi default-args static, which NPEs on the
        // mock Jellyfin's null options (see AdminApiClientImplTest's setup
        // note). With the flow stuck at null the collector never fires.
        val router = mockk<ServerAddressRouter>(relaxed = true)
        every { router.activeAddress } returns MutableStateFlow(null)
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { jellyfin },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = router,
        )
        // A signed-in (server, user) pair so the favorite cache keys off a real
        // CacheIdentity instead of the pre-login UNKNOWN fallback.
        engine.updateServer(testServer)
        engine.updateUser(testUser)

        client = LibraryApiClientImpl(engine, mockk(relaxed = true), SystemTimeSource())
        // The user-data write funnel rides its own adapter over the SAME
        // engine — the shape the Koin graph builds in production (the
        // favorite-flag cache lives HERE, not on the wide client anymore).
        userDataClient = UserDataApiClientImpl(
            engine = engine,
            cacheIdentity = { engine.currentCacheIdentity() },
        )
        // The moved families ride their own adapters over the SAME engine —
        // the shape the Koin graph builds in production.
        playlistClient = PlaylistApiClientImpl(engine)
        collectionClient = CollectionApiClientImpl(engine)
        homeLayoutClient = HomeLayoutApiClientImpl(engine, SystemTimeSource())
    }

    private val favoriteItemId = FAVORITE_ITEM_ID

    /**
     * Minimal recording [ApiClient]: the favorite paths run the REAL
     * `UserLibraryApi` over it (mockk can't proxy the final operations
     * classes), with every request answered by a 200 whose body decodes to
     * the DTO under test. Defaults to the all-defaults UserItemDataDto the
     * favorite paths need; tests targeting other endpoints pass their own
     * [responseBody]. [bodiesByPath] scripts per-endpoint bodies (keyed by
     * the SDK's pathTemplate, e.g. "Items/Latest") for flows hitting more
     * than one endpoint in a single client call.
     */
    private class RecordingApiClient(
        private val responseBody: String = """
            {"PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,
            "Played":false,"Key":"k","ItemId":"$FAVORITE_ITEM_ID"}
        """.trimIndent(),
        private val bodiesByPath: Map<String, String> = emptyMap(),
    ) : org.jellyfin.sdk.api.client.ApiClient() {
        val requests = mutableListOf<String>()

        /** The raw query-parameter maps each request carried (for the isMissing param mapping). */
        val queries = mutableListOf<Map<String, Any?>>()

        /** The raw path-parameter maps each request carried (for the DisplayPreferences id). */
        val pathParams = mutableListOf<Map<String, Any?>>()

        /** The raw request bodies (for the DisplayPreferences write DTO). */
        val requestBodies = mutableListOf<Any?>()
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
            // Path params record the WIRE shape (strings) — the typed SDK
            // extensions hand these over as UUIDs/typed values.
            pathParams += pathParameters.mapValues { (_, v) -> v?.toString() }
            requestBodies += requestBody
            val body = bodiesByPath[pathTemplate] ?: responseBody
            return org.jellyfin.sdk.api.client.RawResponse(body.toByteArray(), 200, emptyMap())
        }
    }

    @Test
    fun `SetFavorite seeds the favorite cache ToggleFavorite reads`() = runTest {
        // Both favorite paths must share one cache key: seeding via
        // SetFavorite(true) then toggling with currentIsFavorite = null has
        // to read the seeded flag (unmark → false) instead of re-fetching —
        // a cache miss would fetch userData.isFavorite = false and mark → true.
        val api = RecordingApiClient()
        engine.updateApi(api)

        userDataClient.writeUserData(UserDataWrite.SetFavorite(favoriteItemId, isFavorite = true)).getOrThrow()
        val toggled = userDataClient.writeUserData(UserDataWrite.ToggleFavorite(favoriteItemId)).getOrThrow()

        assertFalse((toggled as UserDataWriteOutcome.FavoriteNow).isFavorite)
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
    fun `the classic-rows resume keeps the pre-12 wire and the classic latest fetches the episode pool`() = runTest {
        // Resume: the wire request is the exact pre-12 shape (no
        // IncludeItemTypes) — the classic Series/Season rollup fold is
        // client-side, so the request is byte-identical across modes and
        // server generations.
        val api = RecordingApiClient(responseBody = emptyItemsBody)
        engine.updateApi(api)
        client.getContinueWatching(limit = 20, classicRows = true).getOrThrow()

        assertEquals(
            "",
            queryParam(api.queries[0], "includeItemTypes"),
            "the classic resume query must stay the pre-12 unconstrained wire shape",
        )

        // Latest (classic TV): the raw-Episode pool — IncludeItemTypes=Episode
        // + groupItems=false is the shape every server generation answers
        // with plain episode rows; the pre-12 grouping then runs client-side.
        // limit rides the wire as the 10.x-equivalent pool (16 × 5).
        // /Users/{userId}/Items/Latest answers a bare JSON array, not an envelope.
        val latestApi = RecordingApiClient(responseBody = "[]")
        engine.updateApi(latestApi)
        client.getLatestMedia(parentId = LATEST_FOLDER_ID, limit = 16, classicEpisodePool = 16 * 5).getOrThrow()

        assertEquals(
            "Episode",
            queryParam(latestApi.queries[0], "includeItemTypes"),
            "the classic latest pool must pin to Episode",
        )
        assertEquals(
            "false",
            queryParam(latestApi.queries[0], "groupItems"),
            "the classic pool must bypass the server grouping route",
        )
        assertEquals(
            "80",
            queryParam(latestApi.queries[0], "limit"),
            "the classic pool fetches the pre-12 5x overfetch",
        )
    }

    @Test
    fun `classic latest groups the episode pool into series cards with the real series items`() = runTest {
        // Pool of three episodes: sA twice (first + third position) and sB
        // once. The 10.x grouping twin must produce [Grouped(sA), Single(e2)]
        // — sA's card takes its FIRST-encounter slot — and resolve sA's card
        // through the batched real-Series fetch, not synthesis.
        val seriesIdA = "8a8a8a8a-1111-4888-8888-999999999999"
        val seriesIdB = "8b8b8b8b-1111-4888-8888-999999999999"
        val episodeJson = { id: String, seriesId: String, seriesName: String ->
            """{"Id":"$id","Name":"ep-$id","Type":"Episode","SeriesId":"$seriesId","SeriesName":"$seriesName"}"""
        }
        val api = RecordingApiClient(
            responseBody = "[]",
            bodiesByPath = mapOf(
                "/Items/Latest" to """
                    [${episodeJson("aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa", seriesIdA, "Show A")},
                     ${episodeJson("bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb", seriesIdB, "Show B")},
                     ${episodeJson("cccccccc-1111-4111-8111-cccccccccccc", seriesIdA, "Show A")}]
                """.trimIndent(),
                "/Items" to """
                    {"TotalRecordCount":1,"StartIndex":0,
                     "Items":[{"Id":"$seriesIdA","Name":"Show A","Type":"Series","ProductionYear":2019}]}
                """.trimIndent(),
            ),
        )
        engine.updateApi(api)

        val rows = client.getLatestMedia(parentId = LATEST_FOLDER_ID, limit = 16, classicEpisodePool = 16 * 5).getOrThrow()

        // Two cards: the sA group (real Series item, childCount = pool size)
        // then sB's single episode card in first-encounter order.
        assertEquals(2, rows.size)
        val grouped = rows[0]
        assertEquals(seriesIdA, grouped.id)
        assertEquals("Show A", grouped.name)
        assertEquals(2019, grouped.year, "the real Series DTO fields ride the card")
        assertEquals(2, grouped.childCount)
        assertEquals("bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb", rows[1].id)

        // Exactly one batched ids fetch, carrying only the grouped series id,
        // with the list projection.
        val itemsCalls = api.requests.withIndex().filter { it.value == "GET /Items" }
        assertEquals(1, itemsCalls.size, "one batched series-resolution call")
        val idsParam = queryParam(api.queries[itemsCalls[0].index], "ids")
        assertEquals(seriesIdA, idsParam)
    }

    @Test
    fun `classic latest degrades to a synthesized series card when the batched fetch fails`() = runTest {
        // The Items envelope answers with garbage: the ids fetch yields no
        // matching series, so the grouped card must fall back to the episode-
        // derived synthesis (series id + name + childCount) instead of
        // failing the row.
        val seriesIdA = "8a8a8a8a-1111-4888-8888-999999999999"
        val api = RecordingApiClient(
            responseBody = "[]",
            bodiesByPath = mapOf(
                "/Items/Latest" to """
                    [{"Id":"aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa","Name":"Pilot","Type":"Episode",
                      "SeriesId":"$seriesIdA","SeriesName":"Show A"},
                     {"Id":"cccccccc-1111-4111-8111-cccccccccccc","Name":"Ep 2","Type":"Episode",
                      "SeriesId":"$seriesIdA","SeriesName":"Show A"}]
                """.trimIndent(),
                "/Items" to """{"TotalRecordCount":0,"StartIndex":0,"Items":[]}""",
            ),
        )
        engine.updateApi(api)

        val rows = client.getLatestMedia(parentId = LATEST_FOLDER_ID, limit = 16, classicEpisodePool = 16 * 5).getOrThrow()

        assertEquals(1, rows.size)
        assertEquals(seriesIdA, rows[0].id)
        assertEquals("Show A", rows[0].name)
        assertEquals(2, rows[0].childCount)
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

    // ── new wrappers: upcoming / theme videos / local trailers / box sets / people / display prefs ──

    @Test
    fun `getUpcoming shapes the Shows Upcoming query with the home-row projection`() = runTest {
        // /Shows/Upcoming rides the shared resume-spec shape (limit + the
        // home-row projection) and scopes to the parent when given (the
        // series strip). The card-overlay reads ride the same fetch.
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[{"Id":"$RESUMABLE_ITEM_ID","Name":"Ep 2","Type":"Episode"}],
                 "TotalRecordCount":1,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val items = client.getUpcoming(parentId = LATEST_FOLDER_ID, limit = 7).getOrThrow()

        assertEquals(listOf("Ep 2"), items.map { it.name })
        assertEquals(listOf("GET /Shows/Upcoming"), api.requests)
        assertEquals("7", queryParam(api.queries[0], "limit"))
        assertEquals(LATEST_FOLDER_ID, queryParam(api.queries[0], "parentId"))
        val fields = queryParam(api.queries[0], "fields")
        assertTrue(fields.contains("MediaStreams"), "quality badges read MediaStreams off the row fetch")
        assertTrue(fields.contains("DateCreated"), "the NEW dot reads DateCreated off the row fetch")
        assertTrue(fields.contains("Trickplay"), "the focus preview reads Trickplay off the row fetch")
    }

    @Test
    fun `getThemeVideos reads the ThemeVideos endpoint and inherits by default`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[{"Id":"$FAVORITE_ITEM_ID","Name":"Theme","Type":"Video"}],
                 "TotalRecordCount":1,"StartIndex":0,"OwnerId":"$LATEST_FOLDER_ID"}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val items = client.getThemeVideos(FAVORITE_ITEM_ID).getOrThrow()

        assertEquals(listOf("Theme"), items.map { it.name })
        assertEquals(listOf("GET /Items/{itemId}/ThemeVideos"), api.requests)
        assertEquals(
            "true",
            queryParam(api.queries[0], "inheritFromParent"),
            "an episode/season card inherits the series clip",
        )
    }

    @Test
    fun `getLocalTrailers reads the bare-array LocalTrailers endpoint`() = runTest {
        val api = RecordingApiClient(
            responseBody = """[{"Id":"$FAVORITE_ITEM_ID","Name":"Trailer","Type":"Trailer"}]""",
        )
        engine.updateApi(api)

        val items = client.getLocalTrailers(FAVORITE_ITEM_ID).getOrThrow()

        assertEquals(listOf("Trailer"), items.map { it.name })
        assertEquals(listOf("GET /Items/{itemId}/LocalTrailers"), api.requests)
        assertEquals(
            testUser.id,
            queryParam(api.queries[0], "userId"),
            "user data attaches like the special-features sibling",
        )
    }

    @Test
    fun `getBoxSetsContaining probes each boxset for membership`() = runTest {
        // On a real server: /Items/{id}/Ancestors never lists BoxSets, so
        // membership resolves explicitly — enumerate the user's boxsets
        // (includeItemTypes=BoxSet), then one id-filtered probe per boxset.
        // Members resolve through getCollectionItems — this stays
        // summaries-only.
        val boxSetId = "9a9a9a9a-1111-4999-8999-999999999999"
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[{"Id":"$boxSetId","Name":"Franchise","Type":"BoxSet","ChildCount":4,"ImageTags":{"Primary":"tag-1"}}],"TotalRecordCount":1,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val sets = client.getBoxSetsContaining(FAVORITE_ITEM_ID).getOrThrow()

        assertEquals(1, sets.size)
        with(sets.single()) {
            assertEquals(boxSetId, id)
            assertEquals("Franchise", name)
            assertEquals(4, itemCount)
            assertEquals("tag-1", imageTag)
        }
        assertEquals(listOf("GET /Items", "GET /Items"), api.requests)
        assertEquals(
            "BoxSet",
            queryParam(api.queries[0], "includeItemTypes"),
            "the enumeration queries the user's boxsets",
        )
        assertEquals(
            FAVORITE_ITEM_ID,
            queryParam(api.queries[1], "ids"),
            "the probe filters the boxset's children to the item",
        )
        assertEquals(
            boxSetId,
            queryParam(api.queries[1], "parentId"),
            "the probe runs under the boxset parent",
        )
    }

    @Test
    fun `getPeople threads appearsInItemId only when asked`() = runTest {
        val api = RecordingApiClient(
            // SDK 1.8.x deserializes the persons rows as BaseItemDto, whose
            // Type field is required — a bare Id/Name row no longer decodes.
            responseBody = """
                {"Items":[{"Id":"$FAVORITE_ITEM_ID","Name":"Jane","Type":"Person"}],"TotalRecordCount":1,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        client.getPeople(searchTerm = "ja", limit = 10).getOrThrow()
        assertEquals("", queryParam(api.queries[0], "appearsInItemId"), "the picker call-site stays unchanged")

        client.getPeople(searchTerm = "ja", limit = 10, appearsInItemId = RESUMABLE_ITEM_ID).getOrThrow()
        assertEquals(RESUMABLE_ITEM_ID, queryParam(api.queries[1], "appearsInItemId"), "the co-star row narrows server-side")
    }

    @Test
    fun `season and all-episodes fetches project MediaStreams`() = runTest {
        val api = RecordingApiClient(responseBody = emptyItemsBody)
        engine.updateApi(api)

        client.getEpisodes(EPISODE_SERIES_ID, EPISODE_SEASON_ID).getOrThrow()
        assertEquals(
            "MediaSources,MediaStreams",
            queryParam(api.queries[0], "fields"),
            "the per-season fetch carries the stream-badge projection — MediaSources is the container the mapper reads",
        )

        client.getAllEpisodes(EPISODE_SERIES_ID).getOrThrow()
        assertEquals(
            "MediaSources,MediaStreams",
            queryParam(api.queries[1], "fields"),
            "the series-detail round-trip carries the projection too — a field-less query mapped every badge to null online",
        )
        assertEquals("", queryParam(api.queries[1], "seasonId"), "season omitted = the single round-trip path")
    }

    @Test
    fun `the resume query carries excludeActiveSessions with the flag`() = runTest {
        val api = RecordingApiClient(responseBody = emptyItemsBody)
        engine.updateApi(api)

        client.getContinueWatching(limit = 20).getOrThrow()
        assertEquals(
            "false",
            queryParam(api.queries[0], "excludeActiveSessions"),
            "default keeps the server default — the legacy wire",
        )

        client.getContinueWatching(limit = 20, excludeActiveSessions = true).getOrThrow()
        assertEquals(
            "true",
            queryParam(api.queries[1], "excludeActiveSessions"),
            "batch and refresh arms send the pref verbatim",
        )
    }

    @Test
    fun `home layout display preferences decode the custom-pref payload`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Id":"${testUser.id}","RememberIndexing":false,"PrimaryImageHeight":0,"PrimaryImageWidth":0,
                 "CustomPrefs":{"jellyplay.homeLayout":"{\"sections\":[\"latest\",\"next_up\"]}",
                                "jellyplay.homeLayout.schemaVersion":"3",
                                "jellyplay.homeLayout.savedAt":"1728600000000"},
                 "ScrollDirection":"Horizontal","ShowBackdrop":false,"RememberSorting":false,
                 "SortOrder":"Ascending","ShowSidebar":false}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val state = homeLayoutClient.readHomeLayoutDisplayPreferences(testUser.id).getOrThrow()

        assertEquals("""{"sections":["latest","next_up"]}""", state.json)
        assertEquals(3, state.schemaVersion)
        assertEquals(1728600000000L, state.savedAtEpochMillis)
        assertEquals(listOf("GET /DisplayPreferences/{displayPreferencesId}"), api.requests)
        assertEquals(testUser.id, queryParam(api.queries[0], "userId"))
        assertEquals(
            "jellyplay",
            queryParam(api.queries[0], "client"),
            "the store is namespaced away from jellyfin-web's own display preferences",
        )
        assertEquals("usersettings", api.pathParams.single()["displayPreferencesId"])
    }

    @Test
    fun `home layout read with an empty store decodes to the all-null state`() = runTest {
        // Never-synced user: no keys — a value (render "nothing on server"),
        // never a failure.
        val api = RecordingApiClient(
            responseBody = """
                {"Id":"${testUser.id}","RememberIndexing":false,"PrimaryImageHeight":0,"PrimaryImageWidth":0,
                 "CustomPrefs":{},"ScrollDirection":"Horizontal","ShowBackdrop":false,"RememberSorting":false,
                 "SortOrder":"Ascending","ShowSidebar":false}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val state = homeLayoutClient.readHomeLayoutDisplayPreferences(testUser.id).getOrThrow()

        assertNull(state.json)
        assertNull(state.schemaVersion)
        assertNull(state.savedAtEpochMillis)
    }

    @Test
    fun `home layout write posts the payload under the jellyplay namespace`() = runTest {
        val api = RecordingApiClient(
            responseBody = "{}",
            bodiesByPath = mapOf("/DisplayPreferences/{displayPreferencesId}" to "{}"),
        )
        engine.updateApi(api)

        homeLayoutClient.writeHomeLayoutDisplayPreferences(
            userId = testUser.id,
            json = """{"sections":[]}""",
            schemaVersion = 3,
        ).getOrThrow()

        assertEquals(listOf("POST /DisplayPreferences/{displayPreferencesId}"), api.requests)
        assertEquals("usersettings", api.pathParams.single()["displayPreferencesId"])
        assertEquals("jellyplay", queryParam(api.queries[0], "client"))
        val dto = api.requestBodies.single() as DisplayPreferencesDto
        assertEquals("""{"sections":[]}""", dto.customPrefs["jellyplay.homeLayout"])
        assertEquals("3", dto.customPrefs["jellyplay.homeLayout.schemaVersion"])
        assertTrue(
            dto.customPrefs["jellyplay.homeLayout.savedAt"]?.toLongOrNull() != null,
            "the write stamps a wall-clock marker for the sync dialog",
        )
        assertEquals("jellyplay", dto.client)
    }

    // ── the family adapters (playlist/collection split + the home cache port) ──

    @Test
    fun `createPlaylist posts the SDK dto over the same engine`() = runTest {
        // The playlist family moved to PlaylistApiClientImpl (its own adapter
        // over the SAME engine single): the wire must not have moved with it —
        // POST /Playlists with the CreatePlaylistDto carrying the current
        // user, the item ids and the default AUDIO media type.
        val api = RecordingApiClient(responseBody = """{"Id":"$FAVORITE_ITEM_ID"}""")
        engine.updateApi(api)

        val created = playlistClient.createPlaylist(name = "Mix", itemIds = listOf(FAVORITE_ITEM_ID)).getOrThrow()

        assertEquals(FAVORITE_ITEM_ID, created)
        assertEquals(listOf("POST /Playlists"), api.requests)
        val dto = api.requestBodies.single() as CreatePlaylistDto
        assertEquals("Mix", dto.name)
        assertEquals(listOf(FAVORITE_ITEM_ID), dto.ids?.map { it.toString() })
        assertEquals(testUser.id, dto.userId?.toString())
        assertEquals(SdkMediaType.AUDIO, dto.mediaType)
    }

    @Test
    fun `deletePlaylist rides the raw Items DELETE path`() = runTest {
        // The one hand-rolled SDK request in the playlist family — the raw
        // DELETE /Items/{id} — must survive the move to the adapter
        // byte-identically.
        val api = RecordingApiClient()
        engine.updateApi(api)

        playlistClient.deletePlaylist(FAVORITE_ITEM_ID).getOrThrow()

        assertEquals(listOf("DELETE Items/$FAVORITE_ITEM_ID"), api.requests)
    }

    @Test
    fun `addItemsToPlaylist carries the current user with the entry ids`() = runTest {
        val api = RecordingApiClient()
        engine.updateApi(api)

        playlistClient
            .addItemsToPlaylist(playlistId = FAVORITE_ITEM_ID, itemIds = listOf(RESUMABLE_ITEM_ID))
            .getOrThrow()

        assertEquals(listOf("POST /Playlists/{playlistId}/Items"), api.requests)
        assertEquals(RESUMABLE_ITEM_ID, queryParam(api.queries[0], "ids"))
        assertEquals(
            testUser.id,
            queryParam(api.queries[0], "userId"),
            "the server-side reorder/edit ACL keys on the calling user",
        )
    }

    @Test
    fun `the collection adapter reads members and creates over the same engine`() = runTest {
        // The collection family moved to CollectionApiClientImpl: members page
        // under the collection parent with the SearchResult envelope, and
        // creation POSTs /Collections and echoes the new id.
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[{"Id":"$RESUMABLE_ITEM_ID","Name":"Member","Type":"Movie"}],
                 "TotalRecordCount":1,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val members = collectionClient.getCollectionItems(EPISODE_SERIES_ID, startIndex = 20, limit = 10).getOrThrow()

        assertEquals(listOf("Member"), members.items.map { it.name })
        assertEquals(1, members.totalRecordCount)
        assertEquals(20, members.startIndex)
        assertEquals("GET /Items", api.requests.single())
        assertEquals(EPISODE_SERIES_ID, queryParam(api.queries[0], "parentId"))
        assertEquals("20", queryParam(api.queries[0], "startIndex"))

        val createApi = RecordingApiClient(responseBody = """{"Id":"$LATEST_FOLDER_ID"}""")
        engine.updateApi(createApi)
        assertEquals(
            LATEST_FOLDER_ID,
            collectionClient.createCollection(name = "Canon", itemIds = listOf(FAVORITE_ITEM_ID)).getOrThrow(),
        )
        assertEquals(listOf("POST /Collections"), createApi.requests)
        assertEquals("Canon", queryParam(createApi.queries[0], "name"))
        assertEquals(FAVORITE_ITEM_ID, queryParam(createApi.queries[0], "ids"))
    }

    @Test
    fun `the home cache port rides the impl-owned adapter over the same fetcher`() {
        // The port's production adapter ([HomeSectionsCachePortAdapter]) is
        // constructed ON the impl — over its own fetcher — and re-exposed
        // internal; the Koin construction owner binds the port to this
        // property. The best-effort verbs are documented no-ops before any
        // home fetch has memoised, so the drop runs for real here.
        val port: HomeSectionsCachePort = client.homeSectionsCachePort
        assertTrue(port is HomeSectionsCachePortAdapter)
        port.invalidateSubcallCaches()
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
