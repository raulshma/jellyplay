package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import com.raulshma.jellyplay.core.network.library.LibraryItemsQuerySpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [LibraryItemsQuerySpec] seam's transport half
 * ([getItemsQuery] / [getItemsPage] / [toSearchResult] — the call site the
 * JVM twins' /Items expansions collapsed into): the full spec→SDK expansion
 * lands on the wire arg-for-arg, the discover row's presence-chip opt-out
 * holds, and the page fold applies the parental filter while carrying the
 * server's totalRecordCount and the echoed startIndex.
 *
 * Setup mirrors [LibraryApiClientImplTest]: a real [JellyfinApiEngine] (mocked
 * Jellyfin SDK instance, real OkHttp) over a minimal recording ApiClient, the
 * executor invoked through [JellyfinApiEngine.withApi] exactly the way the
 * client methods invoke it.
 */
class LibraryItemsQueryExecutorsTest {

    private lateinit var engine: JellyfinApiEngine

    private val testServer = ServerInfo(
        id = "server-1",
        name = "Test Server",
        address = "https://test.example.com",
    )

    /** A rated user so the fold's parental filter has a real ceiling to enforce. */
    private val testUser = UserInfo(
        id = "11111111-1111-4111-8111-111111111111",
        name = "testuser",
        serverAddress = "https://test.example.com",
        accessToken = "token-123",
        serverId = "server-1",
        maxParentalAgeRating = 13,
    )

    @BeforeTest
    fun setup() {
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        // Permanently-null activeAddress so the engine's rebuild collector
        // never fires — see LibraryApiClientImplTest's setup note.
        val router = mockk<ServerAddressRouter>(relaxed = true)
        every { router.activeAddress } returns MutableStateFlow(null)
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { jellyfin },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = router,
        )
        engine.updateServer(testServer)
        engine.updateUser(testUser)
    }

    /**
     * Minimal recording [ApiClient] — the same shape LibraryApiClientImplTest
     * uses (mockk can't proxy the final operations classes): every request is
     * answered by a 200 whose body decodes to the DTOs under test, and the
     * raw query-parameter maps are kept for the wire assertions.
     */
    private class RecordingApiClient(
        private val responseBody: String,
    ) : org.jellyfin.sdk.api.client.ApiClient() {
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
            queries += queryParameters
            return org.jellyfin.sdk.api.client.RawResponse(responseBody.toByteArray(), 200, emptyMap())
        }
    }

    /** Normalizes an SDK collection query param to a comma string for assertions. */
    private fun queryParam(map: Map<String, Any?>, name: String): String = when (val v = map[name]) {
        is Collection<*> -> v.joinToString(",") { it.toString() }
        null -> ""
        else -> v.toString()
    }

    /**
     * A representative spec exercising EVERY dimension the two full expansions
     * (library grid + discover row) used to restate — kinds include+exclude,
     * name filters, ids, sort tokens + order, paging, term, ItemFilters,
     * presence chips, rating floor, projection, people and both date windows.
     */
    private fun fullSpec() = LibraryItemsQuerySpec(
        parentId = PARENT_ID,
        includeKinds = listOf("Movie"),
        excludeKinds = listOf("Season"),
        genres = listOf("Drama"),
        years = listOf(2019),
        studioIds = listOf(STUDIO_ID),
        tags = listOf("neo-noir"),
        sortBy = listOf("ProductionYear", "SortName"),
        sortOrderDescending = true,
        startIndex = 40,
        limit = 20,
        searchTerm = "dune",
        itemFilters = listOf("IsUnplayed", "IsResumable"),
        hasSubtitles = true,
        hasTrailer = true,
        minCommunityRating = 6.5,
        fields = listOf("Genres"),
        personIds = listOf(PERSON_ID),
        minDateLastSavedMs = 1_700_000_000_000,
        minPremiereDateMs = 1_600_000_000_000,
    )

    /** One row inside the rating ceiling, one above — the fold must split them. */
    private val ratedPageBody = """
        {"Items":[
            {"Id":"4c4c4c4c-1111-4444-8444-555555555555","Name":"Safe One","Type":"Movie","OfficialRating":"PG-13"},
            {"Id":"3b3b3b3b-1111-4333-8333-444444444444","Name":"Locked Out","Type":"Movie","OfficialRating":"NC-17"}
        ],"TotalRecordCount":57,"StartIndex":40}
    """.trimIndent()

    @Test
    fun `getItemsPage expands every spec dimension onto the wire and folds the page`() = runTest {
        val api = RecordingApiClient(ratedPageBody)
        engine.updateApi(api)

        val page = engine.withApi { client ->
            client.getItemsPage(fullSpec(), engine.currentMaxParentalRating)
        }.getOrThrow()

        // ── the wire: the expansion, param for param ──
        val q = api.queries.single()
        assertEquals(PARENT_ID, queryParam(q, "parentId"), "parentId resolves through toUUID()")
        assertEquals("Movie", queryParam(q, "includeItemTypes"))
        assertEquals("Season", queryParam(q, "excludeItemTypes"))
        assertEquals("Drama", queryParam(q, "genres"))
        assertEquals("2019", queryParam(q, "years"))
        assertEquals(STUDIO_ID, queryParam(q, "studioIds"), "studioIds resolve through toUUID()")
        assertEquals("neo-noir", queryParam(q, "tags"))
        assertEquals("ProductionYear,SortName", queryParam(q, "sortBy"), "sort tokens resolve to their serial names")
        assertEquals("Descending", queryParam(q, "sortOrder"), "sortOrderDescending=true is the Descending list")
        assertEquals("40", queryParam(q, "startIndex"))
        assertEquals("20", queryParam(q, "limit"))
        assertEquals("true", queryParam(q, "recursive"))
        assertEquals("dune", queryParam(q, "searchTerm"))
        assertEquals("IsUnplayed,IsResumable", queryParam(q, "filters"), "ItemFilter serial names resolve in order")
        assertEquals("true", queryParam(q, "hasSubtitles"), "the library-grid wire carries the presence chips")
        assertEquals("true", queryParam(q, "hasTrailer"))
        assertEquals("6.5", queryParam(q, "minCommunityRating"))
        assertEquals(PERSON_ID, queryParam(q, "personIds"), "personIds resolve through toUUID()")
        assertEquals(
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(1_700_000_000_000), java.time.ZoneId.systemDefault()).toString(),
            queryParam(q, "minDateLastSaved"),
            "the epoch-millis window resolves to the SDK's LocalDateTime in the default zone",
        )
        assertEquals(
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(1_600_000_000_000), java.time.ZoneId.systemDefault()).toString(),
            queryParam(q, "minPremiereDate"),
        )
        assertEquals("Genres", queryParam(q, "fields"))

        // ── the fold: parental filter applied, server totals carried ──
        assertEquals(listOf("Safe One"), page.items.map { it.name }, "the NC-17 row is filtered at the user's ceiling")
        assertEquals(57, page.totalRecordCount, "the server's totalRecordCount rides through untouched")
        assertEquals(40, page.startIndex, "the page echoes the spec's startIndex")
    }

    @Test
    fun `the discover-style call drops the presence chips the row wire never sent`() = runTest {
        // The discover-row call opts out (sendPresenceParams = false): a row's
        // LibraryFilters CAN carry the chips (the shared persisted blob), but
        // the discover wire has never sent them — the opt-out must keep them
        // off the wire while every other dimension still rides. The spec's
        // searchTerm stays null here because the discover builder pins it
        // null; this assertion doubles as the pin for that invariant.
        val api = RecordingApiClient(ratedPageBody)
        engine.updateApi(api)

        val spec = fullSpec().copy(searchTerm = null)
        val page = engine.withApi { client ->
            client.getItemsPage(spec, engine.currentMaxParentalRating, sendPresenceParams = false)
        }.getOrThrow()

        val q = api.queries.single()
        assertEquals("", queryParam(q, "hasSubtitles"), "the opted-out chips must not reach the wire")
        assertEquals("", queryParam(q, "hasTrailer"))
        assertEquals("", queryParam(q, "searchTerm"), "the discover builder pins the term null")
        // The rest of the expansion is unaffected by the opt-out.
        assertEquals("Movie", queryParam(q, "includeItemTypes"))
        assertEquals(PERSON_ID, queryParam(q, "personIds"))
        assertEquals(listOf("Safe One"), page.items.map { it.name })
    }

    private companion object {
        /** Real UUIDs: the id-carrying dimensions resolve through String.toUUID(). */
        const val PARENT_ID = "7f7f7f7f-1111-4777-8777-888888888888"
        const val STUDIO_ID = "9a9a9a9a-1111-4999-8999-999999999999"
        const val PERSON_ID = "2b2b2b2b-1111-4222-8222-333333333333"
    }
}
