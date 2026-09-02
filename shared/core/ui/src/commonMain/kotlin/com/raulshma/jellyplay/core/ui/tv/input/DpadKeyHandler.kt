package com.raulshma.jellyplay.core.ui.tv.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

enum class DpadAction {
    Left,
    Right,
    Up,
    Down,
    Select,
    Back,
    PlayPause,
    FastForward,
    Rewind,
    Menu,
}

data class DpadKeyEvent(
    val action: DpadAction,
    val type: KeyEventType,
    val repeatCount: Int,
) {
    val isKeyDown: Boolean get() = type == KeyEventType.KeyDown
    val isKeyUp: Boolean get() = type == KeyEventType.KeyUp
}

/**
 * Platform key mapping: Android translates `android.view.KeyEvent` keycodes
 * (V3 move from the legacy :core:ui shim, verbatim); desktop has no D-pad
 * source, so the mapping never fires and returns null.
 */
expect fun KeyEvent.toDpadKeyEvent(): DpadKeyEvent?

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
