package com.raulshma.jellyplay.feature.home

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The hero metadata leg's chrome colours: the year/runtime badges, the official
 * rating chip, the ★ rating box, the genre chips, the Details and pause-video
 * pills, and the supporting text alphas (kicker, overview).
 *
 * The shipped hero assumed the dark ambient backdrop behind it: translucent
 * BLACK containers (`onSurface.copy(alpha = 0.1f)`) read as frosted glass over
 * dark artwork, and the top-only legibility scrim left the metadata zone to the
 * backdrop dissolve. In light themes `onSurface` is near-black and the ambient
 * backdrop is suppressed entirely (`shouldRenderBackdrop`), so that chrome
 * renders dark-on-dark. The light branch swaps to frosted LIGHT containers —
 * near-opaque `surface` glass that keeps ≥ 4.5:1 against its text over a
 * worst-case black backdrop — and the official rating chip moves to the theme's
 * `primaryContainer`/`onPrimaryContainer` pair. The title keeps its inline
 * `onSurface` + `surface` halo shadow, which is already theme-correct both ways.
 *
 * Pure so it can be unit-tested without a composition
 * ([HomeHeroLegibilityTest]); the dark branch reproduces the shipped inline
 * values exactly, so dark mode is unchanged.
 */
internal data class HeroLegibilityColors(
    /** Year/runtime InfoChips and the ★ rating box. */
    val badgeContainer: Color,
    val badgeBorder: Color,
    val badgeText: Color,
    /** The official-rating chip (the accent badge). */
    val accentContainer: Color,
    val accentBorder: Color,
    val accentText: Color,
    /** The genre chips. */
    val genreContainer: Color,
    val genreBorder: Color,
    val genreText: Color,
    /** The Details pill. */
    val pillContainer: Color,
    val pillBorder: Color,
    val pillText: Color,
    /** The pause-video chip (container + icon/text tint; no border shipped). */
    val pauseContainer: Color,
    val pauseContent: Color,
    /** The curated subhead (kicker) above the headline. */
    val kickerText: Color,
    /** The two-line synopsis under the chips. */
    val overviewText: Color,
)

internal fun heroLegibilityColors(
    colorScheme: ColorScheme,
    isLightTheme: Boolean,
): HeroLegibilityColors {
    val onSurface = colorScheme.onSurface
    return if (isLightTheme) {
        // Frosted-light chrome. The containers sit on [heroScrimColorStops]'s
        // light wash, but stay ≥ 4.5:1 even composited directly over a black
        // backdrop (asserted in HomeHeroLegibilityTest), so contrast doesn't
        // depend on the scrim being behind them.
        HeroLegibilityColors(
            badgeContainer = colorScheme.surface.copy(alpha = 0.8f),
            badgeBorder = onSurface.copy(alpha = 0.14f),
            badgeText = onSurface,
            accentContainer = colorScheme.primaryContainer.copy(alpha = 0.9f),
            accentBorder = colorScheme.primary.copy(alpha = 0.5f),
            accentText = colorScheme.onPrimaryContainer,
            genreContainer = colorScheme.surface.copy(alpha = 0.72f),
            genreBorder = onSurface.copy(alpha = 0.1f),
            genreText = onSurface.copy(alpha = 0.95f),
            pillContainer = colorScheme.surface.copy(alpha = 0.8f),
            pillBorder = colorScheme.outline.copy(alpha = 0.3f),
            pillText = onSurface,
            pauseContainer = colorScheme.surface.copy(alpha = 0.8f),
            pauseContent = onSurface,
            kickerText = onSurface.copy(alpha = 0.9f),
            overviewText = onSurface.copy(alpha = 0.8f),
        )
    } else {
        // The shipped inline values (HomeHero.kt), verbatim.
        HeroLegibilityColors(
            badgeContainer = onSurface.copy(alpha = 0.1f),
            badgeBorder = onSurface.copy(alpha = 0.15f),
            badgeText = onSurface.copy(alpha = 0.9f),
            accentContainer = colorScheme.primaryContainer.copy(alpha = 0.2f),
            accentBorder = colorScheme.primary.copy(alpha = 0.4f),
            accentText = colorScheme.primary,
            genreContainer = onSurface.copy(alpha = 0.12f),
            genreBorder = onSurface.copy(alpha = 0.08f),
            genreText = onSurface.copy(alpha = 0.85f),
            pillContainer = onSurface.copy(alpha = 0.1f),
            pillBorder = colorScheme.outline.copy(alpha = 0.15f),
            pillText = onSurface,
            pauseContainer = onSurface.copy(alpha = 0.35f),
            pauseContent = onSurface,
            kickerText = onSurface.copy(alpha = 0.85f),
            overviewText = onSurface.copy(alpha = 0.7f),
        )
    }
}

/**
 * The hero's legibility-scrim gradient as (fraction → colour) stops, fed to
 * `Brush.verticalGradient(colorStops = …)` by [heroScrimBrush].
 *
 *  - **Light** — the shipped top scrim (black 0.45 → transparent at 0.3) is
 *    kept, and the bottom half gains an early wash toward the background
 *    colour: the metadata leg (kicker/title/badges/buttons, ≈ 40–90% of the
 *    hero height) lands on a light surface instead of the artwork. The wash is
 *    fully opaque by 0.8, so it blends seamlessly into the flat background fill
 *    that replaces the ambient backdrop in light mode. The artwork's own DstIn
 *    dissolve only starts at ~0.55, so the wash compounds with the melt below
 *    that point and carries the top of the text zone on its own.
 *  - **Dark + backdrop pref on** — the shipped top-only scrim, verbatim.
 *  - **Dark + backdrop pref off** — the shipped black→transparent→background
 *    fade. The shipped call built this as an evenly-spaced `colors` list with
 *    `endY = POSITIVE_INFINITY`; expressed as stops (0, ⅓, ⅔, 1) it renders
 *    identically (an infinite endY just maps the gradient onto the draw size).
 *
 * Pure so it can be unit-tested without a composition.
 */
internal fun heroScrimColorStops(
    backgroundColor: Color,
    homeBackdropEnabled: Boolean,
    isLightTheme: Boolean,
): List<Pair<Float, Color>> = when {
    isLightTheme -> listOf(
        0.0f to Color.Black.copy(alpha = 0.45f),
        0.3f to Color.Transparent,
        0.48f to backgroundColor.copy(alpha = 0.6f),
        0.65f to backgroundColor.copy(alpha = 0.92f),
        0.8f to backgroundColor,
        1.0f to backgroundColor,
    )

    homeBackdropEnabled -> listOf(
        0.0f to Color.Black.copy(alpha = 0.45f),
        0.3f to Color.Transparent,
        0.6f to Color.Transparent,
        1.0f to Color.Transparent,
    )

    else -> listOf(
        0.0f to Color.Black.copy(alpha = 0.45f),
        1f / 3f to Color.Transparent,
        2f / 3f to backgroundColor.copy(alpha = 0.3f),
        1.0f to backgroundColor,
    )
}

/** Builds the hero scrim's gradient brush from [heroScrimColorStops]. */
internal fun heroScrimBrush(
    backgroundColor: Color,
    homeBackdropEnabled: Boolean,
    isLightTheme: Boolean,
): Brush = Brush.verticalGradient(
    colorStops = heroScrimColorStops(
        backgroundColor = backgroundColor,
        homeBackdropEnabled = homeBackdropEnabled,
        isLightTheme = isLightTheme,
    ).toTypedArray(),
)
