package com.raulshma.jellyplay.desktop

import androidx.compose.ui.input.key.Key

/**
 * The one desktop back-key decision, folded out of the two handlers that
 * used to hand-copy it — DesktopAppRoot's scaffold Row (the signed-in shell)
 * and the signed-out shell's shared SignedOutAuthHost frame:
 * **Esc**, or **Alt+Left**, pops the current
 * back stack, but only when there is anything to pop. Every stack is seeded
 * with its tab/seed root (NavigationState seeds each top-level back stack
 * with its route key), so `stackDepth > 1` is exactly "not at the root" and
 * the root-refuse is part of the fold: at the root every key — back keys
 * included — is refused (`false`), i.e. the caller leaves the key event
 * unconsumed. This shell has no quit-on-Esc convention; the window closes
 * via the titlebar / tray Quit like everywhere else.
 *
 * Pure on purpose: no composition, no state, no event object — call sites
 * pass `event.key` / `event.isAltPressed` / `backStack.size` and keep the
 * KeyDown gate (and, in the shell, the video-player media-key fallback) on
 * their side of the line. The full truth table is pinned by
 * DesktopBackKeyDecisionTest in jvmTest.
 *
 * @return `true` when the caller must consume the event and pop the stack
 *   (the back key above the root); `false` when the event is not a back
 *   key — or is one at the root, which must fall through unconsumed.
 */
internal fun desktopBackKeyDecision(
    key: Key,
    isAltPressed: Boolean,
    stackDepth: Int,
): Boolean =
    (key == Key.Escape || (key == Key.DirectionLeft && isAltPressed)) && stackDepth > 1
