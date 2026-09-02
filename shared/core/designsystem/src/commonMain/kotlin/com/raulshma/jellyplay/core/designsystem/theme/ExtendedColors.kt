package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object BrandColors {
    val tmdb = Color(0xFF01B4E4)
    val imdb = Color(0xFFF5C518)
    val tvdb = Color(0xFF32A852)
    val tmdbBackground = Color(0xFF90CEA1)
}

object SyncStatusColors {
    val synced = Color(0xFF4CAF50)
    val syncing = Color(0xFF2196F3)
    val else_ = Color(0xFFFFC107)
}

object StatusColors {
    val available = Color(0xFF4CAF50)
    val availableLight = Color(0xFF81C784)
    val pending = Color(0xFFFFA726)
    val pendingLight = Color(0xFFFFB74D)
    val requested = Color(0xFF42A5F5)
    val success = Color(0xFF4CAF50)
    val warning = Color(0xFFFF9800)
    val error = Color(0xFFEF5350)
    val info = Color(0xFF42A5F5)
    val debug = Color(0xFF78909C)
}

object RatingColors {
    val star = Color(0xFFFFC107)
}

object HdrColors {
    val hdr10Gold = Color(0xFFB8860B)
    val dolbyVisionGold = Color(0xFFFFD700)
}

object AmbientColors {
    val deepIndigo = Color(0xFF1a237e)
    val deepPurple = Color(0xFF4a148c)
    val deepTeal = Color(0xFF004d40)
    val deepRed = Color(0xFFb71c1c)
}

object CastColors {
    val connected = Color(0xFF4285F4)
    val indicator = Color(0xFF2ECC71)
}

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

data class ExtendedColors(
    val statsOverlayText: Color = Color(0xFF8AB4F8),
    val hdrBadgeBackground: Color = Color(0xFF1A1A1A),
)

fun isLightColor(color: Color): Boolean =
    (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.5f

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

val LocalIsLightTheme = staticCompositionLocalOf { false }

/**
 * The scrim color used behind on-video player chrome (control bars, overlays).
 *
 * Material 3's [androidx.compose.material3.ColorScheme.scrim] is always black in both light and
 * dark schemes, which forces a dark look even in light mode. For the player to respect the theme,
 * the scrim base flips: white in light mode (yielding a light translucent gradient behind controls,
 * paired with the dark `onSurface` text the controls already use) and black in dark mode (the
 * classic dark gradient behind light text). Overlay surfaces and pills derive their fills/borders
 * from the *foreground* counterpart — [playerOnScrim] — so they stay cohesive in either theme.
 */
@Composable
@ReadOnlyComposable
fun playerScrimColor(): Color =
    if (LocalIsLightTheme.current) Color.White else Color.Black

/**
 * The foreground color for on-video player chrome that sits on top of a [playerScrimColor] fill.
 * The inverse of the scrim: black-on-white in light mode, white-on-black in dark mode. Use this
 * for overlay text, icon tints, pill borders and translucent chip backgrounds.
 */
@Composable
@ReadOnlyComposable
fun playerOnScrim(): Color =
    if (LocalIsLightTheme.current) Color.Black else Color.White

/**
 * Container fill for grouped list rows, search-result rows, search fields, and similar outlined
 * items.
 *
 * Dark mode keeps the translucent [androidx.compose.material3.ColorScheme.surfaceVariant] that
 * reads as a gentle elevation over the dark background. Light mode returns a crisp solid white
 * (`surfaceContainerLowest`) — mirroring the profile-banner hero — so rows read as clean outlined
 * cards; callers pair this with [lightModeHairlineBorder] so white rows stay defined inside white
 * group surfaces. [darkAlpha] lets callers preserve the exact dark-mode alpha they were using
 * (e.g. 0.4f for search fields) without changing its light-mode behaviour.
 */
@Composable
@ReadOnlyComposable
fun groupedItemContainerColor(darkAlpha: Float = 0.3f): Color =
    if (LocalIsLightTheme.current) MaterialTheme.colorScheme.surfaceContainerLowest
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = darkAlpha)

/**
 * Container fill for [com.raulshma.jellyplay.feature.settings.SettingsGroup]. Light mode returns a
 * crisp solid white (`surfaceContainerLowest`) — the hero treatment — paired with a hairline border
 * and drop shadow at the call site; dark mode keeps the original `surfaceContainerLow`.
 */
@Composable
@ReadOnlyComposable
fun settingsGroupContainerColor(): Color =
    if (LocalIsLightTheme.current) MaterialTheme.colorScheme.surfaceContainerLowest
    else MaterialTheme.colorScheme.surfaceContainerLow

/**
 * Hairline border used by search fields, the profile banner, and similar outlined surfaces.
 *
 * `onSurface` at low alpha reads fine in dark mode but vanishes against light surfaces; there we
 * use the M3 `outlineVariant` token, which is purpose-made for subtle-but-visible dividers against
 * light backgrounds.
 */
@Composable
@ReadOnlyComposable
fun hairlineBorderColor(): Color =
    if (LocalIsLightTheme.current) MaterialTheme.colorScheme.outlineVariant
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/**
 * A [Modifier.border] of [hairlineBorderColor] at [width] along [shape], applied only in light
 * mode — where grouped rows and outlined surfaces need a visible edge against white surfaces.
 * Dark mode returns the receiver unchanged, since the translucent container fills already read as
 * elevation there. Centralizes the `if (isLight) Modifier.border(1.dp, hairlineBorderColor(), shape)
 * else Modifier` shape that was open-coded at every grouped-row call site.
 */
@Composable
@ReadOnlyComposable
fun Modifier.lightModeHairlineBorder(
    shape: Shape,
    width: Dp = 1.dp,
): Modifier =
    if (LocalIsLightTheme.current) border(width, hairlineBorderColor(), shape) else this
