package com.raulshma.jellyplay.core.ui.tv.input

import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Reusable capture modifier that fires callbacks on ACTION_UP (matching Android click semantics
 * and letting users press-and-drag-off to cancel). Uses `onPreviewKeyEvent` so it can intercept
 * focus traversal for sliders and other elements that should "hold" focus.
 *
 * A null lambda lets that direction propagate normally. Set [triggerOnAction] to
 * `KeyEvent.ACTION_DOWN` for sliders/repeatable UIs that need to fire on every repeat event.
 *

 *
 * Critical for any UI that should hold focus on D-pad press (e.g. a seek bar) — returning true
 * consumes the event before focus traversal, preventing focus from escaping.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.handleDPadKeyEvents(
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onCenter: (() -> Unit)? = null,
    triggerOnAction: Int = ACTION_UP,
): Modifier = onPreviewKeyEvent {
    if (it.type != KeyEventType.KeyUp && it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    fun onActionUp(block: () -> Unit) {
        if (it.nativeKeyEvent.action == triggerOnAction) block()
    }
    when (it.nativeKeyEvent.keyCode) {
        NativeKeyEvent.KEYCODE_DPAD_LEFT,
        NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
            if (onLeft != null) { onActionUp(onLeft); return@onPreviewKeyEvent true }
            false
        }
        NativeKeyEvent.KEYCODE_DPAD_RIGHT,
        NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
            if (onRight != null) { onActionUp(onRight); return@onPreviewKeyEvent true }
            false
        }
        NativeKeyEvent.KEYCODE_DPAD_CENTER,
        NativeKeyEvent.KEYCODE_ENTER,
        NativeKeyEvent.KEYCODE_NUMPAD_ENTER -> {
            if (onCenter != null) { onActionUp(onCenter); return@onPreviewKeyEvent true }
            false
        }
        else -> false
    }
}
