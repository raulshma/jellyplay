package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Modifier for cyclic Up/Down navigation in a Column of focusable items — pressing Up at the top
 * wraps to the bottom and vice versa. Apply this to the container; the wrap targets are the first
 * and last *focusable* children.
 *
 * Consumed by the video player's picker sheets (AspectRatio/Chapter/Decoder/PlaybackMode) and the
 * details [com.raulshma.jellyplay.feature.details.DetailTopBar] action overflow.
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
