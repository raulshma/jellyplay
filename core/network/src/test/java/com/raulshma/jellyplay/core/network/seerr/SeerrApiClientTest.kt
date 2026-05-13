package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrSettings
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeerrApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: SeerrApiClientImpl

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        apiClient = SeerrApiClientImpl(OkHttpClient())
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getRadarrSettings parses response correctly`() = runBlocking {
        val jsonResponse = """
            [
                {
                    "id": 1,
                    "name": "Radarr Main",
                    "hostname": "192.168.1.10",
                    "port": 7878,
                    "apiKey": "testkey",
                    "useSsl": false,
                    "baseUrl": "/radarr",
                    "isDefault": true,
                    "externalUrl": "https://radarr.example.com"
                }
            ]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = apiClient.getRadarrSettings(baseUrl, "apikey")

        assertTrue(result.isSuccess)
        val settings = result.getOrThrow()
        assertEquals(1, settings.size)
        assertEquals("Radarr Main", settings[0].name)
        assertEquals("192.168.1.10", settings[0].hostname)
        assertEquals(7878, settings[0].port)
        assertEquals("testkey", settings[0].apiKey)
        assertEquals("/radarr", settings[0].baseUrl)
        assertEquals(true, settings[0].isDefault)
        assertEquals("https://radarr.example.com", settings[0].externalUrl)
    }

    @Test
    fun `getRadarrSettings handles error response`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\": \"Unauthorized\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = apiClient.getRadarrSettings(baseUrl, "wrongkey")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }
}
