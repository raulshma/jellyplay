package com.raulshma.jellyplay.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [computeLibrarySurface] — the render-branch fold that replaced the
 * library screen's inline error-vs-content branch. Pure JVM over the fold's
 * inputs, the `SearchSurfaceTest` pattern: every precedence rule the screen
 * used to decide inline is asserted here. The full-screen empty and
 * initial-loading rungs are deliberately NOT part of the fold (the chassis
 * ladder inside [LibrarySurface.Content] owns them — `PagedCollectionLadderTest`
 * pins those), so "empty" here is the zero-item half of the error gate's
 * predicate; the fold is section-blind by construction (the shipped branch
 * never read the section mode).
 */
class LibrarySurfaceTest {

    private fun surface(
        error: String? = null,
        itemCount: Int = 24,
    ) = computeLibrarySurface(
        error = error,
        itemCount = itemCount,
    )

    // ── Error ────────────────────────────────────────────────────────────────

    @Test
    fun `an error with zero loaded items is the error surface`() {
        assertEquals(LibrarySurface.Error("boom"), surface(error = "boom", itemCount = 0))
    }

    @Test
    fun `the error surface carries the message verbatim`() {
        val surface = surface(error = "the server fell over", itemCount = 0)
        assertEquals("the server fell over", (surface as LibrarySurface.Error).message)
    }

    // ── the itemCount == 0 interaction ───────────────────────────────────────

    @Test
    fun `an error over loaded items stays Content`() {
        // The load-bearing corner: stale pages keep rendering under the header's
        // error strip — only a pager with nothing loaded takes the full-screen
        // error.
        assertEquals(LibrarySurface.Content, surface(error = "boom", itemCount = 24))
    }

    @Test
    fun `a zero count without an error never yields the error surface`() {
        // The empty state is the chassis ladder's Empty rung inside Content,
        // not a fold arm — a plain empty library must not render ErrorScreen.
        assertEquals(LibrarySurface.Content, surface(error = null, itemCount = 0))
    }

    // ── Content ──────────────────────────────────────────────────────────────

    @Test
    fun `loaded items without an error render Content`() {
        assertEquals(LibrarySurface.Content, surface(itemCount = 24))
    }
}
