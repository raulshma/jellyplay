package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class VersionComparisonTest {

    @Test
    fun `compareVersions returns positive when first is newer`() {
        assertTrue(compareVersions("1.2.4", "1.2.3") > 0)
        assertTrue(compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(compareVersions("1.2", "1.1.9") > 0)
    }

    @Test
    fun `compareVersions returns negative when first is older`() {
        assertTrue(compareVersions("1.2.2", "1.2.3") < 0)
        assertTrue(compareVersions("1.9.9", "2.0.0") < 0)
    }

    @Test
    fun `compareVersions returns zero when equal`() {
        assertEquals(0, compareVersions("1.2.3", "1.2.3"))
        assertEquals(0, compareVersions("1.2", "1.2.0"))
    }

    @Test
    fun `compareVersions treats non-numeric segments as zero`() {
        assertEquals(0, compareVersions("1.2.x", "1.2.0"))
        assertTrue(compareVersions("1.2.1", "1.2.x") > 0)
    }

    @Test
    fun `compareVersions sorts pre-releases below their release`() {
        assertTrue(compareVersions("0.11.0-alpha.1", "0.11.0") < 0)
        assertTrue(compareVersions("0.11.0", "0.11.0-alpha.1") > 0)
    }

    @Test
    fun `compareVersions orders pre-release numbers numerically`() {
        assertTrue(compareVersions("0.11.0-alpha.2", "0.11.0-alpha.1") > 0)
        assertTrue(compareVersions("0.11.0-alpha.10", "0.11.0-alpha.9") > 0)
        assertEquals(0, compareVersions("0.11.0-alpha.1", "0.11.0-alpha.1"))
    }

    @Test
    fun `compareVersions orders pre-release channels lexically`() {
        // "alpha" < "beta" < "rc" per semver lexical identifier ordering.
        assertTrue(compareVersions("0.11.0-beta.1", "0.11.0-alpha.1") > 0)
        assertTrue(compareVersions("0.11.0-rc", "0.11.0-beta.1") > 0)
    }

    @Test
    fun `compareVersions still prefers newer numeric core across pre-releases`() {
        assertTrue(compareVersions("0.11.0-alpha.1", "0.10.7") > 0)
        assertTrue(compareVersions("0.12.0", "0.11.0-alpha.9") > 0)
    }
}
