package com.raulshma.jellyplay.core.data.cast.dlna

import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins [DlnaCastStrategy]'s renderer-control invariants against a local
 * [MockWebServer] standing in for the UPnP device:
 *
 * - `connect` requires an [UpnpDevice] (device descriptor) — a tag-less device
 *   is a silent no-op. A successful connect flips isConnected, clears
 *   isConnecting, and persists the device as a recent DLNA renderer via
 *   [AppRuntimeStateStore.addRecentDlnaDevice].
 * - Connecting a second device while one is connected first sends the AVT
 *   `Stop` action to the previous renderer.
 * - `disconnect` sends Stop (when the device exposes an AVT URL), resets the
 *   connection flag and zeroes the transport flows.
 * - `loadMedia` is a SetAVTransportURI (with DIDL-Lite metadata + the stream
 *   URI) followed by Play, and only flips isPlaying when Play succeeded;
 *   transport calls without a connected device never touch the network.
 * - `setRendererVolume` coerces to 0..1 after sending.
 * - `refreshPlaybackState` maps GetPositionInfo → (ms, ms), GetTransportState
 *   → isPlaying, GetVolume → 0..1, and survives an unreachable device.
 */
class DlnaCastStrategyTest {

    private lateinit var server: MockWebServer
    private val appRuntimeStateStore: AppRuntimeStateStore = mockk(relaxed = true)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String = "") = MockResponse().setResponseCode(200).setBody(body)

    private fun soapEnvelope(responseXml: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>$responseXml</s:Body>
        </s:Envelope>
    """.trimIndent()

    private val avTransportNs = "urn:schemas-upnp-org:service:AVTransport:1"
    private val renderingControlNs = "urn:schemas-upnp-org:service:RenderingControl:1"

    private fun strategy(): DlnaCastStrategy =
        DlnaCastStrategy(
            appContext = mockk(relaxed = true),
            okHttpClient = OkHttpClient(),
            appRuntimeStateStore = appRuntimeStateStore,
        )

    private fun upnpDevice(avTransport: Boolean = true, rendering: Boolean = true) = UpnpDevice(
        udn = "uuid:renderer-1",
        friendlyName = "Living Room TV",
        locationUrl = server.url("/desc.xml").toString(),
        avTransportControlUrl = if (avTransport) server.url("/avt").toString() else null,
        renderingControlUrl = if (rendering) server.url("/rc").toString() else null,
    )

    private fun castDevice(device: UpnpDevice) = CastDevice(
        id = device.udn,
        name = device.friendlyName,
        type = "dlna",
        tag = device,
        strategyName = "dlna",
    )

    private fun awaitTrue(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    // ── connect / disconnect ─────────────────────────────────────────────

    @Test
    fun `connect to a tagged device marks the strategy connected and saves the recent renderer`() {
        val s = strategy()

        s.connect(mockk(relaxed = true), castDevice(upnpDevice()))

        assertTrue(s.isConnected.value)
        assertFalse(s.isConnecting.value)
        coVerify(timeout = 3_000) {
            appRuntimeStateStore.addRecentDlnaDevice(
                DlnaDeviceRef(id = "uuid:renderer-1", name = "Living Room TV", locationUrl = any()),
            )
        }
    }

    @Test
    fun `connect without a device descriptor is a no-op`() {
        val s = strategy()
        val unknown = CastDevice(id = "uuid:x", name = "?", type = "dlna", tag = null, strategyName = "dlna")

        s.connect(mockk(relaxed = true), unknown)

        assertFalse(s.isConnected.value)
        coVerify(exactly = 0) { appRuntimeStateStore.addRecentDlnaDevice(any()) }
    }

    @Test
    fun `reconnecting stops the previous renderer first`() {
        val s = strategy()
        s.connect(mockk(relaxed = true), castDevice(upnpDevice()))
        server.enqueue(ok()) // the Stop sent to the previous renderer

        s.connect(mockk(relaxed = true), castDevice(upnpDevice(avTransport = false)))

        val stop = server.takeRequest(3_000, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("expected an AVT Stop request", stop != null && stop.body.readUtf8().contains("<u:Stop"))
        assertTrue(s.isConnected.value)
    }

    @Test
    fun `disconnect sends Stop and resets the transport flows`() {
        val s = strategy()
        s.connect(mockk(relaxed = true), castDevice(upnpDevice()))
        server.enqueue(ok())

        s.disconnect(mockk(relaxed = true))

        val stop = server.takeRequest(3_000, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(stop != null && stop.body.readUtf8().contains("<u:Stop"))
        assertFalse(s.isConnected.value)
        assertEquals(0L, s.rendererPositionMs.value)
        assertEquals(0L, s.rendererDurationMs.value)
        assertFalse(s.rendererIsPlaying.value)
    }

    // ── loadMedia / transport ────────────────────────────────────────────

    @Test
    fun `loadMedia sets the AVT URI with DIDL metadata then plays and flips isPlaying`() {
        val s = strategy()
        s.connect(mockk(relaxed = true), castDevice(upnpDevice()))
        server.enqueue(ok())
        server.enqueue(ok())

        s.loadMedia(url = "http://server/media.mkv", title = "Movie")

        awaitTrue { s.rendererIsPlaying.value }
        val setUri = server.takeRequest(1_000, java.util.concurrent.TimeUnit.SECONDS)
        val play = server.takeRequest(1_000, java.util.concurrent.TimeUnit.SECONDS)
        val setBody = setUri?.body?.readUtf8().orEmpty()
        val playBody = play?.body?.readUtf8().orEmpty()
        assertTrue(setBody.contains("<u:SetAVTransportURI"))
        assertTrue(setBody.contains("<CurrentURI>http://server/media.mkv</CurrentURI>"))
        assertTrue(setBody.contains("DIDL-Lite"))
        assertTrue(playBody.contains("<u:Play"))
    }

    @Test
    fun `loadMedia without a connected device never touches the network`() {
        val s = strategy()

        s.loadMedia(url = "http://server/media.mkv", title = "Movie")
        s.play()
        s.pause()
        s.stop()
        s.seekTo(1_000L)

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `loadMedia on a device without an AVT URL is a no-op`() {
        val s = strategy()
        s.connect(mockk(relaxed = true), castDevice(upnpDevice(avTransport = false)))

        s.loadMedia(url = "http://server/media.mkv", title = "Movie")

        assertEquals(0, server.requestCount)
    }

    // ── volume ───────────────────────────────────────────────────────────

    @Test
    fun `setRendererVolume sends the percent and coerces the local value to 0-1`() {
        val s = strategy()
        s.connect(mockk(relaxed = true), castDevice(upnpDevice()))
        server.enqueue(ok())

        s.setRendererVolume(1.5f)

        awaitTrue { s.rendererVolume.value == 1.0f }
        val request = server.takeRequest(1_000, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(request?.body?.readUtf8()?.contains("<DesiredVolume>100</DesiredVolume>") == true)
    }

    @Test
    fun `setRendererVolume without a rendering-control URL is a no-op`() {
        val s = strategy()
        s.connect(mockk(relaxed = true), castDevice(upnpDevice(rendering = false)))

        s.setRendererVolume(0.5f)

        assertEquals(0, server.requestCount)
        assertEquals(1f, s.rendererVolume.value)
    }

    // ── refreshPlaybackState ─────────────────────────────────────────────

    @Test
    fun `refreshPlaybackState maps position duration state and volume`() {
        val s = strategy()
        s.connect(mockk(relaxed = true), castDevice(upnpDevice()))
        server.enqueue(ok(soapEnvelope(
            """<u:GetPositionInfoResponse xmlns:u="$avTransportNs">
                 <RelTime>0:01:30</RelTime>
                 <Duration>00:04:00</Duration>
               </u:GetPositionInfoResponse>""",
        )))
        server.enqueue(ok(soapEnvelope(
            """<u:GetTransportInfoResponse xmlns:u="$avTransportNs">
                 <CurrentTransportState>PLAYING</CurrentTransportState>
               </u:GetTransportInfoResponse>""",
        )))
        server.enqueue(ok(soapEnvelope(
            """<u:GetVolumeResponse xmlns:u="$renderingControlNs"><CurrentVolume>37</CurrentVolume></u:GetVolumeResponse>""",
        )))

        runBlocking { s.refreshPlaybackState() }

        assertEquals(90_000L, s.rendererPositionMs.value)
        assertEquals(240_000L, s.rendererDurationMs.value)
        assertTrue(s.rendererIsPlaying.value)
        assertEquals(0.37f, s.rendererVolume.value, 0.0001f)
    }

    @Test
    fun `refreshPlaybackState survives an unreachable renderer`() {
        val s = strategy()
        val unreachable = upnpDevice().copy(
            avTransportControlUrl = "http://127.0.0.1:1/avt",
            renderingControlUrl = "http://127.0.0.1:1/rc",
        )
        s.connect(mockk(relaxed = true), castDevice(unreachable))

        runBlocking { s.refreshPlaybackState() }

        assertEquals(0L, s.rendererPositionMs.value)
        assertEquals(0L, s.rendererDurationMs.value)
        assertFalse(s.rendererIsPlaying.value)
    }

    @Test
    fun `refreshPlaybackState without a connected device is a no-op`() {
        val s = strategy()

        runBlocking { s.refreshPlaybackState() }

        assertEquals(0, server.requestCount)
        assertEquals(0L, s.rendererPositionMs.value)
    }
}
