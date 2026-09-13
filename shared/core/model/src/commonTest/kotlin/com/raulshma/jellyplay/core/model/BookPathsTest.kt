package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the Content-Disposition filename parser: the RFC 5987 `filename*=`
 * form wins over legacy `filename=`, and its percent-escapes decode as
 * UTF-8 (multi-byte sequences must not collapse to Latin-1 mojibake).
 */
class BookPathsTest {

    @Test
    fun `extended form wins over legacy`() {
        assertEquals(
            "Flat Book.epub",
            parseContentDispositionFileName(
                "attachment; filename=\"flat.epub\"; filename*=UTF-8''Flat%20Book.epub",
            ),
        )
    }

    @Test
    fun `multi byte utf8 escapes decode to real characters`() {
        // "Café Cœur 日本.epub" percent-encoded as UTF-8.
        assertEquals(
            "Café Cœur 日本.epub",
            parseContentDispositionFileName(
                "attachment; filename*=UTF-8''Caf%C3%A9%20C%C5%93ur%20%E6%97%A5%E6%9C%AC.epub",
            ),
        )
    }

    @Test
    fun `legacy quoted form decodes with escapes untouched`() {
        assertEquals(
            "Flat Book.epub",
            parseContentDispositionFileName("attachment; filename=\"Flat Book.epub\""),
        )
    }

    @Test
    fun `blank or formless headers yield null`() {
        assertNull(parseContentDispositionFileName(null))
        assertNull(parseContentDispositionFileName("  "))
        assertNull(parseContentDispositionFileName("attachment"))
    }
}
