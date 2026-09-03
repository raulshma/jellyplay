package com.raulshma.jellyplay.core.ui.tv.input

import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure dispatch core of the TV D-pad key system ([DpadKeyHandlerScope]
 * has an internal constructor, exercised here directly; the `Modifier.composed`
 * wrappers and the platform `KeyEvent` translation are composition/platform
 * bound and untested — the desktop `toDpadKeyEvent` actual always returns
 * null):
 *
 *  - a SIMPLE handler fires only for its action and only on KeyDown;
 *  - a DETAILED handler fires for its action regardless of event type and
 *    receives the whole [DpadKeyEvent]; its return value becomes the
 *    consumption result;
 *  - a registered detailed handler takes precedence over a simple one for the
 *    same action;
 *  - unregistered actions return false (event not consumed);
 *  - [DpadKeyHandlerScope.hasHandlers] reflects whether ANY handler is
 *    registered in either map;
 *  - [DpadKeyEvent.isKeyDown]/[isKeyUp] derive from the event type.
 */
class DpadKeyHandlerScopeTest {

    private fun keyEvent(
        action: DpadAction,
        type: KeyEventType = KeyEventType.KeyDown,
        repeatCount: Int = 0,
    ) = DpadKeyEvent(action, type, repeatCount)

    @Test
    fun simpleHandler_firesOnlyForItsActionOnKeyDown() {
        val scope = DpadKeyHandlerScope()
        var leftCalls = 0
        scope.onKey(DpadAction.Left) { leftCalls++; true }

        assertTrue(scope.handle(keyEvent(DpadAction.Left)), "matching KeyDown must consume")
        assertEquals(1, leftCalls)

        assertFalse(scope.handle(keyEvent(DpadAction.Right)), "unregistered action must not consume")
        assertEquals(1, leftCalls)

        assertFalse(scope.handle(keyEvent(DpadAction.Left, type = KeyEventType.KeyUp)))
        assertEquals(1, leftCalls, "simple handlers must not fire on KeyUp")
    }

    @Test
    fun simpleHandler_resultPropagates() {
        val scope = DpadKeyHandlerScope()
        scope.onKey(DpadAction.Select) { false }

        assertFalse(scope.handle(keyEvent(DpadAction.Select)), "handler's false must propagate")
    }

    @Test
    fun detailedHandler_firesOnAnyEventTypeAndReceivesTheEvent() {
        val scope = DpadKeyHandlerScope()
        val seen = mutableListOf<DpadKeyEvent>()
        scope.onKeyEvent(DpadAction.Menu) { event ->
            seen += event
            true
        }

        assertTrue(scope.handle(keyEvent(DpadAction.Menu, type = KeyEventType.KeyUp, repeatCount = 4)))
        assertEquals(
            listOf(DpadKeyEvent(DpadAction.Menu, KeyEventType.KeyUp, 4)),
            seen,
        )
    }

    @Test
    fun detailedHandler_takesPrecedenceOverSimpleForSameAction() {
        val scope = DpadKeyHandlerScope()
        var simpleCalls = 0
        scope.onKey(DpadAction.Down) { simpleCalls++; true }
        scope.onKeyEvent(DpadAction.Down) { false } // detailed "veto"

        assertFalse(scope.handle(keyEvent(DpadAction.Down)))
        assertEquals(0, simpleCalls, "detailed handler must win; simple must not run")
    }

    @Test
    fun unregisteredActions_returnFalse() {
        val scope = DpadKeyHandlerScope()
        scope.onKey(DpadAction.Up) { true }

        DpadAction.entries
            .filter { it != DpadAction.Up }
            .forEach { action ->
                assertFalse(scope.handle(keyEvent(action)), "$action must fall through")
            }
    }

    @Test
    fun hasHandlers_reflectsRegistrationState() {
        val empty = DpadKeyHandlerScope()
        assertFalse(empty.hasHandlers)

        val simpleOnly = DpadKeyHandlerScope().apply { onKey(DpadAction.Left) { true } }
        assertTrue(simpleOnly.hasHandlers)

        val detailedOnly = DpadKeyHandlerScope().apply { onKeyEvent(DpadAction.Back) { true } }
        assertTrue(detailedOnly.hasHandlers)
    }

    @Test
    fun nullSimpleHandler_isEquivalentToUnregistered() {
        // onDpadKey defaults handlers to null — registering null must not
        // count as a handler or consume the event.
        val scope = DpadKeyHandlerScope()
        scope.onKey(DpadAction.Rewind, null)

        assertFalse(scope.hasHandlers)
        assertFalse(scope.handle(keyEvent(DpadAction.Rewind)))
    }

    @Test
    fun dpadKeyEvent_derivedFlagsFollowType() {
        val down = keyEvent(DpadAction.Select, KeyEventType.KeyDown)
        assertTrue(down.isKeyDown)
        assertFalse(down.isKeyUp)

        val up = keyEvent(DpadAction.Select, KeyEventType.KeyUp)
        assertFalse(up.isKeyDown)
        assertTrue(up.isKeyUp)
    }

    @Test
    fun dpadAction_coversTheFullRemoteVocabulary() {
        // The handler maps exactly these actions; a new remote button must
        // land here first.
        assertEquals(
            setOf(
                DpadAction.Left, DpadAction.Right, DpadAction.Up, DpadAction.Down,
                DpadAction.Select, DpadAction.Back, DpadAction.PlayPause,
                DpadAction.FastForward, DpadAction.Rewind, DpadAction.Menu,
            ),
            DpadAction.entries.toSet(),
        )
    }
}
