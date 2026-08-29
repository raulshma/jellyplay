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
 *
 * The matcher lives in commonMain (`SelfSignedTrustMatcher`) since the
 * wave-21 review round; the jvmShared `SelfSignedTrustHosts` facade it left
 * behind is pinned below (one delegation test) so a drift between the two
 * can't compile-and-pass silently.
 */
class SelfSignedTrustMatcherTest {

    // ------------------------------------------- jvmShared facade delegation pin

    @Test
    fun `jvmShared facade delegates to the common matcher unchanged`() {
        val entries = setOf("https://media.example.com")
        // One spot-check per interesting branch: portless grant any-port,
        // host mismatch fail-closed, empty set.
        assertEquals(
            SelfSignedTrustMatcher.isGranted(entries, "media.example.com", 8920),
            SelfSignedTrustHosts.isGranted(entries, "media.example.com", 8920),
        )
        assertEquals(
            SelfSignedTrustMatcher.isGranted(entries, "other.example.com", 443),
            SelfSignedTrustHosts.isGranted(entries, "other.example.com", 443),
        )
        assertEquals(
            SelfSignedTrustMatcher.isGranted(emptySet(), "media.example.com", 443),
            SelfSignedTrustHosts.isGranted(emptySet(), "media.example.com", 443),
        )
    }

    // -------------------------------------------------------- isAddressGranted

    @Test
    fun `portless grant covers a ported address - the display-drift regression`() {
        // Wave-21 review finding: the settings toggle used exact string
        // membership, so a portless grant showed OFF for a ported primary even
        // though every handshake honored it.
        val entries = setOf("https://media.example.com")
        assertTrue(SelfSignedTrustMatcher.isAddressGranted(entries, "https://media.example.com:8920"))
        assertTrue(SelfSignedTrustMatcher.isAddressGranted(entries, "https://media.example.com"))
    }

    @Test
    fun `ported grant does not cover a different port of the same host`() {
        val entries = setOf("https://media.example.com:8920")
        assertFalse(SelfSignedTrustMatcher.isAddressGranted(entries, "https://media.example.com:8921"))
        assertFalse(SelfSignedTrustMatcher.isAddressGranted(entries, "https://media.example.com"))
    }

    @Test
    fun `address check is case-insensitive and tolerant of missing scheme`() {
        val entries = setOf("https://Media.Example.COM")
        assertTrue(SelfSignedTrustMatcher.isAddressGranted(entries, "media.example.com"))
        assertTrue(SelfSignedTrustMatcher.isAddressGranted(entries, "https://MEDIA.example.com:443"))
    }

    @Test
    fun `unparseable or uncovered addresses are never granted`() {
        val entries = setOf("https://media.example.com")
        assertFalse(SelfSignedTrustMatcher.isAddressGranted(entries, ""))
        assertFalse(SelfSignedTrustMatcher.isAddressGranted(entries, "https://"))
        assertFalse(SelfSignedTrustMatcher.isAddressGranted(entries, "https://other.example.com"))
        assertFalse(SelfSignedTrustMatcher.isAddressGranted(emptySet(), "https://media.example.com"))
    }

    // ------------------------------------------------------------- isGranted

    @Test
    fun `entry with explicit port matches same host and port`() {
        val entries = setOf("https://192.168.1.10:8920")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "192.168.1.10", 8920))
    }

    @Test
    fun `entry with explicit port rejects same host different port`() {
        val entries = setOf("https://192.168.1.10:8920")
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "192.168.1.10", 8921))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "192.168.1.10", 443))
    }

    @Test
    fun `entry without port matches the host on the default port`() {
        val entries = setOf("https://media.example.com")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "media.example.com", 443))
    }

    @Test
    fun `entry without port also matches the host on non-default ports`() {
        // Documented semantics: a portless entry means "this host over TLS" —
        // alternates of the same server share the grant instead of each
        // re-prompting. Trust never crosses to a different host.
        val entries = setOf("https://media.example.com")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "media.example.com", 8920))
    }

    @Test
    fun `grant never crosses to a different host`() {
        val entries = setOf("https://trusted.example.com")
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "other.example.com", 443))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "trusted.example.com.evil.net", 443))
        // A subdomain is a different host too — no wildcard matching.
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "sub.trusted.example.com", 443))
    }

    @Test
    fun `host comparison is case-insensitive on both sides`() {
        val entries = setOf("https://Media.Example.COM:8920")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "media.example.com", 8920))
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "MEDIA.example.com", 8920))
    }

    @Test
    fun `unknown peer port falls back to host-only comparison`() {
        // The hostname-verifier path tolerates a session without a port; the
        // trust manager has already gated the same handshake WITH the port.
        val entries = setOf("https://media.example.com:8920")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "media.example.com", -1))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "other.example.com", -1))
    }

    @Test
    fun `blank or null host is never granted`() {
        val entries = setOf("https://media.example.com")
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, null, 443))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "", 443))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "   ", 443))
    }

    @Test
    fun `empty entry set grants nothing`() {
        assertFalse(SelfSignedTrustMatcher.isGranted(emptySet(), "media.example.com", 443))
    }

    @Test
    fun `one of several entries matching is enough`() {
        val entries = setOf("https://a.example.com", "https://b.example.com:8920", "https://c.example.com")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "b.example.com", 8920))
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "c.example.com", 443))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "b.example.com", 443))
    }

    @Test
    fun `scheme-less entry is treated as a bare authority`() {
        // Defensive: user-typed input that escaped normalization.
        val entries = setOf("media.example.com:8920")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "media.example.com", 8920))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "media.example.com", 443))
    }

    @Test
    fun `entry with trailing slash or path still matches its authority`() {
        val entries = setOf("https://media.example.com:8920/")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "media.example.com", 8920))
    }

    @Test
    fun `unparseable entries are ignored, not fatal`() {
        val entries = setOf(":::", "https://", "https://[::1", "https://host:notaport")
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "host", 443))
        // A valid entry alongside garbage still works.
        assertTrue(
            SelfSignedTrustMatcher.isGranted(entries + "https://host:443", "host", 443),
        )
    }

    @Test
    fun `ipv6 bracketed entries match bracket-stripped peers`() {
        val entries = setOf("https://[::1]:8920")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "::1", 8920))
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "[::1]", 8920))
        assertFalse(SelfSignedTrustMatcher.isGranted(entries, "::2", 8920))
    }

    @Test
    fun `ipv6 entry without port matches`() {
        val entries = setOf("https://[fe80::1]")
        assertTrue(SelfSignedTrustMatcher.isGranted(entries, "fe80::1", 443))
    }

    // ------------------------------------------------------------ parseEntry

    @Test
    fun `parseEntry handles the canonical router form`() {
        assertEquals(
            SelfSignedTrustMatcher.ParsedEntry("media.example.com", 8920),
            SelfSignedTrustMatcher.parseEntry("https://media.example.com:8920"),
        )
        assertEquals(
            SelfSignedTrustMatcher.ParsedEntry("media.example.com", null),
            SelfSignedTrustMatcher.parseEntry("https://media.example.com"),
        )
    }

    @Test
    fun `parseEntry lowercases host and strips userinfo path query fragment`() {
        assertEquals(
            SelfSignedTrustMatcher.ParsedEntry("host.example.com", 1234),
            SelfSignedTrustMatcher.parseEntry("HTTPS://User:Pass@Host.Example.com:1234/web?x=1#frag"),
        )
    }

    @Test
    fun `parseEntry rejects junk`() {
        assertNull(SelfSignedTrustMatcher.parseEntry(""))
        assertNull(SelfSignedTrustMatcher.parseEntry("   "))
        assertNull(SelfSignedTrustMatcher.parseEntry("https://"))
        assertNull(SelfSignedTrustMatcher.parseEntry("https://host:notaport"))
        assertNull(SelfSignedTrustMatcher.parseEntry("https://host:0"))
        assertNull(SelfSignedTrustMatcher.parseEntry("https://host:65536"))
        assertNull(SelfSignedTrustMatcher.parseEntry("https://[::1")) // unclosed bracket
        assertNull(SelfSignedTrustMatcher.parseEntry("http://a:b:c")) // two colons, no brackets
    }

    @Test
    fun `parseEntry accepts boundary ports`() {
        assertEquals(
            SelfSignedTrustMatcher.ParsedEntry("host", 1),
            SelfSignedTrustMatcher.parseEntry("https://host:1"),
        )
        assertEquals(
            SelfSignedTrustMatcher.ParsedEntry("host", 65535),
            SelfSignedTrustMatcher.parseEntry("https://host:65535"),
        )
    }

    // ---------------------------------------------------------- normalizeHost

    @Test
    fun `normalizeHost lowercases and strips brackets`() {
        assertEquals("media.example.com", SelfSignedTrustMatcher.normalizeHost("Media.Example.COM"))
        assertEquals("::1", SelfSignedTrustMatcher.normalizeHost("[::1]"))
        assertNull(SelfSignedTrustMatcher.normalizeHost("  "))
    }
}
