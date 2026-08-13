package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors
import com.raulshma.jellyplay.core.ui.components.AmbientColorBackdrop
import com.raulshma.jellyplay.core.ui.image.BlurHashBackdrop

/**
 * Which visual treatment the home backdrop is currently showing. Used as the
 * [Crossfade] key so the orchestrator recomposes only when the *kind* of layer
 * changes (blurhash ↔ ambient ↔ none), not on every hero rotation.
 */
internal enum class BackdropLayer { NONE, BLUR_HASH, AMBIENT }

/**
 * The resolved inputs to [HomeBackdrop], snapped from `HomeUiState` + the hero
 * controller. Held in a [Stable] holder so passing it down doesn't widen
 * recomposition beyond the fields that actually changed.
 */
@Stable
internal data class HomeBackdropState(
    val enabled: Boolean,
    val performanceMode: Boolean,
    val oledMode: Boolean,
    val isLightTheme: Boolean,
    /** BlurHash of the current hero backdrop, if any. */
    val blurHash: String?,
    /** URL used to extract the ambient palette when no blur hash is available. */
    val backdropUrl: String?,
    /** The screen's resolved background colour (the flat fill behind this layer). */
    val backgroundColor: Color,
)

/**
 * Whether the backdrop should render at all. Suppressed when disabled, in
 * performance mode (matches `MediaImage`'s blurhash gating), and in light
 * themes. The colourful ambient/blurhash layers sit full-screen behind a
 * scrolling list, which destroys text contrast on a light background — so
 * light mode shows the plain [HomeBackdropState.backgroundColor] (the
 * resolved `colorScheme.background`) instead. This mirrors the detail screen,
 * which fades its hero into the plain theme background in light mode rather
 * than darkening it. Pure so it can be unit-tested without a composition.
 */
internal fun shouldRenderBackdrop(state: HomeBackdropState): Boolean =
    state.enabled && !state.performanceMode && !state.isLightTheme

/**
 * Picks the active [BackdropLayer] from the resolved state. Pure for testing.
 * BlurHash wins when present; otherwise the ambient gradient shows (including
 * when the hero section itself is off — the gradient still has a palette to
 * draw from, or falls back to [com.raulshma.jellyplay.core.designsystem.theme.AmbientColors]).
 */
internal fun resolveBackdropLayer(state: HomeBackdropState): BackdropLayer =
    if (!state.blurHash.isNullOrBlank()) BackdropLayer.BLUR_HASH else BackdropLayer.AMBIENT

/**
 * The home screen's ambient backdrop.
 *
 * Layer priority:
 *  1. **BlurHash** of the hero artwork when present — decoded tiny and upscaled
 *     full-screen, so the low resolution *is* the blur. Cheap and cached.
 *  2. **Ambient gradient** — slowly drifting palette blobs derived from the
 *     artwork (via [rememberArtworkColors]), falling back to [com.raulshma.jellyplay.core.designsystem.theme.AmbientColors].
 *  3. **Nothing** — the flat [HomeBackdropState.backgroundColor] shows through.
 *
 * Performance & accessibility:
 *  - Suppressed entirely in `performanceMode` (matches `MediaImage`'s blurhash
 *    nulling) so low-end devices pay zero cost.
 *  - Suppressed entirely in light themes (see [shouldRenderBackdrop]); the
 *    flat [HomeBackdropState.backgroundColor] shows through instead, keeping
 *    list content legible on a light surface.
 *  - The ambient layer's own animation freezes under reduced motion.
 *  - A scrim is drawn in the draw phase (no recompose) blending toward
 *    [backgroundColor] for legibility; OLED adds a near-black overlay, light
 *    themes keep it lighter so content stays readable.
 */
@Composable
internal fun HomeBackdrop(state: HomeBackdropState) {
    if (!shouldRenderBackdrop(state)) return

    // Extract the palette only when we'll actually need it (ambient path).
    val artworkColors = rememberArtworkColors(
        if (state.blurHash.isNullOrBlank() && !state.backdropUrl.isNullOrBlank()) {
            state.backdropUrl
        } else {
            null
        },
    )
    val ambientColors = remember(artworkColors) { extractAmbientColors(artworkColors) }

    val layer = resolveBackdropLayer(state)

    Crossfade(
        targetState = layer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "backdropLayer",
    ) { active ->
        when (active) {
            BackdropLayer.BLUR_HASH -> {
                if (!state.blurHash.isNullOrBlank()) {
                    BlurHashBackdrop(blurHash = state.blurHash!!)
                }
            }
            BackdropLayer.AMBIENT -> {
                AmbientColorBackdrop(colors = ambientColors)
            }
            BackdropLayer.NONE -> Unit
        }
    }

    // Legibility scrim, drawn in the draw phase so animated background colours
    // don't trigger recomposition. Drawn over the whole backdrop layer.
    ScrimOverlay(
        backgroundColor = state.backgroundColor,
        oledMode = state.oledMode,
        isLightTheme = state.isLightTheme,
    )
}

/**
 * Transparent overlay whose only job is to paint the legibility scrim on top
 * of the backdrop. Kept separate so the backdrop layer and scrim compose and
 * invalidate independently.
 */
@Composable
private fun ScrimOverlay(
    backgroundColor: Color,
    oledMode: Boolean,
    isLightTheme: Boolean,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val overlay = scrimColors(
                    backgroundColor = backgroundColor,
                    oledMode = oledMode,
                    isLightTheme = isLightTheme,
                )
                onDrawBehind {
                    drawRect(brush = Brush.verticalGradient(overlay))
                }
            },
    )
}

private fun scrimColors(
    backgroundColor: Color,
    oledMode: Boolean,
    isLightTheme: Boolean,
): List<Color> {
    // Blend the backdrop toward the screen's background colour so list content
    // is legible. OLED crushes everything toward black (the whole point of the
    // mode); light themes keep a lighter scrim so dark text on top stays readable.
    val target = when {
        oledMode -> Color.Black
        isLightTheme -> lerp(backgroundColor, Color.White, 0.1f)
        else -> backgroundColor
    }
    val topAlpha = if (oledMode) 0.45f else 0.2f
    val bottomAlpha = if (oledMode) 0.85f else 0.6f
    return listOf(
        target.copy(alpha = topAlpha),
        target.copy(alpha = bottomAlpha),
    )
}

/** Replicates the audio player's private palette → colour list projection. */
private fun extractAmbientColors(artworkColors: ArtworkColors?): List<Color> {
    if (artworkColors == null) return emptyList()
    return listOfNotNull(
        artworkColors.vibrant,
        artworkColors.darkVibrant,
        artworkColors.lightVibrant,
        artworkColors.muted,
        artworkColors.darkMuted,
        artworkColors.lightMuted,
        artworkColors.dominant,
    ).map { it.copy(alpha = 1f) }
}
