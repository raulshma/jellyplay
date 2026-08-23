package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
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
        val result = apiClient.getRadarrSettings(baseUrl, SeerrCredentials.ApiKey("apikey"))

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
    fun `getServiceRadarrDetail parses nested server defaults from service endpoint`() = runBlocking {
        val jsonResponse = """
            {
                "server": {
                    "id": 1,
                    "name": "Radarr Main",
                    "is4k": false,
                    "isDefault": true,
                    "activeDirectory": "/data/movies",
                    "activeProfileId": 7,
                    "activeTags": [1, 2]
                },
                "profiles": [
                    { "id": 7, "name": "HD-1080p" },
                    { "id": 8, "name": "Ultra-HD" }
                ],
                "rootFolders": [
                    { "id": 1, "path": "/data/movies", "freeSpace": 1000, "totalSpace": 2000 },
                    { "id": 2, "path": "/data/movies-4k", "freeSpace": 1000, "totalSpace": 2000 }
                ],
                "tags": [
                    { "id": 1, "label": "wanted" }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = apiClient.getServiceRadarrDetail(baseUrl, SeerrCredentials.ApiKey("apikey"), 1)

        assertTrue(result.isSuccess)
        val detail = result.getOrThrow()
        assertEquals("/data/movies", detail.server?.activeDirectory)
        assertEquals(7, detail.server?.activeProfileId)
        assertEquals(true, detail.server?.isDefault)
        assertEquals(2, detail.rootFolders.size)
        assertEquals("/data/movies", detail.rootFolders[0].path)
        assertEquals(2, detail.profiles.size)
    }

    @Test
    fun `getServiceSonarrDetail parses nested server defaults from service endpoint`() = runBlocking {
        val jsonResponse = """
            {
                "server": {
                    "id": 1,
                    "name": "Sonarr Main",
                    "is4k": false,
                    "isDefault": true,
                    "activeDirectory": "/data/tv",
                    "activeProfileId": 3,
                    "activeAnimeProfileId": 4,
                    "activeAnimeDirectory": "/data/anime",
                    "activeLanguageProfileId": 1,
                    "activeTags": [1],
                    "activeAnimeTags": [2]
                },
                "profiles": [
                    { "id": 3, "name": "HD-1080p" }
                ],
                "rootFolders": [
                    { "id": 1, "path": "/data/tv", "freeSpace": 1000, "totalSpace": 2000 },
                    { "id": 2, "path": "/data/anime", "freeSpace": 1000, "totalSpace": 2000 }
                ],
                "languageProfiles": [
                    { "id": 1, "name": "English" }
                ],
                "tags": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = apiClient.getServiceSonarrDetail(baseUrl, SeerrCredentials.ApiKey("apikey"), 1)

        assertTrue(result.isSuccess)
        val detail = result.getOrThrow()
        assertEquals("/data/tv", detail.server?.activeDirectory)
        assertEquals("/data/anime", detail.server?.activeAnimeDirectory)
        assertEquals(2, detail.rootFolders.size)
    }

    @Test
    fun `getRadarrSettings handles error response`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\": \"Unauthorized\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = apiClient.getRadarrSettings(baseUrl, SeerrCredentials.ApiKey("wrongkey"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }
}
