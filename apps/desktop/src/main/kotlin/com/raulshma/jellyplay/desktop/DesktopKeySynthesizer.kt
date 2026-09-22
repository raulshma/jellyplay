package com.raulshma.jellyplay.desktop

import java.awt.Window
import java.awt.event.KeyEvent

/**
 * In-process key synthesis for the remote navigation ladder: posts
 * an AWT pressed+released Enter pair onto the system event queue with the
 * app window as the source — the exact route every REAL keystroke takes
 * into the window's key dispatcher chain (the scaffold's preview-key
 * handler, the player's media-key bridge when a player route is up, or the
 * focused component's own activation).
 *
 * Deliberately NOT `java.awt.Robot`: Robot injects GLOBAL input (it moves
 * the real keyboard focus and is unusable while the user types); the event
 * queue posts stay inside this process and this window. A null/missing
 * window (headless tests, pre-composition) is a silent no-op — the remote
 * select simply has nothing to land on yet.
 */
internal object DesktopKeySynthesizer {

    fun postEnterKey(window: Window?) {
        val target = window ?: return
        postKeyCode(target, KeyEvent.VK_ENTER)
    }

    /**
     * Posts the pressed+released pair for [keyCode]. The `now` timestamp is
     * shared so consumers cannot observe a release BEFORE its press under
     * clock jitter.
     */
    private fun postKeyCode(target: Window, keyCode: Int) {
        val queue = java.awt.Toolkit.getDefaultToolkit().systemEventQueue
        val whenMs = System.currentTimeMillis()
        runCatching {
            queue.postEvent(
                KeyEvent(target, KeyEvent.KEY_PRESSED, whenMs, 0, keyCode, KeyEvent.CHAR_UNDEFINED),
            )
            queue.postEvent(
                KeyEvent(target, KeyEvent.KEY_RELEASED, whenMs, 0, keyCode, KeyEvent.CHAR_UNDEFINED),
            )
        }
    }
}
