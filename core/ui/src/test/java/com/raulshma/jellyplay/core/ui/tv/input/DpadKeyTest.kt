package com.raulshma.jellyplay.core.ui.tv.input

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DpadKeyTest {

    private fun composeKeyEvent(
        keyCode: Int,
        action: Int = NativeKeyEvent.ACTION_DOWN,
        repeatCount: Int = 0,
    ): KeyEvent {
        val native = NativeKeyEvent(0L, 0L, action, keyCode, repeatCount)
        return KeyEvent(native)
    }

    @Test
    fun dpadLeft_mapsToLeft() {
        assertEquals(DpadAction.Left, composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_LEFT).toDpadAction())
    }

    @Test
    fun dpadRight_mapsToRight() {
        assertEquals(DpadAction.Right, composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_RIGHT).toDpadAction())
    }

    @Test
    fun dpadUp_mapsToUp() {
        assertEquals(DpadAction.Up, composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_UP).toDpadAction())
    }

    @Test
    fun dpadDown_mapsToDown() {
        assertEquals(DpadAction.Down, composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_DOWN).toDpadAction())
    }

    @Test
    fun dpadCenter_mapsToSelect() {
        assertEquals(DpadAction.Select, composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_CENTER).toDpadAction())
    }

    @Test
    fun enter_mapsToSelect() {
        assertEquals(DpadAction.Select, composeKeyEvent(NativeKeyEvent.KEYCODE_ENTER).toDpadAction())
    }

    @Test
    fun numpadEnter_mapsToSelect() {
        assertEquals(DpadAction.Select, composeKeyEvent(NativeKeyEvent.KEYCODE_NUMPAD_ENTER).toDpadAction())
    }

    @Test
    fun back_mapsToBack() {
        assertEquals(DpadAction.Back, composeKeyEvent(NativeKeyEvent.KEYCODE_BACK).toDpadAction())
    }

    @Test
    fun mediaPlayPause_mapsToPlayPause() {
        assertEquals(DpadAction.PlayPause, composeKeyEvent(NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE).toDpadAction())
    }

    @Test
    fun mediaPlay_mapsToPlayPause() {
        assertEquals(DpadAction.PlayPause, composeKeyEvent(NativeKeyEvent.KEYCODE_MEDIA_PLAY).toDpadAction())
    }

    @Test
    fun mediaPause_mapsToPlayPause() {
        assertEquals(DpadAction.PlayPause, composeKeyEvent(NativeKeyEvent.KEYCODE_MEDIA_PAUSE).toDpadAction())
    }

    @Test
    fun mediaFastForward_mapsToFastForward() {
        assertEquals(DpadAction.FastForward, composeKeyEvent(NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD).toDpadAction())
    }

    @Test
    fun mediaRewind_mapsToRewind() {
        assertEquals(DpadAction.Rewind, composeKeyEvent(NativeKeyEvent.KEYCODE_MEDIA_REWIND).toDpadAction())
    }

    @Test
    fun menu_mapsToMenu() {
        assertEquals(DpadAction.Menu, composeKeyEvent(NativeKeyEvent.KEYCODE_MENU).toDpadAction())
    }

    @Test
    fun unmappedKey_returnsNull() {
        assertNull(composeKeyEvent(NativeKeyEvent.KEYCODE_A).toDpadAction())
    }

    @Test
    fun toDpadKeyEvent_returnsCorrectAction() {
        val dpadKeyEvent = composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_LEFT).toDpadKeyEvent()
        assertNotNull(dpadKeyEvent)
        assertEquals(DpadAction.Left, dpadKeyEvent!!.action)
    }

    @Test
    fun toDpadKeyEvent_keyDown_mapsCorrectly() {
        val dpadKeyEvent = composeKeyEvent(
            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            action = NativeKeyEvent.ACTION_DOWN,
        ).toDpadKeyEvent()
        assertEquals(KeyEventType.KeyDown, dpadKeyEvent!!.type)
        assertEquals(true, dpadKeyEvent.isKeyDown)
        assertEquals(false, dpadKeyEvent.isKeyUp)
    }

    @Test
    fun toDpadKeyEvent_keyUp_mapsCorrectly() {
        val dpadKeyEvent = composeKeyEvent(
            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            action = NativeKeyEvent.ACTION_UP,
        ).toDpadKeyEvent()
        assertEquals(KeyEventType.KeyUp, dpadKeyEvent!!.type)
        assertEquals(false, dpadKeyEvent.isKeyDown)
        assertEquals(true, dpadKeyEvent.isKeyUp)
    }

    @Test
    fun toDpadKeyEvent_preservesRepeatCount() {
        val dpadKeyEvent = composeKeyEvent(
            NativeKeyEvent.KEYCODE_DPAD_LEFT,
            repeatCount = 5,
        ).toDpadKeyEvent()
        assertEquals(5, dpadKeyEvent!!.repeatCount)
    }

    @Test
    fun toDpadKeyEvent_unmappedKey_returnsNull() {
        assertNull(composeKeyEvent(NativeKeyEvent.KEYCODE_A).toDpadKeyEvent())
    }
}
