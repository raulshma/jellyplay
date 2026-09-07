package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
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
}
