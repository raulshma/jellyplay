package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the sheet admission fold — the single predicate the JS-tap gate and
 * the chrome auto-hide read (the flags were previously hand-derived per
 * consumer and had drifted: the note dialog suppressed neither).
 */
class ReaderSheetStackTest {

    @Test
    fun `no sheet or dialog means not open`() {
        assertFalse(ReaderSheetStack().open)
    }

    @Test
    fun `every sheet alone holds the screen`() {
        assertTrue(ReaderSheetStack().apply { showSettings = true }.open)
        assertTrue(ReaderSheetStack().apply { showToc = true }.open)
        assertTrue(ReaderSheetStack().apply { showBookmarks = true }.open)
        assertTrue(ReaderSheetStack().apply { showAnnotations = true }.open)
        assertTrue(ReaderSheetStack().apply { showSearch = true }.open)
        assertTrue(ReaderSheetStack().apply { showSleepTimer = true }.open)
    }

    @Test
    fun `the settings sheet is owned here like every other sheet`() {
        // It used to be VM uiState state with screen-side OR guards at every
        // read; the single-owner move must keep it inside the fold.
        val stack = ReaderSheetStack().apply { showSettings = true }
        assertTrue(stack.open)
        stack.showSettings = false
        assertFalse(stack.open)
    }

    @Test
    fun `the note dialog holds the screen like a sheet`() {
        val stack = ReaderSheetStack()
        stack.noteTarget = NoteDialogTarget.Selection
        assertTrue(stack.open)
        stack.noteTarget = NoteDialogTarget.Existing(
            ReaderAnnotation(
                id = 1L,
                itemId = "i1",
                cfi = "epubcfi(/6/8)",
                style = ReaderAnnotationStyle.HIGHLIGHT,
                color = ReaderAnnotationColor.YELLOW,
                anchorText = "quote",
                note = null,
                chapterLabel = "Ch 1",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        assertTrue(stack.open)
    }

    @Test
    fun `closing the last sheet releases the screen`() {
        val stack = ReaderSheetStack().apply {
            showToc = true
            showSearch = true
        }
        assertTrue(stack.open)
        stack.showToc = false
        assertTrue(stack.open, "one sheet still holds it")
        stack.showSearch = false
        assertFalse(stack.open)
    }
}
