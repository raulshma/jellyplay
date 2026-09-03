package com.raulshma.jellyplay.core.data.cast.dlna

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [UpnpControlPoint]'s SOAP wire contract + response parsing against a
 * local [MockWebServer] (a DLNA renderer's ControlURL stand-in):
 *  1. every action POSTs a SOAP envelope with the right `SOAPACTION`
 *     header (`"namespace#Action"`) and action-specific arguments;
 *  2. transport commands succeed on a 200 without an errorCode and fail —
 *     returning false, never throwing — on HTTP errors, SOAP faults, or an
 *     unreachable device;
 *  3. position info parses H:MM:SS (plus fractional seconds) to milliseconds;
 *  4. transport state + volume parse to the app enums / 0..1 float, with
 *     conservative defaults (UNKNOWN / 1f) for unparseable payloads;
 *  5. setVolume coerces the 0..1 app value onto the 0..100 wire value.
 */
class UpnpControlPointTest {

    private lateinit var server: MockWebServer
    private lateinit var controlUrl: String
    private val client = OkHttpClient()

    private val avTransportNs = "urn:schemas-upnp-org:service:AVTransport:1"
    private val renderingControlNs = "urn:schemas-upnp-org:service:RenderingControl:1"

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        controlUrl = server.url("/control").toString()
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    private fun ok(body: String = "") = MockResponse().setResponseCode(200).setBody(body)

    private fun soapEnvelope(responseXml: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>$responseXml</s:Body>
        </s:Envelope>
    """.trimIndent()

    // ── wire shaping ─────────────────────────────────────────────────────────

    @Test
    fun `setAvTransportUri posts the SOAP action with the uri and metadata`() = runBlocking {
        server.enqueue(ok())

        val success = UpnpControlPoint.setAvTransportUri(controlUrl, uri = "http://media/video.mkv", client = client)

        assertTrue(success)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("\"$avTransportNs#SetAVTransportURI\"", recorded.getHeader("SOAPACTION"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("<u:SetAVTransportURI"), body)
        assertTrue(body.contains("<CurrentURI>http://media/video.mkv</CurrentURI>"), body)
        assertTrue(body.contains("DIDL-Lite"), "a null metadata argument builds a DIDL-Lite body")
    }

    @Test
    fun `seek posts the REL_TIME target formatted as H-MM-SS`() = runBlocking {
        server.enqueue(ok())

        UpnpControlPoint.seek(controlUrl, positionMs = 3_723_000L, client = client)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("<Unit>REL_TIME</Unit>"), body)
        assertTrue(body.contains("<Target>01:02:03</Target>"), body)
    }

    @Test
    fun `pause and stop post their actions to the AV transport`() = runBlocking {
        server.enqueue(ok())
        server.enqueue(ok())

        UpnpControlPoint.pause(controlUrl, client = client)
        UpnpControlPoint.stop(controlUrl, client = client)

        val pause = server.takeRequest()
        assertEquals("\"$avTransportNs#Pause\"", pause.getHeader("SOAPACTION"))
        val stop = server.takeRequest()
        assertEquals("\"$avTransportNs#Stop\"", stop.getHeader("SOAPACTION"))
    }

    @Test
    fun `volume actions target the RenderingControl service`() = runBlocking {
        server.enqueue(ok())
        server.enqueue(ok())

        UpnpControlPoint.getVolume(controlUrl, client = client)
        UpnpControlPoint.setVolume(controlUrl, volume = 0.5f, client = client)

        val get = server.takeRequest()
        assertEquals("\"$renderingControlNs#GetVolume\"", get.getHeader("SOAPACTION"))
        val set = server.takeRequest()
        assertEquals("\"$renderingControlNs#SetVolume\"", set.getHeader("SOAPACTION"))
        assertTrue(set.body.readUtf8().contains("<DesiredVolume>50</DesiredVolume>"))
    }

    // ── success / failure semantics ──────────────────────────────────────────

    @Test
    fun `a SOAP fault (errorCode in the body) reads as a failure`() = runBlocking {
        server.enqueue(
            ok(
                soapEnvelope(
                    """<u:PlayResponse xmlns:u="$avTransportNs"/>""" +
                        """<FaultDetail><errorCode>401</errorCode></FaultDetail>""",
                ),
            ),
        )

        assertFalse(UpnpControlPoint.play(controlUrl, client = client))
    }

    @Test
    fun `an HTTP error with an empty body reads as a failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertFalse(UpnpControlPoint.play(controlUrl, client = client))
    }

    @Test
    fun `an unreachable device fails without throwing`() = runBlocking {
        // Port 1 on loopback refuses connections — no device listens there.
        assertFalse(UpnpControlPoint.play("http://127.0.0.1:1/control", client = client))
    }

    // ── response parsing ─────────────────────────────────────────────────────

    @Test
    fun `position info parses RelTime and Duration to milliseconds`() = runBlocking {
        server.enqueue(
            ok(
                soapEnvelope(
                    """<u:GetPositionInfoResponse xmlns:u="$avTransportNs">
                         <RelTime>0:01:30</RelTime>
                         <Duration>00:04:00</Duration>
                       </u:GetPositionInfoResponse>""",
                ),
            ),
        )

        val position = UpnpControlPoint.getPositionInfo(controlUrl, client = client)

        assertEquals(90_000L to 240_000L, position)
    }

    @Test
    fun `fractional seconds in the position are truncated`() = runBlocking {
        server.enqueue(
            ok(
                soapEnvelope(
                    """<u:GetPositionInfoResponse xmlns:u="$avTransportNs">
                         <RelTime>00:00:10.500</RelTime>
                         <Duration>NOT_IMPLEMENTED</Duration>
                       </u:GetPositionInfoResponse>""",
                ),
            ),
        )

        val position = UpnpControlPoint.getPositionInfo(controlUrl, client = client)

        assertEquals(10_000L to 0L, position)
    }

    @Test
    fun `a missing RelTime yields null position info`() = runBlocking {
        server.enqueue(
            ok(
                soapEnvelope(
                    """<u:GetPositionInfoResponse xmlns:u="$avTransportNs">
                         <Duration>00:04:00</Duration>
                       </u:GetPositionInfoResponse>""",
                ),
            ),
        )

        assertNull(UpnpControlPoint.getPositionInfo(controlUrl, client = client))
    }

    @Test
    fun `transport state maps onto the app enum`() = runBlocking {
        listOf("PLAYING", "PAUSED_PLAYBACK", "STOPPED", "TRANSITIONING", "NO_MEDIA_PRESENT", "BOGUS").forEach { state ->
            server.enqueue(
                ok(
                    soapEnvelope(
                        """<u:GetTransportInfoResponse xmlns:u="$avTransportNs">
                             <CurrentTransportState>$state</CurrentTransportState>
                           </u:GetTransportInfoResponse>""",
                    ),
                ),
            )
        }

        assertEquals(TransportState.PLAYING, UpnpControlPoint.getTransportInfo(controlUrl, client = client))
        assertEquals(TransportState.PAUSED, UpnpControlPoint.getTransportInfo(controlUrl, client = client))
        assertEquals(TransportState.STOPPED, UpnpControlPoint.getTransportInfo(controlUrl, client = client))
        assertEquals(TransportState.TRANSITIONING, UpnpControlPoint.getTransportInfo(controlUrl, client = client))
        assertEquals(TransportState.NO_MEDIA, UpnpControlPoint.getTransportInfo(controlUrl, client = client))
        assertEquals(TransportState.UNKNOWN, UpnpControlPoint.getTransportInfo(controlUrl, client = client))
    }

    @Test
    fun `volume parses to a 0-1 float with a neutral default on garbage`() = runBlocking {
        server.enqueue(ok(soapEnvelope(
            """<u:GetVolumeResponse xmlns:u="$renderingControlNs"><CurrentVolume>37</CurrentVolume></u:GetVolumeResponse>""",
        )))
        server.enqueue(ok(soapEnvelope(
            """<u:GetVolumeResponse xmlns:u="$renderingControlNs"><CurrentVolume>999</CurrentVolume></u:GetVolumeResponse>""",
        )))
        server.enqueue(ok(soapEnvelope(
            """<u:GetVolumeResponse xmlns:u="$renderingControlNs"><CurrentVolume>abc</CurrentVolume></u:GetVolumeResponse>""",
        )))

        assertEquals(0.37f, UpnpControlPoint.getVolume(controlUrl, client = client))
        assertEquals(1f, UpnpControlPoint.getVolume(controlUrl, client = client), "out-of-range clamps to 1")
        assertEquals(1f, UpnpControlPoint.getVolume(controlUrl, client = client), "unparseable defaults to 1")
    }

    @Test
    fun `setVolume coerces out-of-range app values onto the wire`() = runBlocking {
        server.enqueue(ok())
        server.enqueue(ok())

        UpnpControlPoint.setVolume(controlUrl, volume = 1.5f, client = client)
        UpnpControlPoint.setVolume(controlUrl, volume = -0.5f, client = client)

        val first = server.takeRequest().body.readUtf8()
        val second = server.takeRequest().body.readUtf8()
        assertTrue(first.contains("<DesiredVolume>100</DesiredVolume>"), first)
        assertTrue(second.contains("<DesiredVolume>0</DesiredVolume>"), second)
    }

    // ── pure helpers ─────────────────────────────────────────────────────────

    @Test
    fun `formatTime renders H-MM-SS`() {
        assertEquals("00:00:00", UpnpControlPoint.formatTime(0))
        assertEquals("00:01:05", UpnpControlPoint.formatTime(65_000))
        assertEquals("01:02:03", UpnpControlPoint.formatTime(3_723_000))
    }

    @Test
    fun `buildDidlLite escapes xml-sensitive metadata`() {
        val didl = UpnpControlPoint.buildDidlLite(
            url = "http://media/a&b.mp4",
            title = """Action & "Power" <Trailer>""",
            artist = "Some'Band",
        )

        assertTrue(didl.contains("http://media/a&amp;b.mp4"), didl)
        assertTrue(didl.contains("Action &amp; &quot;Power&quot; &lt;Trailer&gt;"), didl)
        assertTrue(didl.contains("Some&apos;Band"), didl)
    }
}
