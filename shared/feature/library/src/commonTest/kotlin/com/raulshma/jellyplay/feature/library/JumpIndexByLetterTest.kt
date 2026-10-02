package com.raulshma.jellyplay.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the alphabet rail's letter→index fold ([jumpIndexByLetter]) — extracted
 * verbatim from the library screen's inline `derivedStateOf`. Pure JVM over
 * plain items + a name selector, the [AlphabetRailGeometryTest] pattern: the
 * first-index-per-leading-letter rule, the `#` bucket, case normalization,
 * insertion-ordered keys, and the empty-input degenerate.
 */
class JumpIndexByLetterTest {

    @Test
    fun `records the first index at which each leading letter appears`() {
        val map = jumpIndexByLetter(listOf("apple", "Banana", "avocado", "cherry")) { it }
        // "avocado" is a repeat 'a' — the earlier index wins.
        assertEquals(mapOf('a' to 0, 'b' to 1, 'c' to 3), map)
    }

    @Test
    fun `non-a-z leading characters fold into the misc bucket`() {
        val map = jumpIndexByLetter(listOf("2001", "átila", "bat")) { it }
        // Accented and numeric leads both land in '#'; the first index wins.
        assertEquals(mapOf('#' to 0, 'b' to 2), map)
    }

    @Test
    fun `leading letters are case-normalized`() {
        assertEquals(mapOf('a' to 0), jumpIndexByLetter(listOf("APPLE")) { it })
    }

    @Test
    fun `keys keep the letters' display order`() {
        val map = jumpIndexByLetter(listOf("mango", "apple")) { it }
        assertEquals(listOf('m', 'a'), map.keys.toList())
    }

    @Test
    fun `empty input yields an empty map`() {
        assertEquals(emptyMap(), jumpIndexByLetter(emptyList<String>()) { it })
    }

    @Test
    fun `null and empty names fold into the misc bucket`() {
        val map = jumpIndexByLetter(listOf<String?>(null, "", "bee")) { it }
        assertEquals(mapOf('#' to 0, 'b' to 2), map)
    }
}
