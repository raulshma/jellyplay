package com.raulshma.jellyplay.core.data.cast.dlna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pure-XML tests for [UpnpDeviceParser.parseDeviceXml] — no network, no OkHttp.
 * Pins the device-description contract the cast picker depends on:
 *
 *  - friendlyName / UDN / per-service control URLs are extracted and resolved
 *    against `URLBase` (falling back to the description's own location URL);
 *  - absolute `controlURL`s pass through untouched;
 *  - a missing AVTransport service (the minimum bar for a cast target) or a
 *    missing UDN rejects the device entirely (null);
 *  - malformed XML is rejected (null), never thrown.
 */
class UpnpDeviceParserTest {

    private val locationUrl = "http://192.168.1.50:8200/description.xml"

    private fun deviceXml(
        friendlyName: String = "Living Room Speaker",
        udn: String = "uuid:1234abcd-56ef",
        includeUrlBase: Boolean = false,
        avTransportControlUrl: String? = "/AVTransport/control",
        renderingControlControlUrl: String? = "/RenderingControl/control",
        connectionManagerControlUrl: String? = "/ConnectionManager/control",
        includeIconList: Boolean = false,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append("""<root xmlns="urn:schemas-upnp-org:device-1-0">""")
        append('\n')
        append("  <specVersion><major>1</major><minor>0</minor></specVersion>")
        append('\n')
        if (includeUrlBase) {
            append("  <URLBase>http://10.0.0.9:9999</URLBase>")
            append('\n')
        }
        append("  <device>")
        append('\n')
        append("    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>")
        append('\n')
        append("    <friendlyName>$friendlyName</friendlyName>")
        append('\n')
        append("    <manufacturer>Test Corp</manufacturer>")
        append('\n')
        append("    <modelName>Model-X</modelName>")
        append('\n')
        append("    <modelDescription>A renderer for tests</modelDescription>")
        append('\n')
        append("    <UDN>$udn</UDN>")
        append('\n')
        if (includeIconList) {
            append("    <iconList>")
            append('\n')
            append("      <icon><mimetype>image/png</mimetype><width>48</width><height>48</height><depth>24</depth><url>/icons/small.png</url></icon>")
            append('\n')
            append("      <icon><mimetype>image/png</mimetype><width>120</width><height>120</height><depth>24</depth><url>/icons/big.png</url></icon>")
            append('\n')
            append("    </iconList>")
            append('\n')
        }
        if (avTransportControlUrl != null || renderingControlControlUrl != null || connectionManagerControlUrl != null) {
            append("    <serviceList>")
            append('\n')
            avTransportControlUrl?.let {
                append("      <service>")
                append('\n')
                append("        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>")
                append('\n')
                append("        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>")
                append('\n')
                append("        <controlURL>$it</controlURL>")
                append('\n')
                append("        <eventSubURL>/AVTransport/event</eventSubURL>")
                append('\n')
                append("      </service>")
                append('\n')
            }
            renderingControlControlUrl?.let {
                append("      <service>")
                append('\n')
                append("        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>")
                append('\n')
                append("        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>")
                append('\n')
                append("        <controlURL>$it</controlURL>")
                append('\n')
                append("        <eventSubURL>/RenderingControl/event</eventSubURL>")
                append('\n')
                append("      </service>")
                append('\n')
            }
            connectionManagerControlUrl?.let {
                append("      <service>")
                append('\n')
                append("        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>")
                append('\n')
                append("        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>")
                append('\n')
                append("        <controlURL>$it</controlURL>")
                append('\n')
                append("        <eventSubURL>/ConnectionManager/event</eventSubURL>")
                append('\n')
                append("      </service>")
                append('\n')
            }
            append("    </serviceList>")
            append('\n')
        }
        append("  </device>")
        append('\n')
        append("</root>")
        append('\n')
    }

    // ── Happy path ──────────────────────────────────────────────────────

    @Test
    fun `parses friendlyName UDN and control URLs with relative paths`() {
        val device = UpnpDeviceParser.parseDeviceXml(deviceXml(), locationUrl)

        assertNotNull(device)
        assertEquals("Living Room Speaker", device.friendlyName)
        assertEquals("uuid:1234abcd-56ef", device.udn)
        assertEquals("Test Corp", device.manufacturer)
        assertEquals("Model-X", device.modelName)
        assertEquals("A renderer for tests", device.modelDescription)
        // No URLBase → relative control URLs resolve against the location URL.
        assertEquals("http://192.168.1.50:8200/AVTransport/control", device.avTransportControlUrl)
        assertEquals("http://192.168.1.50:8200/RenderingControl/control", device.renderingControlUrl)
        assertEquals("http://192.168.1.50:8200/ConnectionManager/control", device.connectionManagerUrl)
        assertEquals(locationUrl, device.locationUrl)
    }

    @Test
    fun `relative control URLs resolve against URLBase when present`() {
        val device = UpnpDeviceParser.parseDeviceXml(deviceXml(includeUrlBase = true), locationUrl)

        assertNotNull(device)
        assertEquals("http://10.0.0.9:9999/AVTransport/control", device.avTransportControlUrl)
        assertEquals("http://10.0.0.9:9999/RenderingControl/control", device.renderingControlUrl)
        assertEquals("http://10.0.0.9:9999/ConnectionManager/control", device.connectionManagerUrl)
    }

    @Test
    fun `absolute control URLs pass through unchanged`() {
        val device = UpnpDeviceParser.parseDeviceXml(
            deviceXml(avTransportControlUrl = "http://other-host:1234/AVT/control"),
            locationUrl,
        )

        assertNotNull(device)
        assertEquals("http://other-host:1234/AVT/control", device.avTransportControlUrl)
    }

    @Test
    fun `missing friendlyName falls back to Unknown DLNA Device`() {
        val xml = deviceXml().replace(
            "    <friendlyName>Living Room Speaker</friendlyName>\n",
            "",
        )

        val device = UpnpDeviceParser.parseDeviceXml(xml, locationUrl)

        assertNotNull(device)
        assertEquals("Unknown DLNA Device", device.friendlyName)
    }

    @Test
    fun `picks the largest icon at or below the 256x256 budget`() {
        val device = UpnpDeviceParser.parseDeviceXml(deviceXml(includeIconList = true), locationUrl)

        assertNotNull(device)
        assertEquals("http://192.168.1.50:8200/icons/big.png", device.iconUrl)
    }

    // ── Rejection paths ─────────────────────────────────────────────────

    @Test
    fun `device without an AVTransport service is rejected`() {
        val device = UpnpDeviceParser.parseDeviceXml(
            deviceXml(avTransportControlUrl = null),
            locationUrl,
        )

        assertNull(device)
    }

    @Test
    fun `device without a UDN is rejected`() {
        val xml = deviceXml().replace(
            "    <UDN>uuid:1234abcd-56ef</UDN>\n",
            "",
        )

        assertNull(UpnpDeviceParser.parseDeviceXml(xml, locationUrl))
    }

    @Test
    fun `malformed XML returns null instead of throwing`() {
        assertNull(UpnpDeviceParser.parseDeviceXml("<root><device>", locationUrl))
        assertNull(UpnpDeviceParser.parseDeviceXml("not xml at all <<<", locationUrl))
        assertNull(UpnpDeviceParser.parseDeviceXml("", locationUrl))
    }

    @Test
    fun `XML without a device element returns null`() {
        assertNull(
            UpnpDeviceParser.parseDeviceXml(
                """<?xml version="1.0"?><root><specVersion/></root>""",
                locationUrl,
            ),
        )
    }
}
