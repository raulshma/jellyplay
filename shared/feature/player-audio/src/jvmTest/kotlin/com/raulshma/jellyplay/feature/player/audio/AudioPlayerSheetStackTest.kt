package com.raulshma.jellyplay.feature.player.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the audio player's sheet admission fold + back ladder (the
 * `ReaderSheetStack` precedent): ONE `current` slot behind `open` (replacing
 * the seven parallel `showX` booleans whose implicit stack let sheets pile
 * up and let one back press discard the whole pile), the sticky confirmation
 * rule (a list/browser sheet must not silently replace an open DeleteConfirm
 * — the former swipe-up-over-confirm stacking bug), the lyrics arm DECLARED
 * non-sheet (a persisted preference — back navigates away without hiding
 * them), the self-dismissing menu, and the menu-item open cascade
 * (`showFromMenu`).
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

    private val replaceableSheetArms = sheetArms.filter { it != AudioPlayerSheet.DeleteConfirm }

    @Test
    fun `no sheet or dialog means not open`() {
        assertFalse(AudioPlayerSheetStack().open)
    }

    @Test
    fun `every sheet alone holds the screen`() {
        sheetArms.forEach { sheet ->
            val stack = AudioPlayerSheetStack().apply { show(sheet) }
            assertTrue(stack.open, "$sheet must hold the screen")
            assertEquals(sheet, stack.current, "$sheet must be the one open sheet")
        }
    }

    @Test
    fun `show while the delete confirm is open is a no-op`() {
        // The swipe-up queue gesture used to open the queue OVER the delete
        // confirmation (parallel booleans; z-order was declaration order in
        // the screen). The sticky rule makes the confirmation un-replaceable
        // by show() — every sheet arm bounces off it.
        replaceableSheetArms.forEach { sheet ->
            val stack = AudioPlayerSheetStack().apply { show(AudioPlayerSheet.DeleteConfirm) }
            stack.show(sheet)
            assertEquals(
                AudioPlayerSheet.DeleteConfirm,
                stack.current,
                "$sheet must not replace the open confirmation",
            )
        }
        // Re-showing the confirmation itself is idempotent.
        val confirm = AudioPlayerSheetStack().apply { show(AudioPlayerSheet.DeleteConfirm) }
        confirm.show(AudioPlayerSheet.DeleteConfirm)
        assertEquals(AudioPlayerSheet.DeleteConfirm, confirm.current)
    }

    @Test
    fun `the sticky gate guards the slot only — lyrics stay togglable`() {
        // The declared non-sheet arms never touch the slot, so they are
        // exempt from the sticky rule (a preference flip is not a sheet
        // competing with the confirmation).
        val stack = AudioPlayerSheetStack().apply { show(AudioPlayerSheet.DeleteConfirm) }
        stack.show(AudioPlayerSheet.Lyrics)
        assertTrue(stack.showLyrics, "the lyrics arm is exempt from the sticky gate")
        assertEquals(AudioPlayerSheet.DeleteConfirm, stack.current)
        stack.hide(AudioPlayerSheet.Lyrics)
        assertFalse(stack.showLyrics)
    }

    @Test
    fun `showing another sheet replaces the open one`() {
        // The former parallel booleans stacked (several true at once); the
        // slot holds exactly one — the last show wins.
        val stack = AudioPlayerSheetStack().apply { show(AudioPlayerSheet.Queue) }
        stack.show(AudioPlayerSheet.Equalizer)
        assertEquals(AudioPlayerSheet.Equalizer, stack.current, "exactly the new sheet is open")
        assertTrue(stack.open)
    }

    @Test
    fun `hide closes exactly the named sheet`() {
        // A hide of a sheet that isn't the open one is a no-op — with one
        // slot there is nothing else to close.
        val stack = AudioPlayerSheetStack().apply { show(AudioPlayerSheet.Equalizer) }
        stack.hide(AudioPlayerSheet.Queue)
        assertEquals(AudioPlayerSheet.Equalizer, stack.current)
        stack.hide(AudioPlayerSheet.Equalizer)
        assertNull(stack.current)
        assertFalse(stack.open)
    }

    @Test
    fun `the effects-to-equalizer hand-off leaves exactly the equalizer open`() {
        // The sheet's "Open equalizer" cascade (`hide(Effects); show(Equalizer)`)
        // rides hide + show; the slot must end on the equalizer alone.
        val stack = AudioPlayerSheetStack().apply { show(AudioPlayerSheet.Effects) }
        stack.hide(AudioPlayerSheet.Effects)
        stack.show(AudioPlayerSheet.Equalizer)
        assertEquals(AudioPlayerSheet.Equalizer, stack.current)
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
    fun `consumeBack pops exactly the one open sheet`() {
        sheetArms.forEach { sheet ->
            val stack = AudioPlayerSheetStack().apply { show(sheet) }
            assertTrue(stack.consumeBack(), "$sheet must consume back")
            assertNull(stack.current)
            assertFalse(stack.open)
            assertFalse(stack.consumeBack(), "second back is a navigation")
        }
    }

    @Test
    fun `back with nothing open returns false`() {
        // Even with the declared non-sheet arms set — they never hold the
        // screen, so back still navigates away.
        val stack = AudioPlayerSheetStack().apply {
            show(AudioPlayerSheet.Lyrics)
            showMenu = true
        }
        assertFalse(stack.consumeBack())
    }

    @Test
    fun `back never touches the lyrics preference or the menu`() {
        val stack = AudioPlayerSheetStack().apply {
            show(AudioPlayerSheet.Queue)
            show(AudioPlayerSheet.Lyrics)
            showMenu = true
        }
        assertTrue(stack.consumeBack())
        assertNull(stack.current, "the one open sheet is popped")
        assertTrue(stack.showLyrics, "the persisted preference survives back")
        assertTrue(stack.showMenu, "the self-dismissing menu survives back")
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
            assertEquals(sheet, stack.current)
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
