package com.raulshma.jellyplay.core.ui.tv

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay

/**
 * State that gates whether a dialog's items are interactable when the dialog was opened via a
 * long-press. When the user long-presses DpadCenter/Enter to open a context menu, their finger is
 * still holding the button when the dialog appears. Without protection, the long-press KeyUp would
 * activate the first menu item. This state disables interactions for 1 second, with an early
 * opt-out when the user releases Enter/DpadCenter/NumPadEnter.
 *

 *
 * Usage:
 * ```
 * val guard = rememberDialogLongPressGuard(waitToLoad = openedFromLongPress)
 * Dialog(...) {
 *     Column(modifier = Modifier.then(guard.dialogModifier)) {
 *         items.forEach { item ->
 *             NavigationDrawerItem(
 *                 enabled = guard.allowsInteraction, // gated while waiting
 *                 ...
 *             )
 *         }
 *     }
 * }
 * ```
 */
class DialogLongPressGuard internal constructor(
    val allowsInteraction: Boolean,
    val dialogModifier: Modifier,
)

@Composable
fun rememberDialogLongPressGuard(
    waitToLoad: Boolean,
    delayMillis: Long = 1000L,
): DialogLongPressGuard {
    var waiting by remember { mutableStateOf(waitToLoad) }
    if (waitToLoad) {
        LaunchedEffect(Unit) {
            waiting = true
            delay(delayMillis)
            waiting = false
        }
    }
    val unlockModifier = Modifier.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp &&
            event.key.nativeKeyCode in setOf(
                KEYCODE_ENTER,
                KEYCODE_DPAD_CENTER,
                KEYCODE_NUMPAD_ENTER,
            )
        ) {
            waiting = false
            true
        } else {
            false
        }
    }
    return DialogLongPressGuard(
        allowsInteraction = !waiting,
        dialogModifier = unlockModifier,
    )
}

/**
 * Modifier for cyclic Up/Down navigation in a Column of focusable items — pressing Up at the top
 * wraps to the bottom and vice versa. Apply this to the container; the wrap targets are the first
 * and last *focusable* children.
 *

 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.verticalWrapAround(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    this.focusProperties {
        @Suppress("DEPRECATION")
        exit = { direction ->
            when (direction) {
                FocusDirection.Up -> {
                    focusManager.moveFocus(FocusDirection.Down)
                    FocusRequester.Cancel
                }
                FocusDirection.Down -> {
                    focusManager.moveFocus(FocusDirection.Up)
                    FocusRequester.Cancel
                }
                else -> FocusRequester.Default
            }
        }
    }
}

/**
 * Per-item wrap-around modifier — attach to the first and last focusable items in a cyclic list.
 * Example:
 * ```
 * itemsIndexed(items) { index, item ->
 *     val wrap = Modifier.wrapAroundVertical(
 *         isFirst = index == 0,
 *         isLast = index == items.lastIndex,
 *     )
 *     ListItem(modifier = wrap, ...)
 * }
 * ```
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.wrapAroundVertical(
    isFirst: Boolean = false,
    isLast: Boolean = false,
): Modifier = composed {
    val focusManager = LocalFocusManager.current
    this.focusProperties {
        @Suppress("DEPRECATION")
        exit = { direction ->
            when {
                isFirst && direction == FocusDirection.Up -> {
                    focusManager.moveFocus(FocusDirection.Down)
                    FocusRequester.Cancel
                }
                isLast && direction == FocusDirection.Down -> {
                    focusManager.moveFocus(FocusDirection.Up)
                    FocusRequester.Cancel
                }
                else -> FocusRequester.Default
            }
        }
    }
}
