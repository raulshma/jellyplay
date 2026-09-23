package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Android shell's remaining remote-navigation vocabulary — the
 * keycodes the d-pad/select/context-menu ladder targets synthesize through
 * `NavRequestCollector`'s `dispatchKey` seam. The target→route mapping and
 * the `ClosePlayer` pop semantics moved to the shared home
 * (`feature.shell.navigation.RemoteNavigationRouting`, pinned by the
 * shared/feature/shell jvmTest of the same name); only this keycode table is
 * platform-conditional and stays in the app.
 */
class RemoteNavigationRoutingTest {

    @Test
    fun `focus directions map onto the d-pad keycodes`() {
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_UP, keyCodeForFocusDirection(RemoteFocusDirection.UP))
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_DOWN, keyCodeForFocusDirection(RemoteFocusDirection.DOWN))
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_LEFT, keyCodeForFocusDirection(RemoteFocusDirection.LEFT))
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, keyCodeForFocusDirection(RemoteFocusDirection.RIGHT))
    }

    @Test
    fun `select and context menu synthesize the center and menu keycodes`() {
        assertEquals(android.view.KeyEvent.KEYCODE_DPAD_CENTER, REMOTE_SELECT_KEYCODE)
        assertEquals(android.view.KeyEvent.KEYCODE_MENU, REMOTE_CONTEXT_MENU_KEYCODE)
    }
}
