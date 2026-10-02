package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.IdentifyItemType
import com.raulshma.jellyplay.core.model.IdentifyQuery
import com.raulshma.jellyplay.core.model.IdentifyResult
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the Identify endpoints (jellyfin-web parity) onto the recording
 * ApiClient (real engine + real SDK operations over a recorded transport):
 * the type-specific remote-search dispatch (Series / Movie), the query body
 * shaping (name / year / providerIds), and the apply round-trip (path +
 * replaceAllImages flag + the server-original DTO posted back verbatim).
 */
class MetadataApiClientIdentifyTest {

    private lateinit var engine: JellyfinApiEngine
    private lateinit var client: RecordingApiClient
    private lateinit var api: MetadataApiClientImpl

    @BeforeTest
    fun setup() {
        client = RecordingApiClient()
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = com.raulshma.jellyplay.core.network.failover.ServerAddressRouter(),
        )
        engine.updateApi(client)
        api = MetadataApiClientImpl(engine)
    }

    private class RecordingApiClient : org.jellyfin.sdk.api.client.ApiClient() {
        var nextBody: String = "[]"
        val requests = mutableListOf<RecordedRequest>()
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
            requests += RecordedRequest(method.name, pathTemplate, pathParameters, queryParameters, requestBody)
            return org.jellyfin.sdk.api.client.RawResponse(nextBody.toByteArray(), 200, emptyMap())
        }
    }

    private data class RecordedRequest(
        val method: String,
        val pathTemplate: String,
        val pathParameters: Map<String, Any?>,
        val queryParameters: Map<String, Any?>,
        val requestBody: Any?,
    )

    @Test
    fun `series search posts to the Series endpoint with the query body`() = runTest {
        client.nextBody = """
            [{"Name":"The Real Show","ProductionYear":2020,
              "ProviderIds":{"tvdb":"121361"},"SearchProviderName":"TheTVDB",
              "ImageUrl":"https://img/t.jpg"}]
        """.trimIndent()

        val results = api.identifyRemoteSearch(
            IdentifyQuery(
                itemId = "00000000-0000-0000-0000-000000000001",
                itemType = IdentifyItemType.SERIES,
                name = "The Real Show",
                year = 2020,
                providerIds = mapOf("tvdb" to "121361"),
            ),
        ).getOrThrow()

        assertEquals(1, results.size)
        assertEquals("The Real Show", results[0].name)
        assertEquals(2020, results[0].year)
        assertEquals("121361", results[0].providerIds["tvdb"])
        assertEquals("https://img/t.jpg", results[0].imageUrl)

        val request = client.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/Items/RemoteSearch/Series", request.pathTemplate)
        val body = request.requestBody as org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
        assertEquals("The Real Show", body.searchInfo?.name)
        assertEquals(2020, body.searchInfo?.year)
        assertEquals("121361", body.searchInfo?.providerIds?.get("tvdb"))
        assertEquals("00000000-0000-0000-0000-000000000001", body.itemId.toString())
    }

    @Test
    fun `movie search posts to the Movie endpoint`() = runTest {
        client.nextBody = "[]"

        val results = api.identifyRemoteSearch(
            IdentifyQuery(
                itemId = "00000000-0000-0000-0000-000000000002",
                itemType = IdentifyItemType.MOVIE,
                name = "A Film",
                year = null,
                providerIds = emptyMap(),
            ),
        ).getOrThrow()

        assertTrue(results.isEmpty())
        assertEquals("/Items/RemoteSearch/Movie", client.requests.single().pathTemplate)
    }

    @Test
    fun `apply posts the selected result to the Apply endpoint`() = runTest {
        val applied = api.applyIdentifyResult(
            itemId = "00000000-0000-0000-0000-000000000004",
            result = IdentifyResult(
                name = "The Real Show",
                year = 2020,
                providerIds = mapOf("tvdb" to "121361"),
                imageUrl = "https://img/t.jpg",
            ),
            replaceAllImages = true,
        )

        assertTrue(applied.isSuccess)
        val request = client.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/Items/RemoteSearch/Apply/{itemId}", request.pathTemplate)
        assertEquals(
            "00000000-0000-0000-0000-000000000004",
            request.pathParameters["itemId"].toString(),
        )
        assertEquals(true, request.queryParameters["replaceAllImages"])
        val body = request.requestBody as org.jellyfin.sdk.model.api.RemoteSearchResult
        assertEquals("The Real Show", body.name)
        assertEquals("121361", body.providerIds?.get("tvdb"))
    }

    @Test
    fun `apply posts the server original result dto verbatim when the candidate came from a search`() = runTest {
        // Provider-id key arrives mixed-case from the wire; the model's
        // lowercase convention is display-only and must NOT fold back into
        // the applied payload (jellyfin-web posts the original object).
        client.nextBody = """
            [{"Name":"The Real Show","ProductionYear":2020,
              "ProviderIds":{"Tvdb":"121361"},"SearchProviderName":"TheTVDB"}]
        """.trimIndent()
        val results = api.identifyRemoteSearch(
            IdentifyQuery(
                itemId = "00000000-0000-0000-0000-000000000005",
                itemType = IdentifyItemType.SERIES,
                name = "The Real Show",
            ),
        ).getOrThrow()

        api.applyIdentifyResult(
            itemId = "00000000-0000-0000-0000-000000000005",
            result = results.single(),
            replaceAllImages = false,
        )

        val applyBody = client.requests.last().requestBody as org.jellyfin.sdk.model.api.RemoteSearchResult
        assertEquals("TheTVDB", applyBody.searchProviderName)
        assertEquals("121361", applyBody.providerIds?.get("Tvdb"))
        assertEquals(null, applyBody.providerIds?.get("tvdb"), "the original key case must survive, not be re-lowercased")
    }
}
