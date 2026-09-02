package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvFocusExitHandler(): Modifier {
    val isTv = LocalTvMode.current
    if (!isTv) return this
    return this.focusProperties {
        @Suppress("DEPRECATION")
        exit = { focusDirection ->
            when (focusDirection) {
                FocusDirection.Up -> FocusRequester.Cancel
                FocusDirection.Down -> FocusRequester.Cancel
                else -> FocusRequester.Default
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvHeroFocusExitHandler(
    onDownExit: () -> Unit = {},
): Modifier {
    val isTv = LocalTvMode.current
    if (!isTv) return this
    return this.focusProperties {
        @Suppress("DEPRECATION")
        exit = { focusDirection ->
            when (focusDirection) {
                FocusDirection.Down -> {
                    onDownExit()
                    FocusRequester.Default
                }
                else -> FocusRequester.Default
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvRowFocusHandler(
    onUpExit: () -> Unit = {},
    onDownExit: () -> Unit = {},
): Modifier {
    val isTv = LocalTvMode.current
    if (!isTv) return this
    return this.focusProperties {
        @Suppress("DEPRECATION")
        exit = { focusDirection ->
            when (focusDirection) {
                FocusDirection.Up -> {
                    onUpExit()
                    FocusRequester.Cancel
                }
                FocusDirection.Down -> {
                    onDownExit()
                    FocusRequester.Cancel
                }
                FocusDirection.Left, FocusDirection.Right -> FocusRequester.Default
                else -> FocusRequester.Default
            }
        }
    }
}
