package com.raulshma.jellyplay.core.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the RANDOM-sort cache bust: `/Items?sortBy=Random` rides with
 * `Cache-Control: no-cache` so the OkHttp response cache can never replay a
 * previous shuffle to the dice re-roll; every other request passes untagged.
 */
class RandomSortCacheBusterInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    /** The request header the server OBSERVED (what OkHttp actually sent). */
    private var servedCacheControl: String? = null

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RandomSortCacheBusterInterceptor())
            .build()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun serveAndGetCacheControl(url: String): okhttp3.mockwebserver.RecordedRequest {
        server.enqueue(MockResponse().setBody("[]"))
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return server.takeRequest()
    }

    @Test
    fun `random sorted items query rides with no-cache`() {
        val recorded = serveAndGetCacheControl(server.url("/Items?sortBy=Random&Limit=20").toString())
        assertEquals("no-cache", recorded.getHeader("Cache-Control"))
    }

    @Test
    fun `random among multiple sort keys still busts`() {
        val recorded = serveAndGetCacheControl(server.url("/Items?sortBy=IsFavoriteOrLike,Random").toString())
        assertEquals("no-cache", recorded.getHeader("Cache-Control"))
    }

    @Test
    fun `deterministic sort is not busted`() {
        val recorded = serveAndGetCacheControl(server.url("/Items?sortBy=SortName&Limit=20").toString())
        assertNull(recorded.getHeader("Cache-Control"))
    }

    @Test
    fun `non items paths are not busted`() {
        val recorded = serveAndGetCacheControl(server.url("/Shows/NextUp?sortBy=Random").toString())
        assertNull(recorded.getHeader("Cache-Control"))
    }
}
