package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
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
import kotlin.test.assertTrue

/**
 * Pins the hand-mapped playlist / collection / tag endpoints of
 * [LibraryApiClientImpl] (the LibraryApiClientImplTest harness shape: real
 * [JellyfinApiEngine] over a minimal recording [org.jellyfin.sdk.api.client.ApiClient]
 * — mockk can't proxy the final SDK operations classes): both the REQUEST
 * shape (path + the query vocabulary the SDK serializes) and the WIRE → model
 * mapping of the hand-built rows, so an SDK DTO rename or a mapper edit shows
 * up as a diff here instead of a silent feature break.
 */
class LibraryApiHandMappersTest {

    private lateinit var client: LibraryApiClientImpl
    private lateinit var engine: JellyfinApiEngine

    @BeforeTest
    fun setup() {
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        engine.updateServer(ServerInfo(id = "server-1", name = "Test Server", address = "https://test.example.com"))
        engine.updateUser(TEST_USER)
        client = LibraryApiClientImpl(engine, mockk(relaxed = true), SystemTimeSource())
    }

    /** Minimal recording ApiClient — see [LibraryApiClientImplTest.RecordingApiClient] for the full contract. */
    private class RecordingApiClient(
        private val responseBody: String,
    ) : org.jellyfin.sdk.api.client.ApiClient() {
        val requests = mutableListOf<String>()
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

    // ── playlists (getPlaylists / getPlaylistItems) ─────────────────────────

    @Test
    fun `getPlaylists queries PLAYLIST items and maps the hand-built row`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[
                    {"Id":"77777777-7777-4777-8777-777777777777","Name":"Road trip",
                     "Type":"Playlist","Overview":"Mix","ChildCount":12,
                     "ImageTags":{"Primary":"pltag"},
                     "CanDelete":false,"DateCreated":"2026-01-02T03:04:05.000Z"}
                ],"TotalRecordCount":1,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val playlists = client.getPlaylists(limit = 25).getOrThrow()

        assertEquals("GET /Items", api.requests.single())
        val query = api.queries.single()
        assertEquals("Playlist", queryParam(query, "includeItemTypes"))
        assertEquals("25", queryParam(query, "limit"))
        assertEquals("true", queryParam(query, "recursive"))
        // The hand-built row: id/name/overview verbatim, child count → itemCount,
        // the Primary image tag, the CURRENT user stamped on, the server's
        // CanDelete honored, and the date string carried as-is.
        val playlist = playlists.single()
        assertEquals("77777777-7777-4777-8777-777777777777", playlist.id)
        assertEquals("Road trip", playlist.name)
        assertEquals("Mix", playlist.overview)
        assertEquals(12, playlist.itemCount)
        assertEquals("pltag", playlist.imageTag)
        assertEquals(TEST_USER.id, playlist.userId)
        assertEquals(false, playlist.canDelete)
        // The date carries through as a string; its zone rendering is the
        // SDK datetime type's business — pin only the calendar day.
        assertEquals(true, playlist.createdAt?.startsWith("2026-01-02") == true)
    }

    @Test
    fun `getPlaylistItems maps entry ids artist fold and media type`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[
                    {"Id":"88888888-8888-4888-8888-888888888888","Name":"Song A",
                     "Type":"Audio","PlaylistItemId":"e1","Album":"LP",
                     "AlbumArtist":"The Band","RunTimeTicks":23450000},
                    {"Id":"99999999-9999-4999-8999-999999999999","Name":"Song B",
                     "Type":"Episode","PlaylistItemId":"e2"}
                ],"TotalRecordCount":2,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val items = client.getPlaylistItems(PLAYLIST_ID, startIndex = 10, limit = 50).getOrThrow()

        assertEquals("GET /Items", api.requests.single())
        val query = api.queries.single()
        assertEquals(PLAYLIST_ID, queryParam(query, "parentId"))
        assertEquals("10", queryParam(query, "startIndex"))
        // Row 1: the albumArtist FIRST fold (artistItems fallback untested here —
        // absent on this row), album, wire kind → media type, runtime ticks.
        val first = items[0]
        assertEquals("88888888-8888-4888-8888-888888888888", first.id)
        assertEquals("e1", first.playlistItemId)
        assertEquals("The Band", first.artist)
        assertEquals("LP", first.album)
        assertEquals(MediaType.AUDIO, first.mediaType)
        assertEquals(23450000L, first.runTimeTicks)
        // Row 2: an Episode entry maps its wire kind; no artist/album/runtime.
        val second = items[1]
        assertEquals(MediaType.EPISODE, second.mediaType)
        assertEquals(null, second.artist)
        assertEquals(null, second.runTimeTicks)
    }

    // ── collections (getCollections) ────────────────────────────────────────

    @Test
    fun `getCollections queries BOX_SET with child count and maps summaries`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[
                    {"Id":"aaaaaaa1-aaaa-4aaa-8aaa-aaaaaaaaaaa1","Name":"Sci-Fi",
                     "Type":"BoxSet","ChildCount":7,"ImageTags":{"Primary":"ctag"}},
                    {"Id":"aaaaaaa2-aaaa-4aaa-8aaa-aaaaaaaaaaa2","Name":"Empty","Type":"BoxSet"}
                ],"TotalRecordCount":2,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val collections = client.getCollections(limit = 100).getOrThrow()

        assertEquals("GET /Items", api.requests.single())
        val query = api.queries.single()
        assertEquals("BoxSet", queryParam(query, "includeItemTypes"))
        assertEquals("ChildCount", queryParam(query, "fields"))
        // Summary rows: id/name verbatim, ChildCount (absent → 0), Primary tag
        // (absent → null — the picker's "no art" fallback).
        assertEquals(
            listOf(
                "aaaaaaa1-aaaa-4aaa-8aaa-aaaaaaaaaaa1" to "Sci-Fi",
                "aaaaaaa2-aaaa-4aaa-8aaa-aaaaaaaaaaa2" to "Empty",
            ),
            collections.map { it.id to it.name },
        )
        assertEquals(7, collections[0].itemCount)
        assertEquals("ctag", collections[0].imageTag)
        assertEquals(0, collections[1].itemCount)
        assertEquals(null, collections[1].imageTag)
    }

    // ── tags ────────────────────────────────────────────────────────────────

    @Test
    fun `getTags flattens dedups and sorts across rows`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[
                    {"Id":"bbbbbbb1-bbbb-4bbb-8bbb-bbbbbbbbbbb1","Name":"A","Type":"Movie","Tags":["zeta","alpha"]},
                    {"Id":"bbbbbbb2-bbbb-4bbb-8bbb-bbbbbbbbbbb2","Name":"B","Type":"Movie","Tags":["Alpha","mid"]},
                    {"Id":"bbbbbbb3-bbbb-4bbb-8bbb-bbbbbbbbbbb3","Name":"C","Type":"Movie"}
                ],"TotalRecordCount":3,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        val tags = client.getTags(startIndex = 0, limit = 100).getOrThrow()

        assertEquals("GET /Items", api.requests.single())
        assertEquals("Tags", queryParam(api.queries.single(), "fields"))
        assertEquals("true", queryParam(api.queries.single(), "recursive"))
        // Case-sensitive distinct (the mapper does NOT fold case) + sorted.
        assertEquals(listOf("Alpha", "alpha", "mid", "zeta"), tags)
    }

    // ── SDK-drift guard over the requireNotNull enum-resolution block ───────

    /**
     * The module-level projections at the top of LibraryApiClientImpl.kt
     * (`DETAIL_ITEM_FIELDS`, `SEARCH_SUGGESTIONS_*`) resolve the commonMain
     * wire projections against the SDK enums with `requireNotNull`/`error`
     * — an SDK rename fails FAST at first access with the offending token in
     * the message. These tests force that access through real reads, so SDK
     * drift cannot surface as a production-only crash; the pins also check
     * the resolved enums actually reached the query (a projection silently
     * resolving empty would otherwise pass a fail-fast-only guard). The
     * resolved enums arrive as raw query values, stringified case-insensitively.
     */
    private fun queryParam(map: Map<String, Any?>, name: String): String = when (val v = map[name]) {
        is Collection<*> -> v.joinToString(",") { it.toString() }
        null -> ""
        else -> v.toString()
    }

    @Test
    fun `detail projection resolves against the SDK ItemFields enum and rides the query`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[],"TotalRecordCount":0,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        client.getMediaDetail("dddddddd-dddd-4ddd-8ddd-dddddddddddd")

        // The projected query is the FIRST request (the getItem fallback rides second).
        val fields = queryParam(api.queries.first(), "fields")
        assertTrue(fields.isNotEmpty(), "detail projection resolved to nothing — SDK drift")
        assertTrue(
            fields.contains("Path", ignoreCase = true) && fields.contains("ProviderIds", ignoreCase = true),
            "detail projection lost its load-bearing fields (got $fields) — SDK or projection drift",
        )
    }

    @Test
    fun `search-suggestions projections resolve and ride the query`() = runTest {
        val api = RecordingApiClient(
            responseBody = """
                {"Items":[],"TotalRecordCount":0,"StartIndex":0}
            """.trimIndent(),
        )
        engine.updateApi(api)

        client.getSearchSuggestions(limit = 20)

        val query = api.queries.single()
        val sortBy = queryParam(query, "sortBy")
        val kinds = queryParam(query, "includeItemTypes")
        val fields = queryParam(query, "fields")
        assertTrue(sortBy.contains("IsFavoriteOrLiked", ignoreCase = true), "sort projection drifted (got $sortBy)")
        assertTrue(
            kinds.contains("Movie", ignoreCase = true) && kinds.contains("Series", ignoreCase = true),
            "kind projection drifted (got $kinds)",
        )
        assertTrue(fields.contains("PrimaryImageAspectRatio", ignoreCase = true), "field projection drifted (got $fields)")
    }

    private companion object {
        /** The SDK uuid-parses the parentId — a wire-shaped id, not "pl-1". */
        const val PLAYLIST_ID = "55555555-5555-4555-8555-555555555555"

        val TEST_USER = UserInfo(
            id = "11111111-1111-4111-8111-111111111111",
            name = "testuser",
            serverAddress = "https://test.example.com",
            accessToken = "token-123",
            serverId = "server-1",
        )
    }
}
