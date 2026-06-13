package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer

fun FocusRequester.tryRequestFocus(tag: String? = null): Boolean =
    try {
        requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvFocusRestorer(fallback: FocusRequester? = null): Modifier {
    val isTv = LocalTvMode.current
    if (!isTv) return this
    return if (fallback != null) {
        this.focusRestorer(fallback)
    } else {
        this.focusRestorer()
    }
}

@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        requester.tryRequestFocus()
    }
    return requester
}

/**
 * Drives the initial TV focus grab for a screen whose list/grid data loads asynchronously.
 * `Modifier.focusRestorer(fallback)` and `Modifier.focusProperties { onEnter = ... }` only react to
 * focus *entering* a group from outside; neither proactively grabs focus. So a grid/row whose data
 * arrives after first composition would stay unfocused (the first D-pad press does nothing). This
 * helper requests focus once on [focusRequester] the first time [itemCount] is non-empty, and runs
 * [onReady] (e.g. to clamp a saved focused index to the live item count) on every count change.
 *
 * Mirrors Wholphin's page-owned `LaunchedEffect(Unit) { gridFocusRequester.requestFocus() }` inside
 * the Success branch (ItemGrid.kt:214, HomeRowGrid.kt:286) combined with CardGrid's index clamping.
 */
@Composable
fun TvGrabInitialFocus(
    focusRequester: FocusRequester,
    itemCount: Int,
    onReady: () -> Unit = {},
    tag: String = "tv_init",
) {
    val isTv = LocalTvMode.current
    val done = remember { mutableStateOf(false) }
    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            onReady()
            if (isTv && !done.value) {
                done.value = true
                focusRequester.tryRequestFocus(tag)
            }
        }
    }
}
