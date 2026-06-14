package com.raulshma.jellyplay.core.ui.tv.input

import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

fun Modifier.onDpadKey(
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onSelect: (() -> Boolean)? = null,
    onBack: (() -> Boolean)? = null,
    onPlayPause: (() -> Boolean)? = null,
    onFastForward: (() -> Boolean)? = null,
    onRewind: (() -> Boolean)? = null,
    onMenu: (() -> Boolean)? = null,
): Modifier = dpadKeyHandler {
    onKey(DpadAction.Left, onLeft)
    onKey(DpadAction.Right, onRight)
    onKey(DpadAction.Up, onUp)
    onKey(DpadAction.Down, onDown)
    onKey(DpadAction.Select, onSelect)
    onKey(DpadAction.Back, onBack)
    onKey(DpadAction.PlayPause, onPlayPause)
    onKey(DpadAction.FastForward, onFastForward)
    onKey(DpadAction.Rewind, onRewind)
    onKey(DpadAction.Menu, onMenu)
}

fun Modifier.onDpadKeyEvent(
    onLeft: ((DpadKeyEvent) -> Boolean)? = null,
    onRight: ((DpadKeyEvent) -> Boolean)? = null,
    onUp: ((DpadKeyEvent) -> Boolean)? = null,
    onDown: ((DpadKeyEvent) -> Boolean)? = null,
    onSelect: ((DpadKeyEvent) -> Boolean)? = null,
    onBack: ((DpadKeyEvent) -> Boolean)? = null,
    onPlayPause: ((DpadKeyEvent) -> Boolean)? = null,
    onFastForward: ((DpadKeyEvent) -> Boolean)? = null,
    onRewind: ((DpadKeyEvent) -> Boolean)? = null,
    onMenu: ((DpadKeyEvent) -> Boolean)? = null,
): Modifier = dpadKeyHandler {
    onKeyEvent(DpadAction.Left, onLeft)
    onKeyEvent(DpadAction.Right, onRight)
    onKeyEvent(DpadAction.Up, onUp)
    onKeyEvent(DpadAction.Down, onDown)
    onKeyEvent(DpadAction.Select, onSelect)
    onKeyEvent(DpadAction.Back, onBack)
    onKeyEvent(DpadAction.PlayPause, onPlayPause)
    onKeyEvent(DpadAction.FastForward, onFastForward)
    onKeyEvent(DpadAction.Rewind, onRewind)
    onKeyEvent(DpadAction.Menu, onMenu)
}

class DpadKeyHandlerScope internal constructor() {
    private val simpleHandlers = mutableMapOf<DpadAction, (() -> Boolean)?>()
    private val detailedHandlers = mutableMapOf<DpadAction, ((DpadKeyEvent) -> Boolean)?>()

    fun onKey(action: DpadAction, handler: (() -> Boolean)?) {
        simpleHandlers[action] = handler
    }

    fun onKeyEvent(action: DpadAction, handler: ((DpadKeyEvent) -> Boolean)?) {
        detailedHandlers[action] = handler
    }

    internal fun handle(dpadKeyEvent: DpadKeyEvent): Boolean {
        val detailed = detailedHandlers[dpadKeyEvent.action]
        if (detailed != null) {
            return detailed(dpadKeyEvent)
        }
        val simple = simpleHandlers[dpadKeyEvent.action]
        if (simple != null && dpadKeyEvent.isKeyDown) {
            return simple()
        }
        return false
    }

    internal val hasHandlers: Boolean
        get() = simpleHandlers.isNotEmpty() || detailedHandlers.isNotEmpty()
}

fun Modifier.dpadKeyHandler(block: DpadKeyHandlerScope.() -> Unit): Modifier = composed {
    val isTv = LocalTvMode.current
    if (!isTv) return@composed this

    val scope = DpadKeyHandlerScope().apply(block)
    if (!scope.hasHandlers) return@composed this

    this.onPreviewKeyEvent { keyEvent ->
        val dpadKeyEvent = keyEvent.toDpadKeyEvent()
            ?: return@onPreviewKeyEvent false
        scope.handle(dpadKeyEvent)
    }
}

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

