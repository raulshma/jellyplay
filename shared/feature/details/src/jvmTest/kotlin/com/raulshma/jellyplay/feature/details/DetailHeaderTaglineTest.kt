package com.raulshma.jellyplay.feature.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [detailHeaderTagline] — the tagline admission behind the detail header's
 * tagline line: the first non-blank tagline when the show_tagline preference
 * is on, null otherwise — including the absent-taglines cases (older servers
 * / offline snapshots carry an empty list) so the line simply never renders.
 */
class DetailHeaderTaglineTest {

    @Test
    fun `first tagline wins when several are present`() {
        assertEquals(
            "In space no one can hear you scream.",
            detailHeaderTagline(
                taglines = listOf("In space no one can hear you scream.", "Second tagline"),
                showTagline = true,
            ),
        )
    }

    @Test
    fun `blank leading taglines are skipped for the first non-blank one`() {
        assertEquals(
            "Real tagline",
            detailHeaderTagline(taglines = listOf("  ", "", "Real tagline"), showTagline = true),
        )
    }

    @Test
    fun `empty taglines list yields null (older servers, offline)`() {
        assertNull(detailHeaderTagline(taglines = emptyList(), showTagline = true))
    }

    @Test
    fun `blank-only taglines list yields null`() {
        assertNull(detailHeaderTagline(taglines = listOf(" ", "  "), showTagline = true))
    }

    @Test
    fun `preference off yields null even with taglines present`() {
        assertNull(
            detailHeaderTagline(
                taglines = listOf("In space no one can hear you scream."),
                showTagline = false,
            ),
        )
    }
}
