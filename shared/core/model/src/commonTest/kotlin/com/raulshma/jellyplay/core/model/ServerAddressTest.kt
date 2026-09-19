package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class ServerAddressTest {

    @Test
    fun defaultsMissingSchemeToHttps() {
        assertEquals("https://test.example.com", normalizeServerAddress("test.example.com"))
        // A port is preserved untouched — only the scheme is defaulted.
        assertEquals("https://192.168.1.100:8096", normalizeServerAddress("192.168.1.100:8096"))
    }

    @Test
    fun preservesExplicitScheme() {
        assertEquals(
            "https://test.example.com",
            normalizeServerAddress("https://test.example.com"),
        )
        assertEquals(
            "http://test.example.com",
            normalizeServerAddress("http://test.example.com"),
        )
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("https://test.example.com", normalizeServerAddress("  test.example.com\t"))
        assertEquals(
            "https://test.example.com",
            normalizeServerAddress(" https://test.example.com "),
        )
    }

    @Test
    fun stripsTrailingSlashes() {
        assertEquals("https://test.example.com", normalizeServerAddress("test.example.com/"))
        assertEquals(
            "https://test.example.com",
            normalizeServerAddress("https://test.example.com/"),
        )
        // trimEnd('/') strips every trailing slash, not just one.
        assertEquals(
            "https://test.example.com",
            normalizeServerAddress("https://test.example.com///"),
        )
    }

    @Test
    fun combinesTrimSlashStripAndSchemeDefaulting() {
        assertEquals("https://lan.example.com", normalizeServerAddress(" lan.example.com/ "))
    }

    @Test
    fun stripsWholePathLegacyRoutePrefixes() {
        assertEquals("https://host.example.com", stripLegacyRoutePrefix("https://host.example.com/emby"))
        assertEquals(
            "http://host.example.com:8096",
            stripLegacyRoutePrefix("http://host.example.com:8096/mediabrowser"),
        )
        // Trailing slash and case are normalized away.
        assertEquals("https://host.example.com", stripLegacyRoutePrefix("https://host.example.com/emby/"))
        assertEquals("https://host.example.com", stripLegacyRoutePrefix("https://host.example.com/Emby"))
        assertEquals("https://host.example.com", stripLegacyRoutePrefix("https://host.example.com/MediaBrowser"))
        // IPv6 hosts: the first '/' after the scheme still starts the path.
        assertEquals("http://[::1]:8096", stripLegacyRoutePrefix("http://[::1]:8096/emby"))
    }

    @Test
    fun leavesNonLegacyAddressesUntouched() {
        assertNull(stripLegacyRoutePrefix("https://host.example.com"))
        assertNull(stripLegacyRoutePrefix("https://host.example.com/"))
        // Deeper paths and lookalikes belong to the deployment.
        assertNull(stripLegacyRoutePrefix("https://host.example.com/jellyfin"))
        assertNull(stripLegacyRoutePrefix("https://host.example.com/jellyfin/emby"))
        assertNull(stripLegacyRoutePrefix("https://host.example.com/emby/sub"))
        assertNull(stripLegacyRoutePrefix("https://host.example.com/embyweb"))
        // A query after the path disqualifies the suffix outright.
        assertNull(stripLegacyRoutePrefix("https://host.example.com/emby?x=1"))
        // Scheme-less input is not ours to rewrite.
        assertNull(stripLegacyRoutePrefix("host.example.com:8096/emby"))
    }
}
