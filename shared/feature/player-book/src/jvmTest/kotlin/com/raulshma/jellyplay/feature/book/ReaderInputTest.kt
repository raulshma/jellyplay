package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.input.key.Key
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone
import com.raulshma.jellyplay.feature.book.epub.tapZoneFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the ONE content-input decision ([readerNavDecision]) every input path
 * funnels through — native paged tap zones, JS-reported taps, swipes — plus
 * the shared thirds resolver ([tapZoneFor]) it composes with: direction-aware
 * thirds (physical-left input pages forward under RTL, backward under LTR),
 * the guards (sheet-open / live-selection → ignore), the broken-geometry
 * degrade (center → chrome toggle, never a page turn), the volume-key paging
 * mapping and the animated-turn gating.
 */
class ReaderInputTest {

    @Test
    fun `left zone pages backward under LTR`() {
        assertEquals(ReaderNavDecision.BACKWARD, readerNavDecision(EpubTapZone.LEFT, ReadingDirection.LTR))
    }

    @Test
    fun `right zone pages forward under LTR`() {
        assertEquals(ReaderNavDecision.FORWARD, readerNavDecision(EpubTapZone.RIGHT, ReadingDirection.LTR))
    }

    @Test
    fun `left zone pages forward under RTL (manga mapping)`() {
        assertEquals(ReaderNavDecision.FORWARD, readerNavDecision(EpubTapZone.LEFT, ReadingDirection.RTL))
        assertEquals(ReaderNavDecision.BACKWARD, readerNavDecision(EpubTapZone.RIGHT, ReadingDirection.RTL))
    }

    @Test
    fun `center zone always toggles the chrome`() {
        assertEquals(ReaderNavDecision.TOGGLE_CONTROLS, readerNavDecision(EpubTapZone.CENTER, ReadingDirection.LTR))
        assertEquals(ReaderNavDecision.TOGGLE_CONTROLS, readerNavDecision(EpubTapZone.CENTER, ReadingDirection.RTL))
    }

    @Test
    fun `isForwardFromLeft names the single direction predicate`() {
        assertEquals(true, ReadingDirection.RTL.isForwardFromLeft())
        assertEquals(false, ReadingDirection.LTR.isForwardFromLeft())
    }

    // ------------------------------------------------------------------
    // The composed funnel: gesture thirds → zone → direction-aware action
    // ------------------------------------------------------------------

    @Test
    fun `gesture thirds resolve and map direction-aware through one funnel`() {
        // LTR: leading third backward, trailing third forward, center chrome.
        assertEquals(
            ReaderNavDecision.BACKWARD,
            readerNavDecision(tapZoneFor(40.0, 400.0), ReadingDirection.LTR),
        )
        assertEquals(
            ReaderNavDecision.FORWARD,
            readerNavDecision(tapZoneFor(360.0, 400.0), ReadingDirection.LTR),
        )
        assertEquals(
            ReaderNavDecision.TOGGLE_CONTROLS,
            readerNavDecision(tapZoneFor(200.0, 400.0), ReadingDirection.LTR),
        )
        // RTL (manga): the same thirds flip.
        assertEquals(
            ReaderNavDecision.FORWARD,
            readerNavDecision(tapZoneFor(40.0, 400.0), ReadingDirection.RTL),
        )
        assertEquals(
            ReaderNavDecision.BACKWARD,
            readerNavDecision(tapZoneFor(360.0, 400.0), ReadingDirection.RTL),
        )
    }

    @Test
    fun `broken geometry degrades to the chrome toggle — never a page turn`() {
        // The WebView geometry report failing (width 0/absent, x outside the
        // viewport) must yield the harmless chrome toggle under BOTH
        // directions, not a page turn.
        for (direction in ReadingDirection.entries) {
            assertEquals(ReaderNavDecision.TOGGLE_CONTROLS, readerNavDecision(tapZoneFor(null, 400.0), direction))
            assertEquals(ReaderNavDecision.TOGGLE_CONTROLS, readerNavDecision(tapZoneFor(40.0, null), direction))
            assertEquals(ReaderNavDecision.TOGGLE_CONTROLS, readerNavDecision(tapZoneFor(40.0, 0.0), direction))
            assertEquals(ReaderNavDecision.TOGGLE_CONTROLS, readerNavDecision(tapZoneFor(900.0, 400.0), direction))
            assertEquals(ReaderNavDecision.TOGGLE_CONTROLS, readerNavDecision(tapZoneFor(-5.0, 400.0), direction))
        }
    }

    // ------------------------------------------------------------------
    // The guards (the JS-tap path folds them; native keys/zones never do)
    // ------------------------------------------------------------------

    @Test
    fun `sheet-open and live-selection inputs are ignored`() {
        assertEquals(
            ReaderNavDecision.IGNORE,
            readerNavDecision(EpubTapZone.RIGHT, ReadingDirection.LTR, sheetOpen = true, selectionActive = false),
        )
        assertEquals(
            ReaderNavDecision.IGNORE,
            readerNavDecision(EpubTapZone.RIGHT, ReadingDirection.LTR, sheetOpen = false, selectionActive = true),
        )
    }

    // ------------------------------------------------------------------
    // Volume-key paging (physical mapping, like PageUp/PageDown)
    // ------------------------------------------------------------------

    @Test
    fun `volume down pages forward`() {
        assertEquals(ReaderNavDecision.FORWARD, volumeKeyPagingAction(Key.VolumeDown))
    }

    @Test
    fun `volume up pages backward`() {
        assertEquals(ReaderNavDecision.BACKWARD, volumeKeyPagingAction(Key.VolumeUp))
    }

    @Test
    fun `non-volume keys map to nothing`() {
        assertNull(volumeKeyPagingAction(Key.PageDown))
        assertNull(volumeKeyPagingAction(Key.DirectionLeft))
        assertNull(volumeKeyPagingAction(Key.Escape))
    }

    // ------------------------------------------------------------------
    // Animated page-turn gating
    // ------------------------------------------------------------------

    @Test
    fun `programmatic turns animate only when the preference is on`() {
        assertEquals(PageTurnScroll.ANIMATED, pageTurnScroll(animatedPageTurns = true))
        assertEquals(PageTurnScroll.SNAP, pageTurnScroll(animatedPageTurns = false))
    }
}
