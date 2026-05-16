package com.raulshma.jellyplay.core.ui.tv

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext

fun Context.isTv(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)

@Deprecated(
    message = "Use LocalTvMode.current instead of isTvDevice()",
    replaceWith = ReplaceWith("LocalTvMode.current"),
    level = DeprecationLevel.WARNING,
)
@Composable
fun isTvDevice(): Boolean = LocalContext.current.isTv()

@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        requester.requestFocus()
    }
    return requester
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvFocusExitHandler(): Modifier {
    val isTv = LocalTvMode.current
    if (!isTv) return this
    return this.focusProperties {
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
fun Modifier.tvFocusRestorer(): Modifier {
    val isTv = LocalTvMode.current
    if (!isTv) return this
    return this.focusRestorer()
}

/**
 * No-op modifier. Previously added custom focus animation + border on TV.
 * Now `Modifier.clickable()` handles focus targets natively, so calling
 * `tvFocusable().clickable()` no longer creates double focus targets.
 *
 * @deprecated Remove calls to this modifier. Use `Modifier.clickable()` directly.
 *   This is kept as a no-op to avoid breaking 150+ call sites.
 */
@Deprecated(
    message = "Remove this modifier. Use Modifier.clickable() directly.",
    level = DeprecationLevel.WARNING,
)
fun Modifier.tvFocusable(): Modifier = this
