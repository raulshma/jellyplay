package com.raulshma.jellyplay.core.ui.components

import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pure-logic tests for [ConfirmState] lifecycle and [ConfirmAction]/[ConfirmTone]
 * — no Compose UI, so they run on the JVM without a device.
 */
class ConfirmStateTest {

    @Test
    fun state_startsHidden() {
        val state = ConfirmState()
        assertFalse(state.isVisible)
    }

    @Test
    fun request_makesStateVisible() {
        val state = ConfirmState()
        state.request { }
        assertTrue(state.isVisible)
    }

    @Test
    fun dismiss_hidesStateAndClearsPendingAction() {
        val state = ConfirmState()
        state.request { }
        state.dismiss()
        assertFalse(state.isVisible)
        // Re-dismissing a hidden state is a no-op (no crash, still hidden).
        state.dismiss()
        assertFalse(state.isVisible)
    }

    @Test
    fun pendingAction_runsOnlyWhileRequested() {
        val state = ConfirmState()
        var ran = 0
        state.request { ran++ }
        // The captured lambda is what the dialog invokes on confirm.
        state.pending?.invoke()
        assertTrue(ran == 1)
        // After dismiss, there is no pending action to run.
        state.dismiss()
        state.pending?.invoke()
        assertTrue(ran == 1, "pending must be null after dismiss")
    }

    @Test
    fun request_replacesPreviousPendingAction() {
        val state = ConfirmState()
        var first = 0
        var second = 0
        state.request { first++ }
        state.request { second++ }
        state.pending?.invoke()
        assertTrue(first == 0, "first action replaced, not run")
        assertTrue(second == 1, "second action run")
    }

    @Test
    fun confirmAction_defaultsToPrimaryTone() {
        val action = ConfirmAction("Save") {}
        assertSame(ConfirmTone.PRIMARY, action.tone)
        assertTrue(action.text == "Save")
    }

    @Test
    fun confirmTone_hasAllVariants() {
        val tones = ConfirmTone.values().map { it.name }
        assertTrue(tones.containsAll(listOf("DESTRUCTIVE", "WARNING", "NEUTRAL", "PRIMARY")))
    }
}
