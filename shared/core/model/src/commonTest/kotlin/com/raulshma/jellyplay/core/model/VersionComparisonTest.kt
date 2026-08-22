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
}
