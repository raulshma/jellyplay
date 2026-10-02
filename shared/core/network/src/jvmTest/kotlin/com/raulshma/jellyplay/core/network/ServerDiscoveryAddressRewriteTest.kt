package com.raulshma.jellyplay.core.network

import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deterministic matrix for [connectableAddress] — the address-rewrite logic
 * the e2e [ServerDiscoveryServiceJvmTest] pins over real UDP. That loop needs
 * the host to loop 255.255.255.255 broadcasts back to local sockets, which
 * not every environment does (GitHub's macOS runners never deliver it), so
 * this suite carries the full rewrite contract everywhere: Docker-bridge NAT
 * rewrite, host-network verbatim survival, IPv6 bracketing, and the
 * fall-back-to-payload arms.
 */
class ServerDiscoveryAddressRewriteTest {

    private fun info(
        address: String,
        endpointAddress: String? = null,
    ): ServerDiscoveryInfo = ServerDiscoveryInfo(
        address = address,
        id = "9cd3d9be17d54f6a8e7d8f0f2b1c4a55",
        name = "JellyfinDocker",
        endpointAddress = endpointAddress,
    )

    @Test
    fun `Docker bridge payload is rewritten to the datagram source host with payload scheme and port`() {
        // Payload names the container-internal interface; the SDK surfaces the
        // NAT host in endpointAddress. Result: source host, payload scheme+port.
        val rewritten = info(
            address = "http://172.17.0.2:8096",
            endpointAddress = "192.168.1.10",
        ).connectableAddress()
        assertEquals("http://192.168.1.10:8096", rewritten)
    }

    @Test
    fun `payload address survives verbatim when its host already equals the datagram source`() {
        // --network=host / bare metal: no rewrite, port and scheme untouched.
        assertEquals(
            "https://192.168.1.10:8920",
            info(
                address = "https://192.168.1.10:8920",
                endpointAddress = "192.168.1.10",
            ).connectableAddress(),
        )
    }

    @Test
    fun `host match is case-insensitive`() {
        assertEquals(
            "http://MyServer.local:8096",
            info(
                address = "http://MyServer.local:8096",
                endpointAddress = "myserver.LOCAL",
            ).connectableAddress(),
        )
    }

    @Test
    fun `IPv6 endpoint host is bracketed in the rewritten authority`() {
        // The SDK's getHostAddress() emits bare IPv6; a URI authority needs brackets.
        assertEquals(
            "http://[fd00::10]:8096",
            info(
                address = "http://172.17.0.2:8096",
                endpointAddress = "fd00::10",
            ).connectableAddress(),
        )
    }

    @Test
    fun `rewritten address defaults to http scheme and carries no port when the payload has none`() {
        assertEquals(
            "http://192.168.1.10",
            info(
                address = "http://172.17.0.2",
                endpointAddress = "192.168.1.10",
            ).connectableAddress(),
        )
        assertEquals(
            "https://192.168.1.10",
            info(
                address = "https://172.17.0.2",
                endpointAddress = "192.168.1.10",
            ).connectableAddress(),
        )
    }

    @Test
    fun `missing or blank endpoint address keeps the payload verbatim`() {
        assertEquals("http://172.17.0.2:8096", info(address = "http://172.17.0.2:8096").connectableAddress())
        assertEquals(
            "http://172.17.0.2:8096",
            info(address = "http://172.17.0.2:8096", endpointAddress = "  ").connectableAddress(),
        )
    }

    @Test
    fun `unparseable payload address falls back to it verbatim`() {
        // URI() throws → return the raw payload string, never a half-built URL.
        assertEquals(
            "not a uri :// at all",
            info(address = "not a uri :// at all", endpointAddress = "192.168.1.10").connectableAddress(),
        )
    }
}
