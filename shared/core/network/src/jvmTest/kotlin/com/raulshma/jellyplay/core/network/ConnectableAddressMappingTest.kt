package com.raulshma.jellyplay.core.network

import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [connectableAddress] NAT mapping at the unit seam: Docker-bridge
 * payloads (container-internal `Address`, LAN `endpointAddress`) rewrite onto
 * the datagram source, while host-network payloads survive verbatim.
 */
class ConnectableAddressMappingTest {

    private fun info(address: String, endpointAddress: String?) = ServerDiscoveryInfo(
        address = address,
        id = "id",
        name = "Server",
        endpointAddress = endpointAddress,
    )

    @Test
    fun `bridge NAT - payload container address rewrites onto datagram source with payload scheme and port`() {
        assertEquals(
            "http://192.168.1.20:8096",
            info("http://172.17.0.2:8096", "192.168.1.20").connectableAddress(),
        )
    }

    @Test
    fun `https port is preserved in the rewrite`() {
        assertEquals(
            "https://10.0.0.5:8920",
            info("https://172.17.0.2:8920", "10.0.0.5").connectableAddress(),
        )
    }

    @Test
    fun `host network - payload naming the datagram source survives verbatim`() {
        val payload = "http://192.168.1.20:8096"
        assertEquals(payload, info(payload, "192.168.1.20").connectableAddress())
    }

    @Test
    fun `hostname payload matching endpoint host case-insensitively survives verbatim`() {
        val payload = "http://myserver.local:8096"
        assertEquals(payload, info(payload, "MyServer.Local").connectableAddress())
    }

    @Test
    fun `missing endpoint address falls back to payload`() {
        val payload = "http://192.168.1.20:8096"
        assertEquals(payload, info(payload, null).connectableAddress())
    }

    @Test
    fun `blank endpoint address falls back to payload`() {
        val payload = "http://192.168.1.20:8096"
        assertEquals(payload, info(payload, "  ").connectableAddress())
    }

    @Test
    fun `bare ipv6 endpoint gets bracketed`() {
        assertEquals(
            "http://[fe80::1234:5678]:8096",
            info("http://172.17.0.2:8096", "fe80::1234:5678").connectableAddress(),
        )
    }

    @Test
    fun `unparseable payload address falls back to payload`() {
        val payload = "not a url"
        assertEquals(payload, info(payload, "192.168.1.20").connectableAddress())
    }

    @Test
    fun `payload without explicit port omits port in rewrite`() {
        assertEquals(
            "http://192.168.1.20",
            info("http://172.17.0.2", "192.168.1.20").connectableAddress(),
        )
    }
}
