package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [MediaInfoApiClientImpl]'s playback-reporting plugin seam through the
 * [JellyfinRawRequester] (the PluginApiClientImplTest harness shape): the
 * shared BreakdownReport fetch/decode fold behind both breakdown endpoints
 * (the plugin's `label|name` / `total|count|value` field folds), the plugin
 * failure text, the user-id-vs-media-type filter-token heuristic, and the
 * stale-item projection fold shared by getStaleItems' played/unplayed
 * branches.
 */
class MediaInfoApiClientImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var engine: JellyfinApiEngine
    private lateinit var client: MediaInfoApiClientImpl

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        engine.updateServer(
            ServerInfo(id = "server-1", name = "Test", address = mockWebServer.url("/").toString().trimEnd('/')),
        )
        engine.updateUser(UserInfo(id = "user-1", name = "testuser", serverAddress = "", accessToken = "token-123"))
        client = MediaInfoApiClientImpl(engine)
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getPlaybackReportingBreakdown decodes the plugin payload with the field folds`() = runTest {
        // Recorded plugin shapes: label|name naming and total|count|value
        // naming both occur depending on report type; a row can carry neither.
        mockWebServer.enqueue(
            MockResponse().setBody(
                """[{"label":"Movies","total":"12"},{"name":"Episodes","count":"3"},""" +
                    """{"label":"Audio","value":"7"},{"label":"Empty"}]""",
            ),
        )

        val breakdown = client.getPlaybackReportingBreakdown("Type", days = 7, filter = null).getOrThrow()

        assertEquals(
            listOf(
                ContentBreakdown(label = "Movies", value = 12, colorIndex = 0),
                ContentBreakdown(label = "Episodes", value = 3, colorIndex = 1),
                ContentBreakdown(label = "Audio", value = 7, colorIndex = 2),
                ContentBreakdown(label = "Empty", value = 0, colorIndex = 3),
            ),
            breakdown,
        )
        assertEquals("/user_usage_stats/Type/BreakdownReport?days=7", mockWebServer.takeRequest().path)
    }

    @Test
    fun `getPlaybackReportingArtistBreakdown targets the Parent report with the filter param`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("""[{"label":"Artists","total":"4"}]"""))

        val breakdown = client.getPlaybackReportingArtistBreakdown(days = 30, filter = "MusicAlbum").getOrThrow()

        assertEquals(listOf(ContentBreakdown(label = "Artists", value = 4, colorIndex = 0)), breakdown)
        assertEquals(
            "/user_usage_stats/Parent/BreakdownReport?days=30&filter=MusicAlbum",
            mockWebServer.takeRequest().path,
        )
    }

    @Test
    fun `breakdown decoder accepts numeric and string totals`() {
        assertEquals(
            listOf(
                ContentBreakdown(label = "Movies", value = 12, colorIndex = 0),
                ContentBreakdown(label = "Music", value = 3, colorIndex = 1),
            ),
            parseBreakdownReport("""[{"label":"Movies","total":12},{"label":"Music","total":"3"}]"""),
        )
    }

    @Test
    fun `breakdown failure keeps the plugin failure text`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = client.getPlaybackReportingBreakdown("Type", days = 7, filter = null)

        assertTrue(result.isFailure)
        assertEquals("Plugin request failed: 500", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `user-id token heuristic routes dashed and bare-hex UUIDs only`() {
        // Dashed UUID (36 chars) — the canonical user-id form.
        assertTrue(isPlaybackReportingUserIdToken("1f2e3d4c-5b6a-7788-99aa-bbccddeeff00"))
        // Bare-hex UUID (32 chars) — the plugin also emits the dashed form
        // stripped.
        assertTrue(isPlaybackReportingUserIdToken("1f2e3d4c5b6a778899aabbccddeeff00"))
        // 32 chars with a dash anywhere still reads as an id.
        assertTrue(isPlaybackReportingUserIdToken("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-"))

        // Length gate: below 32 and above 36 are media-type-ish tokens.
        assertFalse(isPlaybackReportingUserIdToken("1f2e3d4c5b6a778899aabbccddeeff0"))
        assertFalse(isPlaybackReportingUserIdToken("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        // 40-char hex (SHA-1 shape) is not a user id.
        assertFalse(isPlaybackReportingUserIdToken("1f2e3d4c5b6a778899aabbccddeeff00abcd1234"))
        // Punctuation (other than the dash) breaks the alnum branch.
        assertFalse(isPlaybackReportingUserIdToken("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!aaa"))
        // Plain media-type names never qualify, even dash-containing ones.
        assertFalse(isPlaybackReportingUserIdToken("Movie"))
        assertFalse(isPlaybackReportingUserIdToken("MusicVideo-Extra"))
    }

    @Test
    fun `stale projection keeps shared item fields and branch-specific watch state`() {
        val userData = UserItemDataDto(
            playbackPositionTicks = 0L,
            playCount = 5,
            isFavorite = false,
            played = true,
            key = "key",
            itemId = UUID.randomUUID(),
            lastPlayedDate = LocalDateTime.parse("2026-01-01T12:00:00"),
        )
        val dto = BaseItemDto(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            type = BaseItemKind.EPISODE,
            name = "Pilot",
            mediaType = MediaType.VIDEO,
            parentId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            seriesName = "Show",
            seasonName = "Season 1",
            parentIndexNumber = 1,
            indexNumber = 2,
            imageBlurHashes = mapOf(ImageType.PRIMARY to mapOf("Primary" to "blurhash")),
            premiereDate = LocalDateTime.parse("2020-05-06T00:00:00"),
            overview = "An episode",
            productionYear = 2020,
            dateCreated = LocalDateTime.parse("2026-02-03T00:00:00"),
            userData = userData,
        )

        // Played branch: watch state from userData.
        val played = dto.toStaleMediaItem(
            lastPlayedDate = "2026-01-01T12:00:00",
            daysSincePlay = 40,
            playCount = 5,
        )
        assertEquals("00000000-0000-0000-0000-000000000001", played.itemId)
        assertEquals("Pilot", played.name)
        assertEquals("Episode", played.type)
        assertEquals("Video", played.mediaType)
        assertEquals("2026-01-01T12:00:00", played.lastPlayedDate)
        assertEquals(40, played.daysSincePlay)
        assertEquals(5, played.playCount)
        assertEquals(0, played.sizeBytes)
        assertEquals("", played.sizeText)
        assertEquals("00000000-0000-0000-0000-000000000002", played.parentId)
        assertEquals("Show", played.seriesName)
        assertEquals("Season 1", played.seasonName)
        assertEquals(1, played.seasonNumber)
        assertEquals(2, played.episodeNumber)
        assertEquals("blurhash", played.posterBlurHash)
        assertEquals("2020-05-06T00:00", played.premiereDate)
        assertEquals("An episode", played.overview)
        assertEquals(2020, played.year)
        assertEquals("2026-02-03T00:00", played.dateAdded)

        // Unplayed branch: zeroed watch state, identical projection otherwise.
        val unplayed = dto.toStaleMediaItem(
            lastPlayedDate = null,
            daysSincePlay = 100,
            playCount = 0,
        )
        assertNull(unplayed.lastPlayedDate)
        assertEquals(100, unplayed.daysSincePlay)
        assertEquals(0, unplayed.playCount)
        assertEquals(played.itemId, unplayed.itemId)
        assertEquals("blurhash", unplayed.posterBlurHash)
        assertEquals("2026-02-03T00:00", unplayed.dateAdded)
    }
}
