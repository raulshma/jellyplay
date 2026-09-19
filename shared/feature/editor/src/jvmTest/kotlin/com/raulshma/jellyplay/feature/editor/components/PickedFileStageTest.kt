package com.raulshma.jellyplay.feature.editor.components

import com.raulshma.jellyplay.feature.editor.EditorFilePicker
import com.raulshma.jellyplay.feature.editor.EditorPickedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the pick → stage → confirm choreography both editor upload sheets
 * (Images, Subtitles) render through: the upload gate, the blank-name
 * fallback the Subtitles sheet passes ("subtitle.srt"), pick replacement,
 * and launch-on-tap delegation to the platform picker adapter.
 */
class PickedFileStageTest {

    private class RecordingPicker : EditorFilePicker {
        var launches = 0
        override fun launch() {
            launches++
        }
    }

    private fun picked(name: String) = EditorPickedFile(
        fileName = name,
        previewUrl = null,
        readBytes = { byteArrayOf() },
    )

    @Test
    fun `upload gate opens only once a file is staged`() {
        val stage = PickedFileStage()
        assertFalse(stage.canConfirm)
        assertNull(stage.file)

        stage.stage(picked("poster.png"))
        assertTrue(stage.canConfirm)
        assertEquals("poster.png", stage.file?.fileName)
    }

    @Test
    fun `staging replaces a prior pick`() {
        val stage = PickedFileStage()
        stage.stage(picked("poster.png"))
        stage.stage(picked("backdrop.jpg"))
        assertEquals("backdrop.jpg", stage.file?.fileName)
    }

    @Test
    fun `blank picked name falls back to the holder fallback`() {
        // The Subtitles sheet's "subtitle.srt" contract: SAF/AWT can return a
        // blank name, and the upload call still receives a usable one.
        val stage = PickedFileStage(nameFallback = "subtitle.srt")
        stage.stage(picked(""))
        assertEquals("subtitle.srt", stage.nameOrDefault)

        stage.stage(picked("   "))
        assertEquals("subtitle.srt", stage.nameOrDefault)

        stage.stage(picked("movie.srt"))
        assertEquals("movie.srt", stage.nameOrDefault)
    }

    @Test
    fun `holder without fallback keeps a blank name blank`() {
        // The Images sheet passes no fallback — it never reads the name.
        val stage = PickedFileStage()
        stage.stage(picked(""))
        assertEquals("", stage.nameOrDefault)
    }

    @Test
    fun `launchPick delegates to the picker and is a no-op without one`() {
        val stage = PickedFileStage()
        stage.launchPick() // no adapter — must not throw

        val picker = RecordingPicker()
        stage.picker = picker
        stage.launchPick()
        stage.launchPick()
        assertEquals(2, picker.launches)
    }
}
