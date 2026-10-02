package com.raulshma.jellyplay.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the ACTION_SEND shared-text fold (moved verbatim out of
 * MainViewModel): Jellyfin media URLs win over generic URLs, generic URLs
 * win over plain text, and a blank payload degrades to Empty rather than a
 * blank search. Pure JVM — no Android types in the fold, no Robolectric.
 */
class SharedTextTargetTest {

    @Test
    fun `jellyfin media url resolves to MediaDetail with item id`() {
        assertEquals(
            SharedTextTarget.MediaDetail("0ea7d4b5-a1c2-4e9f-9b3d-1f2a3b4c5d6e"),
            parseSharedText("jellyfin://media/0ea7d4b5-a1c2-4e9f-9b3d-1f2a3b4c5d6e"),
        )
    }

    @Test
    fun `jellyfin url embedded in surrounding text still wins`() {
        assertEquals(
            SharedTextTarget.MediaDetail("abc123"),
            parseSharedText("check this out jellyfin://media/abc123 pretty please"),
        )
    }

    @Test
    fun `jellyfin url beats a generic url in the same payload`() {
        assertEquals(
            SharedTextTarget.MediaDetail("abc123"),
            parseSharedText("https://example.com/watch jellyfin://media/abc123"),
        )
    }

    @Test
    fun `plain https url is searched verbatim`() {
        assertEquals(
            SharedTextTarget.Search("https://example.com/a/b?q=1"),
            parseSharedText("https://example.com/a/b?q=1"),
        )
    }

    @Test
    fun `generic url match stops at whitespace`() {
        assertEquals(
            SharedTextTarget.Search("http://example.com/x"),
            parseSharedText("see http://example.com/x for details"),
        )
    }

    @Test
    fun `plain text becomes the search query`() {
        assertEquals(
            SharedTextTarget.Search("best Dune scenes"),
            parseSharedText("best Dune scenes"),
        )
    }

    @Test
    fun `blank payload degrades to Empty`() {
        assertEquals(SharedTextTarget.Empty, parseSharedText(""))
        assertEquals(SharedTextTarget.Empty, parseSharedText("   \n\t "))
    }
}
