package com.raulshma.jellyplay.core.model.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins the invariants of [SubtitleFile] — the downloaded subtitle payload whose
 * `ByteArray` field forces hand-written equality:
 *
 *  - Two files with CONTENT-EQUAL bytes and equal metadata are equal (value
 *    semantics, not reference equality — the repository dedupes/side-loads on
 *    this).
 *  - A difference in ANY of bytes / fileName / format / language breaks
 *    equality, and equal objects share a hash code.
 */
class SubtitleFileTest {

    private fun file(
        bytes: ByteArray = byteArrayOf(1, 2, 3),
        fileName: String = "sub.srt",
        format: String? = "srt",
        language: String? = "eng",
    ) = SubtitleFile(bytes = bytes, fileName = fileName, format = format, language = language)

    @Test
    fun `content-equal byte arrays are equal`() {
        val a = file(bytes = byteArrayOf(1, 2, 3))
        val b = file(bytes = byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing bytes break equality even with identical metadata`() {
        assertNotEquals(file(bytes = byteArrayOf(1, 2, 3)), file(bytes = byteArrayOf(1, 2, 4)))
    }

    @Test
    fun `differing metadata breaks equality`() {
        assertNotEquals(file(), file(fileName = "other.srt"))
        assertNotEquals(file(), file(format = "ass"))
        assertNotEquals(file(), file(language = "deu"))
    }

    @Test
    fun `null metadata participates in equality`() {
        assertEquals(file(format = null, language = null), file(format = null, language = null))
        assertNotEquals(file(format = null), file(format = "srt"))
    }

    @Test
    fun `a file is not equal to unrelated types`() {
        assertNotEquals<Any>(file(), Any())
    }
}
