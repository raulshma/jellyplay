package com.raulshma.jellyplay.core.ui.tv

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

object TvFocusDefaults {
    val BorderWidth = 2.dp
    val GlowElevation = 16.dp
    const val GlowAmbientAlpha = 0.5f
    const val GlowSpotAlpha = 0.3f
}

@Stable
data class TvFocusState(
    val isFocused: Boolean = false,
    val scale: Float = 1f,
    val borderWidth: Dp = 0.dp,
    val glowElevation: Dp = 0.dp,
    val focusModifier: Modifier = Modifier,
)

@Composable
fun rememberTvFocusState(
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = TvFocusDefaults.BorderWidth,
): TvFocusState {
    val isTv = LocalTvMode.current
    var isFocused by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        label = "tvFocusScale",
    )

    val animatedBorder by animateDpAsState(
        targetValue = if (isFocused && isTv) focusedBorderWidth else 0.dp,
        label = "tvFocusBorder",
    )

    val animatedGlowElevation by animateDpAsState(
        targetValue = if (isFocused && isTv) TvFocusDefaults.GlowElevation else 0.dp,
        label = "tvFocusGlow",
    )

    val focusModifier = if (isTv) {
        Modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
    } else {
        Modifier
    }

    return TvFocusState(
        isFocused = isFocused && isTv,
        scale = if (isTv) 1f else animatedScale,
        borderWidth = animatedBorder,
        glowElevation = animatedGlowElevation,
        focusModifier = focusModifier,
    )
}

fun Modifier.tvFocusIndicator(
    focusState: TvFocusState,
    shape: Shape = RectangleShape,
): Modifier = composed {
    val isTv = LocalTvMode.current
    if (!isTv) return@composed this

    val glowColor = MaterialTheme.colorScheme.primary

    this
        .then(
            if (focusState.glowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = focusState.glowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = glowColor.copy(alpha = TvFocusDefaults.GlowAmbientAlpha),
                    spotColor = glowColor.copy(alpha = TvFocusDefaults.GlowSpotAlpha),
                )
            } else {
                Modifier
            }
        )
        .then(
            if (focusState.borderWidth > 0.dp) {
                Modifier.border(
                    width = focusState.borderWidth,
                    color = MaterialTheme.colorScheme.primary,
                    shape = shape,
                )
            } else {
                Modifier
            }
        )
}

fun Modifier.tvFocusChanged(onFocusChanged: (Boolean) -> Unit): Modifier = composed {
    val isTv = LocalTvMode.current
    if (!isTv) return@composed this

    this.onFocusChanged { focusState ->
        onFocusChanged(focusState.isFocused)
    }
}

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
                FocusDirection.Up -> FocusRequester.Cancel
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
