package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as NativeKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [AndroidPlayerKeyBridge] — the Android twin of
 * DesktopPlayerKeyBridge: the deterministic last-resort path
 * where PlayerActivity.dispatchKeyEvent offers every key the normal dispatch
 * chain did not consume (the null-focus case) to the composing player
 * screen's own arms. Pins the delivery semantics the screens and the Activity
 * rely on: decline-vs-consume counting, throw isolation, and the
 * identity-guarded uninstall (PlayerActivity's singleTask onNewIntent swaps
 * VOD ↔ live screens — a popped screen's dispose must not disarm the
 * survivor's sink).
 */
class AndroidPlayerKeyBridgeTest {

    /** A throwaway native event — the bridge treats keys as opaque payloads. */
    private fun anyKeyEvent(): KeyEvent =
        KeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_DOWN, NativeKeyEvent.KEYCODE_DPAD_UP))

    @Test
    fun `deliver with no sink installed is a false no-op that does not count`() {
        AndroidPlayerKeyBridge.install(null)
        val before = AndroidPlayerKeyBridge.deliveryCount()
        assertFalse(AndroidPlayerKeyBridge.isArmed, "precondition: no sink installed")
        assertFalse(AndroidPlayerKeyBridge.deliver(anyKeyEvent()), "missing sink must not consume")
        assertEquals(before, AndroidPlayerKeyBridge.deliveryCount(), "no sink: nothing counted")
    }

    @Test
    fun `accepted delivery consumes and counts`() {
        val before = AndroidPlayerKeyBridge.deliveryCount()
        AndroidPlayerKeyBridge.install { true }
        assertTrue(AndroidPlayerKeyBridge.deliver(anyKeyEvent()))
        assertEquals(before + 1, AndroidPlayerKeyBridge.deliveryCount())
        AndroidPlayerKeyBridge.install(null) // teardown: install(null) is the unconditional clear
    }

    @Test
    fun `declined offer is unconsumed and uncounted`() {
        val before = AndroidPlayerKeyBridge.deliveryCount()
        AndroidPlayerKeyBridge.install { false }
        assertFalse(AndroidPlayerKeyBridge.deliver(anyKeyEvent()))
        assertEquals(before, AndroidPlayerKeyBridge.deliveryCount())
        AndroidPlayerKeyBridge.install(null)
    }

    @Test
    fun `a throwing sink is isolated to a false no-consume`() {
        val before = AndroidPlayerKeyBridge.deliveryCount()
        AndroidPlayerKeyBridge.install { error("boom") }
        assertFalse(AndroidPlayerKeyBridge.deliver(anyKeyEvent()))
        assertEquals(before, AndroidPlayerKeyBridge.deliveryCount())
        AndroidPlayerKeyBridge.install(null)
    }

    @Test
    fun `uninstall is identity-guarded against a newer sink`() {
        var firstCalls = 0
        var secondCalls = 0
        val first: (KeyEvent) -> Boolean = { firstCalls++; true }
        val second: (KeyEvent) -> Boolean = { secondCalls++; true }
        AndroidPlayerKeyBridge.install(first)
        // A stacked screen installed a newer sink (VOD ↔ live swap).
        AndroidPlayerKeyBridge.install(second)
        AndroidPlayerKeyBridge.uninstall(first)
        assertTrue(AndroidPlayerKeyBridge.isArmed, "popping the older screen must not disarm the survivor")
        AndroidPlayerKeyBridge.deliver(anyKeyEvent())
        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
        AndroidPlayerKeyBridge.uninstall(second)
        assertFalse(AndroidPlayerKeyBridge.isArmed, "the survivor's own dispose disarms")
    }
}
