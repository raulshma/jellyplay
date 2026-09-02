package com.raulshma.jellyplay.core.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BandwidthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var interceptor: BandwidthInterceptor

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        interceptor = BandwidthInterceptor()
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `media stream path produces a positive bandwidth estimate`() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(interceptor)
            .build()
        // Three media responses. Each CountingBody reports its own delta to the
        // shared interceptor; across three responses we accumulate >= 2 samples
        // (required for a throughput estimate), regardless of OkHttp's internal
        // read chunking. This exercises the delta path in addSample().
        val body = ByteArray(300 * 1024) { 65 } // ~300 KB per response
        repeat(3) { i ->
            server.enqueue(MockResponse().setBody(Buffer().write(body)))
            val response = client.newCall(
                Request.Builder().url(server.url("/Videos/item$i/stream").toString()).build()
            ).execute()
            response.body.bytes()
            response.close()
        }

        val kbps = interceptor.estimatedBandwidthKbps.value
        assertTrue(
            kbps > 0.0 && !kbps.isInfinite() && !kbps.isNaN(),
            "bandwidth should be a positive, finite value after media-stream responses, was $kbps",
        )
    }

    @Test
    fun `non-media path does not produce a bandwidth estimate`() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(interceptor)
            .build()
        server.enqueue(MockResponse().setBody("payload"))

        val response = client.newCall(
            Request.Builder().url(server.url("/Items/abc").toString()).build()
        ).execute()
        response.body.bytes()
        response.close()

        // /Items/abc does not match the /Audio/, /Videos/, or /stream filter,
        // so the body is never wrapped and no samples are recorded.
        assertEquals(0.0, interceptor.estimatedBandwidthKbps.value, 0.0001)
    }

    @Test
    fun `audio path is also measured as a media stream`() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(interceptor)
            .build()
        val body = ByteArray(300 * 1024) { 65 }
        repeat(2) { i ->
            server.enqueue(MockResponse().setBody(Buffer().write(body)))
            val response = client.newCall(
                Request.Builder().url(server.url("/Audio/item$i/stream").toString()).build()
            ).execute()
            response.body.bytes()
            response.close()
        }

        assertTrue(
            interceptor.estimatedBandwidthKbps.value > 0.0,
            "audio stream bandwidth should be positive, was ${interceptor.estimatedBandwidthKbps.value}",
        )
    }
}
