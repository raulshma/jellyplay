package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boundary table for [isLocalServerHost] — the network-split bitrate cap's
 * host classifier and the local-network-permission heuristic's shared home.
 * Public hosts must classify `false` (a remote relay must not
 * silently inherit the home-network quality tier); every RFC-private/
 * loopback/link-local range, mDNS and bare hostname must classify `true`.
 */
class ServerHostClassificationTest {

    private fun assertLocal(host: String) {
        assertTrue("expected LOCAL: $host") { isLocalServerHost(host) }
    }

    private fun assertRemote(host: String) {
        assertFalse("expected REMOTE: $host") { isLocalServerHost(host) }
    }

    @Test
    fun `192_168 prefix is local`() {
        assertLocal("192.168.0.0")
        assertLocal("192.168.1.10")
        assertLocal("192.168.255.254")
    }

    @Test
    fun `192_169 is not local`() {
        assertRemote("192.169.1.1")
    }

    @Test
    fun `10 prefix is local`() {
        assertLocal("10.0.0.1")
        assertLocal("10.1.2.3")
        assertLocal("10.255.255.255")
    }

    @Test
    fun `11 prefix is not local`() {
        assertRemote("11.0.0.1")
    }

    @Test
    fun `172_16-31 range is local`() {
        assertLocal("172.16.0.1")
        assertLocal("172.17.5.5")
        assertLocal("172.31.255.255")
    }

    @Test
    fun `172 outside 16-31 is not local`() {
        assertRemote("172.15.0.1")
        assertRemote("172.32.0.1")
    }

    @Test
    fun `loopback and link-local ranges are local`() {
        assertLocal("127.0.0.1")
        assertLocal("127.9.9.9")
        assertLocal("169.254.10.10")
    }

    @Test
    fun `public ips are not local`() {
        assertRemote("8.8.8.8")
        assertRemote("1.1.1.1")
        assertRemote("203.0.113.7")
    }

    @Test
    fun `well-formed but out-of-range quads are not local`() {
        assertRemote("300.168.1.1")
        assertRemote("10.0.0.999")
        assertRemote("1.2.3")
        assertRemote("1.2.3.4.5")
    }

    @Test
    fun `malformed hosts are not local`() {
        assertRemote("192.168.1.1a")
        assertRemote("not.a.host")
    }

    @Test
    fun `localhost and mdns names are local`() {
        assertLocal("localhost")
        assertLocal("LOCALHOST")
        assertLocal("jellyfin.local")
        assertLocal("NAS.LOCAL")
    }

    @Test
    fun `bare hostnames are local`() {
        assertLocal("jellyfin")
        assertLocal("nas")
    }

    @Test
    fun `dotted public domains are not local`() {
        assertRemote("jellyfin.example.com")
        assertRemote("myserver.org")
    }

    @Test
    fun `scheme and port are stripped before classification`() {
        assertLocal("http://192.168.1.10:8096")
        assertLocal("https://10.0.0.2/jellyfin/web")
        assertLocal("ftp://nas:5000")
        assertRemote("http://example.com:8096")
    }

    @Test
    fun `bracketed ipv6 loopback is local`() {
        assertLocal("[::1]")
        assertLocal("[::1]:8096")
        assertLocal("http://[::1]:8096")
    }

    /**
     * Current-behaviour pin (shared with the core:ui twin): an UNbracketed
     * `::1` is split at its first colon (port stripping happens before the
     * loopback check), yielding an empty host that classifies as not-local.
     * Only the bracketed forms survive.
     */
    @Test
    fun `unbracketed ipv6 loopback is currently classified as remote`() {
        assertRemote("::1")
    }

    @Test
    fun `public ipv6 is not local`() {
        assertRemote("[2001:db8::1]")
        assertRemote("http://[2001:db8::1]:8920")
    }

    @Test
    fun `blank hosts are not local`() {
        assertRemote("")
        assertRemote("   ")
    }

    // ── serverHostOf ─────────────────────────────────────────────────────

    @Test
    fun `host extraction strips scheme path port and credentials`() {
        assertEquals("192.168.1.10", serverHostOf("http://192.168.1.10:8096/jellyfin/web/index.html"))
        assertEquals("nas", serverHostOf("https://user:pass@nas:8920"))
        assertEquals("example.com", serverHostOf("https://example.com"))
        assertEquals("::1", serverHostOf("http://[::1]:8096"))
        assertEquals("2001:db8::1", serverHostOf("http://[2001:db8::1]/path"))
    }

    @Test
    fun `host extraction yields null without a host part`() {
        assertNull(serverHostOf(null))
        assertNull(serverHostOf(""))
        assertNull(serverHostOf("   "))
        assertNull(serverHostOf("http://"))
        assertNull(serverHostOf("http://:8096"))
    }
}
