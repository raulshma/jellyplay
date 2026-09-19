package com.raulshma.jellyplay.feature.player.audio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the audio player's sheet admission fold + back ladder (the
 * `ReaderSheetStack` precedent): ONE `open` predicate behind the seven
 * sheet/dialog flags, the lyrics arm DECLARED non-sheet (a persisted
 * preference — back navigates away without hiding them), the self-dismissing
 * menu, and the menu-item open cascade (`showFromMenu`).
 */
class AudioPlayerSheetStackTest {

    private val sheetArms = listOf(
        AudioPlayerSheet.Queue,
        AudioPlayerSheet.SpeedPicker,
        AudioPlayerSheet.Equalizer,
        AudioPlayerSheet.Effects,
        AudioPlayerSheet.LyricsSearch,
        AudioPlayerSheet.SleepTimer,
        AudioPlayerSheet.DeleteConfirm,
    )

    @Test
    fun `no sheet or dialog means not open`() {
        assertFalse(AudioPlayerSheetStack().open)
    }

    @Test
    fun `every sheet alone holds the screen`() {
        sheetArms.forEach { sheet ->
            assertTrue(
                AudioPlayerSheetStack().apply { show(sheet) }.open,
                "$sheet must hold the screen",
            )
        }
    }

    @Test
    fun `lyrics are a declared non-sheet arm and never hold the screen`() {
        val stack = AudioPlayerSheetStack()
        stack.show(AudioPlayerSheet.Lyrics)
        assertTrue(stack.showLyrics)
        assertFalse(stack.open, "lyrics visibility is a persisted preference, not a sheet")
        assertFalse(stack.consumeBack(), "back navigates away rather than hiding lyrics")
        assertTrue(stack.showLyrics, "the persisted preference survives back")
        stack.hide(AudioPlayerSheet.Lyrics)
        assertFalse(stack.showLyrics)
    }

    @Test
    fun `the menu self-dismisses and never holds the screen`() {
        val stack = AudioPlayerSheetStack()
        stack.showMenu = true
        assertFalse(stack.open)
        assertFalse(stack.consumeBack(), "the back ladder never closes the menu")
        assertTrue(stack.showMenu)
    }

    @Test
    fun `every sheet alone consumes back exactly once`() {
        sheetArms.forEach { sheet ->
            val stack = AudioPlayerSheetStack().apply { show(sheet) }
            assertTrue(stack.consumeBack(), "$sheet must consume back")
            assertFalse(stack.open)
            assertFalse(stack.consumeBack(), "second back is a navigation")
        }
    }

    @Test
    fun `back closes all seven sheets at once`() {
        val stack = AudioPlayerSheetStack().apply {
            sheetArms.forEach { show(it) }
        }
        assertTrue(stack.consumeBack())
        assertFalse(stack.showQueue)
        assertFalse(stack.showSpeedPicker)
        assertFalse(stack.showEqualizer)
        assertFalse(stack.showEffectsSheet)
        assertFalse(stack.showLyricsSearch)
        assertFalse(stack.showSleepTimer)
        assertFalse(stack.showDeleteConfirm)
    }

    @Test
    fun `back never touches the lyrics preference`() {
        val stack = AudioPlayerSheetStack().apply {
            show(AudioPlayerSheet.Queue)
            show(AudioPlayerSheet.Lyrics)
        }
        assertTrue(stack.consumeBack())
        assertFalse(stack.showQueue)
        assertTrue(stack.showLyrics)
    }

    @Test
    fun `show opens exactly one arm and hides exactly one arm`() {
        // Overlaps must be preserved: the loose flags never cross-closed.
        val stack = AudioPlayerSheetStack().apply { show(AudioPlayerSheet.Queue) }
        stack.show(AudioPlayerSheet.Equalizer)
        assertTrue(stack.showQueue, "the queue stays open underneath")
        assertTrue(stack.showEqualizer)
        stack.hide(AudioPlayerSheet.Equalizer)
        assertFalse(stack.showEqualizer)
        assertTrue(stack.showQueue, "hiding one arm leaves the others alone")
    }

    @Test
    fun `showFromMenu folds the menu-item cascade`() {
        listOf(
            AudioPlayerSheet.SpeedPicker,
            AudioPlayerSheet.Equalizer,
            AudioPlayerSheet.Effects,
            AudioPlayerSheet.SleepTimer,
        ).forEach { sheet ->
            val stack = AudioPlayerSheetStack().apply { showMenu = true }
            stack.showFromMenu(sheet)
            assertFalse(stack.showMenu, "the menu dismisses before the sheet opens")
            assertTrue(stack.open)
        }
    }

    @Test
    fun `opening the queue leaves the menu alone`() {
        // The top-bar queue button (and the vertical swipe) never closed the
        // menu; the fold must not change that overlap.
        val stack = AudioPlayerSheetStack().apply { showMenu = true }
        stack.show(AudioPlayerSheet.Queue)
        assertTrue(stack.showMenu)
        assertTrue(stack.open)
    }
}
