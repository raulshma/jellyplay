package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection

/**
 * The Android shell's half of the remote-navigation ladder — the keycodes the
 * remote d-pad/select/context-menu targets synthesize through
 * [NavRequestCollector]'s `dispatchKey` seam (Compose's own key handling
 * interprets them). The target→route mapping and the multi-back-stack player
 * pop are the shared pure folds in
 * `com.raulshma.jellyplay.feature.shell.navigation.RemoteNavigationRouting`
 * (shared/feature/shell); only this keycode vocabulary is platform-conditional
 * and stays here.
 */

/** The Android keycodes the remote d-pad/select/context-menu targets synthesize. */
internal fun keyCodeForFocusDirection(direction: RemoteFocusDirection): Int = when (direction) {
    RemoteFocusDirection.UP -> android.view.KeyEvent.KEYCODE_DPAD_UP
    RemoteFocusDirection.DOWN -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
    RemoteFocusDirection.LEFT -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
    RemoteFocusDirection.RIGHT -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
}

/** The select activation keycode (D-pad center; ENTER on keyboard remotes). */
internal val REMOTE_SELECT_KEYCODE: Int = android.view.KeyEvent.KEYCODE_DPAD_CENTER

/** The context-menu keycode (the remote hamburger/"menu" key). */
internal val REMOTE_CONTEXT_MENU_KEYCODE: Int = android.view.KeyEvent.KEYCODE_MENU
