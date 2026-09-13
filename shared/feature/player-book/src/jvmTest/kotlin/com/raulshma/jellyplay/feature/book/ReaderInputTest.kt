package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.input.key.Key
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the direction-aware navigation mapping shared by the native tap zones,
 * the key handler and the JS-reported tap events: physical-left input pages
 * forward under RTL, backward under LTR; the center zone toggles the chrome.
 * Also pins the volume-key paging mapping and the animated-turn gating.
 */
class ReaderInputTest {

    @Test
    fun `left zone pages backward under LTR`() {
        assertEquals(ReaderTapAction.BACKWARD, epubTapAction(EpubTapZone.LEFT, ReadingDirection.LTR))
    }

    @Test
    fun `right zone pages forward under LTR`() {
        assertEquals(ReaderTapAction.FORWARD, epubTapAction(EpubTapZone.RIGHT, ReadingDirection.LTR))
    }

    @Test
    fun `left zone pages forward under RTL (manga mapping)`() {
        assertEquals(ReaderTapAction.FORWARD, epubTapAction(EpubTapZone.LEFT, ReadingDirection.RTL))
        assertEquals(ReaderTapAction.BACKWARD, epubTapAction(EpubTapZone.RIGHT, ReadingDirection.RTL))
    }

    @Test
    fun `center zone always toggles the chrome`() {
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, epubTapAction(EpubTapZone.CENTER, ReadingDirection.LTR))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, epubTapAction(EpubTapZone.CENTER, ReadingDirection.RTL))
    }

    @Test
    fun `isForwardFromLeft names the single direction predicate`() {
        assertEquals(true, ReadingDirection.RTL.isForwardFromLeft())
        assertEquals(false, ReadingDirection.LTR.isForwardFromLeft())
    }

    // ------------------------------------------------------------------
    // Volume-key paging (physical mapping, like PageUp/PageDown)
    // ------------------------------------------------------------------

    @Test
    fun `volume down pages forward`() {
        assertEquals(ReaderTapAction.FORWARD, volumeKeyPagingAction(Key.VolumeDown))
    }

    @Test
    fun `volume up pages backward`() {
        assertEquals(ReaderTapAction.BACKWARD, volumeKeyPagingAction(Key.VolumeUp))
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
