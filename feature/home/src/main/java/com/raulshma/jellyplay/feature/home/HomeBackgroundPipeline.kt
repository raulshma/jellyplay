package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor

/**
 * Computes the Home screen's animated background colour and keeps the
 * system/navigation-bar colour in sync with it.
 *
 * The background blends toward black in dark themes so the hero artwork's
 * dominant/muted palette tones read as a tinted scrim rather than a flat fill.
 * In light themes the raw `colorScheme.background` is used directly. The nav
 * bar mirrors the resolved background so the OS chrome melts into the app.
 *
 * Extracted verbatim from the former `MainHomeContent` so that composable no
 * longer owns the theme pipeline.
 *
 * @param dynamicTheming whether artwork-derived theming is enabled.
 * @param backdropUrl the current hero backdrop URL used to extract palette
 *  colours (only when [dynamicTheming] is true and the URL is non-blank).
 */
@Composable
internal fun rememberHomeBackgroundState(
    dynamicTheming: Boolean,
    backdropUrl: String?,
): HomeBackgroundState {
    val bgColor = MaterialTheme.colorScheme.background
    val isLightTheme = remember(bgColor) { isLightBackdropTheme(bgColor) }
    val artworkColors = com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors(
        if (dynamicTheming && !backdropUrl.isNullOrBlank()) backdropUrl else null
    )
    val baseOverlayColor = artworkColors?.darkMuted
        ?: artworkColors?.dominant
        ?: MaterialTheme.colorScheme.background
    val targetBackgroundColor = if (isLightTheme) {
        MaterialTheme.colorScheme.background
    } else {
        lerp(baseOverlayColor, Color.Black, 0.65f)
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "backgroundColor",
    )

    // Mirror the background into the system nav-bar colour. Write only the
    // settled *target* colour rather than each interpolated animation frame —
    // this state is read by an animateColorAsState at the app root, so feeding
    // it every interpolated frame would re-animate and recompose the whole
    // nav subtree ~18x per transition (the cause of the "dynamic theming makes
    // the app laggy" regression). The root animation smooths the transition on
    // its own; the local backgroundColor animation above handles the screen's
    // own draw.
    val navBarColor = LocalNavigationBarColor.current
    SideEffect {
        if (navBarColor.value != targetBackgroundColor) navBarColor.value = targetBackgroundColor
    }

    return HomeBackgroundState(backgroundColor = backgroundColor, isLightTheme = isLightTheme)
}

@Stable
internal class HomeBackgroundState(
    val backgroundColor: Color,
    val isLightTheme: Boolean,
)

/**
 * The backdrop luminance rule: Rec. 601 weighted perceived brightness decides
 * whether the home's derived chrome goes light or dark. Pure and internal so
 * the test asserts THIS function instead of a copy of its formula.
 */
internal fun isLightBackdropTheme(color: androidx.compose.ui.graphics.Color): Boolean =
    (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.5f
