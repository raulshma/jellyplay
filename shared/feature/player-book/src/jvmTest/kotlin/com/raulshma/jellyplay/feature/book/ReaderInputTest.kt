package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the direction-aware navigation mapping shared by the native tap zones,
 * the key handler and the JS-reported tap events: physical-left input pages
 * forward under RTL, backward under LTR; the center zone toggles the chrome.
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
}
