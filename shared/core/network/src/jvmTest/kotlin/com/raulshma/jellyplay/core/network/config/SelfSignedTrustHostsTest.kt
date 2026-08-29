package com.raulshma.jellyplay.core.network.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exhaustive unit tests for the pure host matcher behind the self-signed
 * trust layer: entry parsing (`scheme://host[:port]`, the
 * `ServerAddressRouter.addressString` canonical form) and the
 * granted-entry ↔ handshake-peer decision. No JVM network types involved —
 * the delegation/handshake behavior lives in [SelfSignedTrustManagerTest] /
 * [SelfSignedTrustHandshakeTest].
 */
class SelfSignedTrustHostsTest {

    // ------------------------------------------------------------- isGranted

    @Test
    fun `entry with explicit port matches same host and port`() {
        val entries = setOf("https://192.168.1.10:8920")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "192.168.1.10", 8920))
    }

    @Test
    fun `entry with explicit port rejects same host different port`() {
        val entries = setOf("https://192.168.1.10:8920")
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "192.168.1.10", 8921))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "192.168.1.10", 443))
    }

    @Test
    fun `entry without port matches the host on the default port`() {
        val entries = setOf("https://media.example.com")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "media.example.com", 443))
    }

    @Test
    fun `entry without port also matches the host on non-default ports`() {
        // Documented semantics: a portless entry means "this host over TLS" —
        // alternates of the same server share the grant instead of each
        // re-prompting. Trust never crosses to a different host.
        val entries = setOf("https://media.example.com")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "media.example.com", 8920))
    }

    @Test
    fun `grant never crosses to a different host`() {
        val entries = setOf("https://trusted.example.com")
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "other.example.com", 443))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "trusted.example.com.evil.net", 443))
        // A subdomain is a different host too — no wildcard matching.
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "sub.trusted.example.com", 443))
    }

    @Test
    fun `host comparison is case-insensitive on both sides`() {
        val entries = setOf("https://Media.Example.COM:8920")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "media.example.com", 8920))
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "MEDIA.example.com", 8920))
    }

    @Test
    fun `unknown peer port falls back to host-only comparison`() {
        // The hostname-verifier path tolerates a session without a port; the
        // trust manager has already gated the same handshake WITH the port.
        val entries = setOf("https://media.example.com:8920")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "media.example.com", -1))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "other.example.com", -1))
    }

    @Test
    fun `blank or null host is never granted`() {
        val entries = setOf("https://media.example.com")
        assertFalse(SelfSignedTrustHosts.isGranted(entries, null, 443))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "", 443))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "   ", 443))
    }

    @Test
    fun `empty entry set grants nothing`() {
        assertFalse(SelfSignedTrustHosts.isGranted(emptySet(), "media.example.com", 443))
    }

    @Test
    fun `one of several entries matching is enough`() {
        val entries = setOf("https://a.example.com", "https://b.example.com:8920", "https://c.example.com")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "b.example.com", 8920))
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "c.example.com", 443))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "b.example.com", 443))
    }

    @Test
    fun `scheme-less entry is treated as a bare authority`() {
        // Defensive: user-typed input that escaped normalization.
        val entries = setOf("media.example.com:8920")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "media.example.com", 8920))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "media.example.com", 443))
    }

    @Test
    fun `entry with trailing slash or path still matches its authority`() {
        val entries = setOf("https://media.example.com:8920/")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "media.example.com", 8920))
    }

    @Test
    fun `unparseable entries are ignored, not fatal`() {
        val entries = setOf(":::", "https://", "https://[::1", "https://host:notaport")
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "host", 443))
        // A valid entry alongside garbage still works.
        assertTrue(
            SelfSignedTrustHosts.isGranted(entries + "https://host:443", "host", 443),
        )
    }

    @Test
    fun `ipv6 bracketed entries match bracket-stripped peers`() {
        val entries = setOf("https://[::1]:8920")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "::1", 8920))
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "[::1]", 8920))
        assertFalse(SelfSignedTrustHosts.isGranted(entries, "::2", 8920))
    }

    @Test
    fun `ipv6 entry without port matches`() {
        val entries = setOf("https://[fe80::1]")
        assertTrue(SelfSignedTrustHosts.isGranted(entries, "fe80::1", 443))
    }

    // ------------------------------------------------------------ parseEntry

    @Test
    fun `parseEntry handles the canonical router form`() {
        assertEquals(
            SelfSignedTrustHosts.ParsedEntry("media.example.com", 8920),
            SelfSignedTrustHosts.parseEntry("https://media.example.com:8920"),
        )
        assertEquals(
            SelfSignedTrustHosts.ParsedEntry("media.example.com", null),
            SelfSignedTrustHosts.parseEntry("https://media.example.com"),
        )
    }

    @Test
    fun `parseEntry lowercases host and strips userinfo path query fragment`() {
        assertEquals(
            SelfSignedTrustHosts.ParsedEntry("host.example.com", 1234),
            SelfSignedTrustHosts.parseEntry("HTTPS://User:Pass@Host.Example.com:1234/web?x=1#frag"),
        )
    }

    @Test
    fun `parseEntry rejects junk`() {
        assertNull(SelfSignedTrustHosts.parseEntry(""))
        assertNull(SelfSignedTrustHosts.parseEntry("   "))
        assertNull(SelfSignedTrustHosts.parseEntry("https://"))
        assertNull(SelfSignedTrustHosts.parseEntry("https://host:notaport"))
        assertNull(SelfSignedTrustHosts.parseEntry("https://host:0"))
        assertNull(SelfSignedTrustHosts.parseEntry("https://host:65536"))
        assertNull(SelfSignedTrustHosts.parseEntry("https://[::1")) // unclosed bracket
        assertNull(SelfSignedTrustHosts.parseEntry("http://a:b:c")) // two colons, no brackets
    }

    @Test
    fun `parseEntry accepts boundary ports`() {
        assertEquals(
            SelfSignedTrustHosts.ParsedEntry("host", 1),
            SelfSignedTrustHosts.parseEntry("https://host:1"),
        )
        assertEquals(
            SelfSignedTrustHosts.ParsedEntry("host", 65535),
            SelfSignedTrustHosts.parseEntry("https://host:65535"),
        )
    }

    // ---------------------------------------------------------- normalizeHost

    @Test
    fun `normalizeHost lowercases and strips brackets`() {
        assertEquals("media.example.com", SelfSignedTrustHosts.normalizeHost("Media.Example.COM"))
        assertEquals("::1", SelfSignedTrustHosts.normalizeHost("[::1]"))
        assertNull(SelfSignedTrustHosts.normalizeHost("  "))
    }
}
