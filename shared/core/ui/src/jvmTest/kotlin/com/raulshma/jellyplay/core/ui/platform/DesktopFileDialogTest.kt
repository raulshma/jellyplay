package com.raulshma.jellyplay.core.ui.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Wave 21D coverage for the shared AWT dialog helper's pure half. The
 * dialog itself is a native modal window (HeadlessException in a test JVM),
 * so it is manually-verified-only; what is pinned here is the
 * answer-mapping contract every picker seam rides on: cancel (either half
 * of the dialog's directory/file pair null) reads as "no pick", and a
 * completed pick resolves against the dialog's directory exactly once.
 */
class DesktopFileDialogTest {

    @Test
    fun `pickedAwtFile maps a completed pick to the dialog's file`() {
        val dir = "C:${File.separator}media"
        val picked = pickedAwtFile(directory = dir, file = "poster.png")

        assertEquals(File(dir, "poster.png"), picked)
    }

    @Test
    fun `pickedAwtFile returns null on every cancel shape`() {
        // Windows clears both halves on cancel; other peers may clear one —
        // EITHER half null must read as "no pick" so callers keep prior
        // state (SAF cancel semantics).
        assertNull(pickedAwtFile(directory = null, file = null))
        assertNull(pickedAwtFile(directory = "C:${File.separator}media", file = null))
        assertNull(pickedAwtFile(directory = null, file = "poster.png"))
    }

    @Test
    fun `pickedAwtFile does not touch the filesystem`() {
        // Pure answer mapping: a path that does not exist still maps —
        // existence/failure handling belongs to the caller's read path.
        assertEquals(
            File("C:${File.separator}no${File.separator}such", "gone.srt"),
            pickedAwtFile("C:${File.separator}no${File.separator}such", "gone.srt"),
        )
    }
}
